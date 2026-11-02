package com.fitshare.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.OffsetDateTime

object WorkoutParser {

    private val ZONE_KEYS = listOf("hrZoneWarmUp", "hrZoneFatBurn", "hrZoneAerobic", "hrZoneAnaerobic", "hrZoneExtreme")

    fun looksLikeJson(bytes: ByteArray): Boolean =
        bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.toChar() == '{'

    fun parse(jsonString: String, filename: String = ""): Result<WorkoutStats> = runCatching {
        val root = try {
            JsonParser.parseString(jsonString).asJsonObject
        } catch (e: RuntimeException) { // JsonParseException, IllegalStateException for arrays/primitives
            throw IllegalArgumentException(tr("Die JSON-Datei ist unvollständig oder beschädigt. Bitte neu aus Gadgetbridge exportieren.", "The JSON file is incomplete or damaged. Export it again from Gadgetbridge."))
        }
        val known = listOf("activeSeconds", "distanceMeters", "active_calories", "averageHR", "laps", "steps")
        require(known.any { root.has(it) }) { tr("Das sieht nicht nach einem Gadgetbridge-Workout aus.", "This doesn't look like a Gadgetbridge workout.") }

        val zones = ZONE_KEYS.map { root.num(it) }
        WorkoutStats(
            startMillis = parseFilenameDate(filename),
            activeSeconds = root.num("activeSeconds").toInt(),
            distanceMeters = root.num("distanceMeters"),
            calories = root.num("active_calories").toInt(),
            averageHR = root.num("averageHR").toInt(),
            maxHR = root.num("maxHR").toInt(),
            laps = root.num("laps").toInt(),
            strokes = root.num("strokes").toInt(),
            swolf = root.num("swolfAvg").toInt(),
            steps = root.num("steps").toInt(),
            swimStyle = root.str("swimStyle").takeIf { it.isNotBlank() },
            hrZoneSeconds = if (zones.sum() > 0) zones else emptyList()
        )
    }

    private val filenameDate = Regex("(\\d{4}-\\d{2}-\\d{2})T(\\d{2})_(\\d{2})_(\\d{2})([+-]\\d{2})_(\\d{2})")

    /** Gadgetbridge names exports like "2026-04-07T13_55_47+02_00.json"; null if the name doesn't say. */
    fun parseFilenameDate(filename: String): Long? {
        val m = filenameDate.find(filename) ?: return null
        val (d, h, min, s, oh, om) = m.destructured
        return try {
            OffsetDateTime.parse("${d}T$h:$min:$s$oh:$om").toInstant().toEpochMilli()
        } catch (e: Exception) { null }
    }

    // Gadgetbridge wraps values as {"unit": "...", "value": 1.0}; older exports use bare numbers.
    private fun JsonObject.value(key: String): JsonElement? {
        val e = get(key) ?: return null
        return if (e.isJsonObject) e.asJsonObject.get("value") else e
    }

    private fun JsonObject.num(key: String): Double =
        value(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble }.getOrNull() } ?: 0.0

    private fun JsonObject.str(key: String): String =
        value(key)?.takeIf { it.isJsonPrimitive }?.asString ?: ""

    private val tcxLapRegex = Regex("<Lap\\b[^>]*>(.*?)</Lap>", RegexOption.DOT_MATCHES_ALL)
    private val tcxIdRegex = Regex("<Id>(.*?)</Id>")
    private val tcxSportRegex = Regex("<Activity[^>]*Sport=\"([^\"]*)\"")

    /**
     * Workout totals from the <Lap> summaries of a TCX. Mi Fitness ("Exportieren") writes them even without GPS,
     * with the average heart rate as a bare <HeartRateBpm> number; Garmin & co. use <AverageHeartRateBpm><Value>.
     * Null if the file has no lap times.
     */
    fun parseTcxStats(tcx: String): WorkoutStats? {
        val laps = tcxLapRegex.findAll(tcx).map { it.groupValues[1].substringBefore("<Track>") }.toList()
        fun List<String>.num(pattern: String) = map { Regex(pattern).find(it)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0 }
        val seconds = laps.num("<TotalTimeSeconds>\\s*([\\d.]+)")
        if (seconds.sum() <= 0) return null
        val hr = laps.num("<AverageHeartRateBpm>\\s*<Value>\\s*([\\d.]+)").zip(laps.num("<HeartRateBpm>\\s*([\\d.]+)")) { a, b -> a.takeIf { it > 0 } ?: b }
        val hrSeconds = seconds.zip(hr).filter { it.second > 0 }
        val avgHr = if (hrSeconds.isEmpty()) 0.0 else hrSeconds.sumOf { it.first * it.second } / hrSeconds.sumOf { it.first }.coerceAtLeast(1.0)
        val sport = tcxSportRegex.find(tcx)?.groupValues?.get(1)?.lowercase().orEmpty()
        return WorkoutStats(
            startMillis = tcxIdRegex.find(tcx)?.groupValues?.get(1)?.let { tcxTime(it.trim(), local = "creator=\"Mi Fitness\"" in tcx) },
            activeSeconds = seconds.sum().toInt(),
            distanceMeters = laps.num("<DistanceMeters>\\s*([\\d.]+)").sum(),
            calories = laps.num("<Calories>\\s*([\\d.]+)").sum().toInt(),
            averageHR = avgHr.toInt(),
            maxHR = laps.num("<MaximumHeartRateBpm>\\s*<Value>\\s*([\\d.]+)").maxOrNull()?.toInt() ?: 0,
            steps = laps.num("<Steps>\\s*([\\d.]+)").sum().toInt(),
            elevationGainMeters = laps.num("<CumulativeClimb>\\s*([\\d.]+)").sum(),
            // Garmin writes Biking/Running/Other; Mi Fitness the sport name in the phone's language
            sport = when {
                listOf("bik", "cycl", "rad", "fahr").any { it in sport } -> Sport.CYCLING
                listOf("run", "lauf").any { it in sport } -> Sport.RUNNING
                listOf("swim", "schwimm").any { it in sport } -> Sport.SWIMMING
                listOf("walk", "hik", "wander", "gehen").any { it in sport } -> Sport.WALKING
                else -> null
            }
        )
    }

    // Mi Fitness formats local time with a literal "Z", so its timestamps are not UTC.
    private fun tcxTime(s: String, local: Boolean): Long? = runCatching {
        if (local) java.time.LocalDateTime.parse(s.removeSuffix("Z")).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        else OffsetDateTime.parse(s).toInstant().toEpochMilli()
    }.getOrNull()
}
