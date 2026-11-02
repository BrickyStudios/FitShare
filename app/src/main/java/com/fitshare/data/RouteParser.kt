package com.fitshare.data

import java.io.ByteArrayInputStream
import java.time.OffsetDateTime
import java.util.zip.ZipInputStream
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reads GPX, KML and KMZ (OpenTracks, Gadgetbridge, Komoot …) into a [Route].
 * Plain regex instead of an XML parser so it runs in JVM unit tests and copes with sloppy files.
 */
object RouteParser {

    fun isZip(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun looksLikeRoute(bytes: ByteArray): Boolean {
        if (isZip(bytes)) return true
        val head = String(bytes, 0, minOf(bytes.size, 4096))
        return "<gpx" in head || "<kml" in head || "<TrainingCenterDatabase" in head
    }

    fun parse(bytes: ByteArray): Route? {
        val text = if (isZip(bytes)) kmlFromKmz(bytes) ?: return null else String(bytes)
        return when {
            "<kml" in text -> parseKml(text)
            "<gpx" in text -> parseGpx(text)
            "<TrainingCenterDatabase" in text -> parseTcx(text)
            else -> null
        }
    }

    private fun kmlFromKmz(bytes: ByteArray): String? =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name.endsWith(".kml", ignoreCase = true) }
                ?.let { zip.readBytes().decodeToString() }
        }

    internal class Pt(val lat: Double, val lon: Double, val ele: Double?, val time: Long?)

    // OpenTracks writes empty <coord/> after some <when>; pairing by document order keeps them aligned.
    private val kmlTrackRegex = Regex("<(?:gx:)?Track>(.*?)</(?:gx:)?Track>", RegexOption.DOT_MATCHES_ALL)
    private val kmlTagRegex = Regex("<when>(.*?)</when>|<(?:gx:)?coord>(.*?)</(?:gx:)?coord>|<(?:gx:)?coord\\s*/>")
    private val kmlLineRegex = Regex("<LineString>.*?<coordinates>(.*?)</coordinates>", RegexOption.DOT_MATCHES_ALL)

    fun parseKml(kml: String): Route? {
        val segments = kmlTrackRegex.findAll(kml).map { track ->
            val pts = mutableListOf<Pt>()
            var pending: Long? = null
            for (m in kmlTagRegex.findAll(track.groupValues[1])) {
                val whenText = m.groupValues[1]
                if (whenText.isNotEmpty()) { pending = parseTime(whenText); continue }
                val p = m.groupValues[2].trim().split(Regex("\\s+"))
                if (p.size >= 2) {
                    val lon = p[0].toDoubleOrNull(); val lat = p[1].toDoubleOrNull()
                    if (lat != null && lon != null) pts += Pt(lat, lon, p.getOrNull(2)?.toDoubleOrNull(), pending)
                }
                pending = null
            }
            pts
        }.filter { it.isNotEmpty() }.toList()

        if (segments.isNotEmpty()) return build(segments)

        val lines = kmlLineRegex.findAll(kml).map { m ->
            m.groupValues[1].trim().split(Regex("\\s+")).mapNotNull { tuple ->
                val p = tuple.split(",")
                val lon = p.getOrNull(0)?.toDoubleOrNull(); val lat = p.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null) Pt(lat, lon, p.getOrNull(2)?.toDoubleOrNull(), null) else null
            }
        }.filter { it.isNotEmpty() }.toList()
        return build(lines)
    }

    private val gpxSegRegex = Regex("<trkseg[^>]*>(.*?)</trkseg>|<rte[^>]*>(.*?)</rte>", RegexOption.DOT_MATCHES_ALL)
    private val gpxPtRegex = Regex("<(?:trkpt|rtept)\\b([^>]*?)(?:/>|>(.*?)</(?:trkpt|rtept)>)", RegexOption.DOT_MATCHES_ALL)
    private val latRegex = Regex("\\blat\\s*=\\s*[\"']([^\"']+)")
    private val lonRegex = Regex("\\blon\\s*=\\s*[\"']([^\"']+)")
    private val eleRegex = Regex("<ele>(.*?)</ele>")
    private val timeRegex = Regex("<time>(.*?)</time>")

    fun parseGpx(gpx: String): Route? {
        val segments = gpxSegRegex.findAll(gpx).map { seg ->
            val body = seg.groupValues[1].ifEmpty { seg.groupValues[2] }
            gpxPtRegex.findAll(body).mapNotNull { m ->
                val attrs = m.groupValues[1]; val inner = m.groupValues[2]
                val lat = latRegex.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
                val lon = lonRegex.find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
                if (lat == null || lon == null) null
                else Pt(lat, lon,
                    eleRegex.find(inner)?.groupValues?.get(1)?.trim()?.toDoubleOrNull(),
                    timeRegex.find(inner)?.groupValues?.get(1)?.let(::parseTime))
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
        return build(segments)
    }

    // TCX (Garmin, Huawei, Polar, Zepp exports): one <Track> per segment, points without a position are skipped.
    private val tcxTrackRegex = Regex("<Track>(.*?)</Track>", RegexOption.DOT_MATCHES_ALL)
    private val tcxPtRegex = Regex("<Trackpoint>(.*?)</Trackpoint>", RegexOption.DOT_MATCHES_ALL)
    private val tcxLatRegex = Regex("<LatitudeDegrees>(.*?)</LatitudeDegrees>")
    private val tcxLonRegex = Regex("<LongitudeDegrees>(.*?)</LongitudeDegrees>")
    private val tcxEleRegex = Regex("<AltitudeMeters>(.*?)</AltitudeMeters>")
    private val tcxTimeRegex = Regex("<Time>(.*?)</Time>")

    fun parseTcx(tcx: String): Route? {
        val segments = tcxTrackRegex.findAll(tcx).map { track ->
            tcxPtRegex.findAll(track.groupValues[1]).mapNotNull { m ->
                val body = m.groupValues[1]
                val lat = tcxLatRegex.find(body)?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
                val lon = tcxLonRegex.find(body)?.groupValues?.get(1)?.trim()?.toDoubleOrNull()
                if (lat == null || lon == null) null
                else Pt(lat, lon,
                    tcxEleRegex.find(body)?.groupValues?.get(1)?.trim()?.toDoubleOrNull(),
                    tcxTimeRegex.find(body)?.groupValues?.get(1)?.let(::parseTime))
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
        return build(segments)
    }

    private fun parseTime(s: String): Long? = try {
        OffsetDateTime.parse(s.trim()).toInstant().toEpochMilli()
    } catch (e: Exception) { null }

    internal fun build(segments: List<List<Pt>>): Route? {
        if (segments.sumOf { it.size } < 2) return null
        var distance = 0.0
        var gain = 0.0
        var moving = 0L
        for (seg in segments) {
            for (i in 1 until seg.size) distance += haversine(seg[i - 1], seg[i])
            val times = seg.mapNotNull { it.time }
            if (times.size >= 2) moving += times.last() - times.first()
            // GPS altitude is noisy: only count climbs once they exceed 3 m above the last low point.
            var anchor: Double? = null
            for (p in seg) {
                val e = p.ele ?: continue
                val a = anchor
                if (a == null || e < a) anchor = e
                else if (e - a >= 3.0) { gain += e - a; anchor = e }
            }
        }
        return Route(
            profile = elevationProfile(segments),
            segments = segments.map { seg -> seg.map { LatLon(it.lat, it.lon) } },
            distanceMeters = distance,
            elevationGainMeters = gain,
            startMillis = segments.firstNotNullOfOrNull { seg -> seg.firstNotNullOfOrNull { it.time } },
            movingSeconds = (moving / 1000).toInt(),
            splitSeconds = splits(segments)
        )
    }

    /** Time per full kilometre. Pauses between segments don't count, like moving time. */
    internal fun splits(segments: List<List<Pt>>): List<Int> {
        val out = mutableListOf<Int>()
        var dist = 0.0; var moving = 0.0; var lastMark = 0.0
        for (seg in segments) for (i in 1 until seg.size) {
            val a = seg[i - 1]; val b = seg[i]
            val ta = a.time ?: return emptyList(); val tb = b.time ?: return emptyList()
            val d = haversine(a, b); val dt = (tb - ta) / 1000.0
            // a step can cross a km mark; interpolate the time at the mark
            while (d > 0 && dist + d >= (out.size + 1) * 1000.0) {
                val f = ((out.size + 1) * 1000.0 - dist) / d
                val at = moving + dt * f
                out += (at - lastMark).roundToInt()
                lastMark = at
                if (out.size > 2000) return out
            }
            dist += d; moving += dt
        }
        return out
    }

    /** Elevation over distance, resampled to [samples] evenly spaced points for drawing. */
    private fun elevationProfile(segments: List<List<Pt>>, samples: Int = 120): List<Double> {
        val dist = ArrayList<Double>(); val ele = ArrayList<Double>()
        var d = 0.0; var prev: Pt? = null
        for (seg in segments) {
            prev = null // pauses don't add distance
            for (p in seg) {
                prev?.let { d += haversine(it, p) }
                prev = p
                p.ele?.let { dist += d; ele += it }
            }
        }
        if (ele.size < 2 || d <= 0) return emptyList()
        var j = 0
        return List(samples) { i ->
            val target = d * i / (samples - 1)
            while (j < dist.size - 2 && dist[j + 1] < target) j++
            val span = dist[j + 1] - dist[j]
            val t = if (span > 0) ((target - dist[j]) / span).coerceIn(0.0, 1.0) else 0.0
            ele[j] + (ele[j + 1] - ele[j]) * t
        }
    }

    internal fun haversine(a: Pt, b: Pt): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(a.lat); val p2 = Math.toRadians(b.lat)
        val dp = p2 - p1; val dl = Math.toRadians(b.lon - a.lon)
        return 2 * r * asin(sqrt(sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)))
    }
}
