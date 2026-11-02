package com.fitshare.data

import kotlin.math.pow

/**
 * Reads Garmin FIT activity files, the format Gadgetbridge now shares workouts in.
 * Only the session summary (message 18) and the records (message 20) are used.
 */
object FitParser {

    private const val FIT_EPOCH = 631_065_600L // 1989-12-31T00:00:00Z in Unix seconds
    private const val MAX_PAUSE_SECONDS = 60L // a longer GPS gap starts a new segment, so pauses don't draw a jump line

    fun looksLikeFit(b: ByteArray): Boolean =
        b.size >= 12 && b[8] == '.'.code.toByte() && b[9] == 'F'.code.toByte() && b[10] == 'I'.code.toByte() && b[11] == 'T'.code.toByte()

    class Result(val stats: WorkoutStats, val route: Route?)

    private class Field(val num: Int, val size: Int, val baseType: Int)
    private class Def(val global: Int, val bigEndian: Boolean, val fields: List<Field>, val devBytes: Int)

    fun parse(bytes: ByteArray): Result {
        require(looksLikeFit(bytes)) { tr("Keine FIT-Datei.", "Not a FIT file.") }
        val headerSize = bytes[0].toInt() and 0xFF
        val dataSize = u(bytes, 4, 4, false)
        val end = minOf(bytes.size, headerSize + dataSize.toInt()) // a truncated file still yields what's there
        val defs = arrayOfNulls<Def>(16)
        var pos = headerSize
        var lastTs = 0L
        var session: Map<Int, List<Long?>>? = null
        val points = mutableListOf<RouteParser.Pt>()

        try {
            while (pos < end) {
                val h = bytes[pos++].toInt() and 0xFF
                if (h and 0x80 != 0) { // compressed timestamp header
                    val def = defs[(h shr 5) and 0x3] ?: error("bad local type")
                    val offset = (h and 0x1F).toLong()
                    lastTs = (lastTs and 0x1F.toLong().inv()) + offset + if (offset < (lastTs and 0x1F)) 0x20 else 0
                    val (p, msg) = handle(bytes, pos, def)
                    pos = p
                    collect(def, msg, lastTs, points)?.let { session = it }
                    continue
                }
                val local = h and 0x0F
                if (h and 0x40 != 0) { // definition message
                    pos++ // reserved
                    val big = bytes[pos++].toInt() == 1
                    val global = u(bytes, pos, 2, big).toInt(); pos += 2
                    val n = bytes[pos++].toInt() and 0xFF
                    val fields = List(n) { i ->
                        val o = pos + i * 3
                        Field(bytes[o].toInt() and 0xFF, bytes[o + 1].toInt() and 0xFF, bytes[o + 2].toInt() and 0xFF)
                    }
                    pos += n * 3
                    var dev = 0
                    if (h and 0x20 != 0) {
                        val nd = bytes[pos++].toInt() and 0xFF
                        for (i in 0 until nd) dev += bytes[pos + i * 3 + 1].toInt() and 0xFF
                        pos += nd * 3
                    }
                    defs[local] = Def(global, big, fields, dev)
                } else {
                    val def = defs[local] ?: error("data before definition")
                    val (p, msg) = handle(bytes, pos, def)
                    pos = p
                    msg[253]?.firstOrNull()?.let { lastTs = it }
                    collect(def, msg, lastTs, points)?.let { session = it }
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            // truncated mid-message: keep what was read so far
        }

        // split records into segments at long gaps
        val segments = mutableListOf<MutableList<RouteParser.Pt>>()
        var prev: RouteParser.Pt? = null
        for (p in points) {
            if (segments.isEmpty() || (prev?.time != null && p.time != null && p.time - prev.time > MAX_PAUSE_SECONDS * 1000)) segments.add(mutableListOf())
            segments.last().add(p); prev = p
        }
        val route = RouteParser.build(segments)

        val s = session ?: error(tr("Die FIT-Datei enthält keine Zusammenfassung.", "The FIT file has no workout summary."))
        fun v(n: Int) = s[n]?.firstOrNull()
        val sport = when (v(5)?.toInt()) { // FIT sport enum
            1 -> Sport.RUNNING
            2 -> Sport.CYCLING
            5 -> Sport.SWIMMING
            11, 17 -> Sport.WALKING
            4, 10 -> Sport.WORKOUT
            else -> null // generic: let the speed decide
        }
        val swim = sport == Sport.SWIMMING
        val zones = s[65]?.map { (it ?: 0L) / 1000.0 }.orEmpty()
        return Result(
            WorkoutStats(
                startMillis = v(2)?.let { (it + FIT_EPOCH) * 1000 },
                activeSeconds = ((v(8) ?: v(7) ?: 0L) / 1000).toInt(),
                distanceMeters = (v(9) ?: 0L) / 100.0,
                calories = (v(11) ?: 0L).toInt(),
                averageHR = (v(16) ?: 0L).toInt(),
                maxHR = (v(17) ?: 0L).toInt(),
                laps = if (swim) (v(26) ?: 0L).toInt() else 0, // every FIT has ≥ 1 lap; only pool laps count
                strokes = if (swim) (v(10) ?: 0L).toInt() else 0,
                hrZoneSeconds = if (zones.sum() > 0) zones else emptyList(),
                sport = sport,
                elevationGainMeters = (v(22) ?: 0L).toDouble()
            ),
            route
        )
    }

    /** Reads one data message; returns the new position and field number → values (null = invalid). */
    private fun handle(b: ByteArray, start: Int, def: Def): Pair<Int, Map<Int, List<Long?>>> {
        var pos = start
        val msg = HashMap<Int, List<Long?>>()
        val wanted = def.global == 18 || def.global == 20
        for (f in def.fields) {
            if (wanted) {
                val base = f.baseType and 0x1F
                val es = SIZES.getOrElse(base) { 1 }
                if (base != 7 && f.size >= es) msg[f.num] = List(f.size / es) { i -> element(b, pos + i * es, es, f.baseType, def.bigEndian) }
            }
            pos += f.size
        }
        if (pos > b.size) throw IndexOutOfBoundsException()
        return (pos + def.devBytes) to msg
    }

    private fun collect(def: Def, msg: Map<Int, List<Long?>>, ts: Long, points: MutableList<RouteParser.Pt>): Map<Int, List<Long?>>? {
        if (def.global == 18) return msg
        if (def.global == 20) {
            val lat = msg[0]?.firstOrNull(); val lon = msg[1]?.firstOrNull()
            if (lat != null && lon != null) {
                val semi = 180.0 / 2.0.pow(31)
                val alt = (msg[78]?.firstOrNull() ?: msg[2]?.firstOrNull())?.let { it / 5.0 - 500 }
                val t = msg[253]?.firstOrNull() ?: ts
                points += RouteParser.Pt(lat * semi, lon * semi, alt, (t + FIT_EPOCH) * 1000)
            }
        }
        return null
    }

    // byte size per FIT base type number (low 5 bits)
    private val SIZES = intArrayOf(1, 1, 1, 2, 2, 4, 4, 1, 4, 8, 1, 2, 4, 1, 8, 8, 8)

    private fun element(b: ByteArray, o: Int, size: Int, baseType: Int, big: Boolean): Long? {
        val raw = u(b, o, size, big)
        val base = baseType and 0x1F
        if (base == 8 || base == 9) return null // floats aren't needed here
        val all = if (size == 8) -1L else (1L shl (size * 8)) - 1
        return when (base) {
            1, 3, 5, 14 -> { // signed: invalid is the max positive value
                val shift = 64 - size * 8
                val signed = (raw shl shift) shr shift
                if (raw == (all ushr 1)) null else signed
            }
            10, 11, 12, 16 -> if (raw == 0L) null else raw // "z" types: 0 is invalid
            else -> if (raw == all) null else raw
        }
    }

    private fun u(b: ByteArray, o: Int, size: Int, big: Boolean): Long {
        var r = 0L
        for (i in 0 until size) {
            val byte = (b[o + i].toLong() and 0xFF)
            r = if (big) (r shl 8) or byte else r or (byte shl (8 * i))
        }
        return r
    }
}
