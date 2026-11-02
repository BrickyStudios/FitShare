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
}
