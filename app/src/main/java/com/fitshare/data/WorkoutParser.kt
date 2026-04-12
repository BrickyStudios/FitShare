package com.fitshare.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object WorkoutParser {

    fun parse(jsonString: String, filename: String = ""): Result<WorkoutData> = runCatching {
        val root = JsonParser.parseString(jsonString).asJsonObject

        val activeSeconds = root.getFloatValue("activeSeconds").toInt()
        val distanceMeters = root.getFloatValue("distanceMeters")
        val calories = root.getFloatValue("active_calories").toInt()
        val strokes = root.getFloatValue("strokes").toInt()
        val laps = root.getFloatValue("laps").toInt()
        val swolfAvg = root.getFloatValue("swolfAvg").toInt()
        val steps = root.getFloatValue("steps").toInt()
        val averageHR = root.getFloatValue("averageHR").toInt()
        val maxHR = root.getFloatValue("maxHR").toInt()
        val vitalityGain = root.getFloatValue("vitality_gain")

        val swimStyleStr = root.getStringValue("swimStyle")
        val swimStyle = if (swimStyleStr.isNotEmpty()) SwimStyle.fromString(swimStyleStr) else null

        val activityType = detectActivityType(root, swimStyle)
        val dateTimeMillis = parseFilenameDate(filename)

        WorkoutData(
            filename = filename,
            activityType = activityType,
            dateTimeMillis = dateTimeMillis,
            activeSeconds = activeSeconds,
            distanceMeters = distanceMeters,
            calories = calories,
            swimStyle = swimStyle,
            laps = laps,
            strokes = strokes,
            swolfAvg = swolfAvg,
            steps = steps,
            averageHR = averageHR,
            maxHR = maxHR,
            vitalityGain = vitalityGain
        )
    }

    private fun detectActivityType(root: JsonObject, swimStyle: SwimStyle?): ActivityType {
        return when {
            swimStyle != null || root.has("swimStyle") || root.has("laps") || root.has("strokes") ->
                ActivityType.SWIMMING
            root.has("steps") && root.getFloatValue("steps") > 0 ->
                ActivityType.RUNNING
            root.has("cadence") ->
                ActivityType.CYCLING
            else -> ActivityType.UNKNOWN
        }
    }

    // Parse filename like "2026-04-07T13_55_47+02_00.json"
    fun parseFilenameDate(filename: String): Long {
        val name = filename.removeSuffix(".json")
        // Replace underscores used for colons/timezone in filename
        // Format: 2026-04-07T13_55_47+02_00  →  2026-04-07T13:55:47+02:00
        val normalized = name
            .replace(Regex("T(\\d{2})_(\\d{2})_(\\d{2})([+-])(\\d{2})_(\\d{2})")) { m ->
                "T${m.groupValues[1]}:${m.groupValues[2]}:${m.groupValues[3]}${m.groupValues[4]}${m.groupValues[5]}:${m.groupValues[6]}"
            }
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            sdf.parse(normalized)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun JsonObject.getFloatValue(key: String): Float {
        if (!has(key)) return 0f
        val obj = get(key)
        return if (obj.isJsonObject) {
            obj.asJsonObject.get("value")?.asFloat ?: 0f
        } else {
            obj.asFloat
        }
    }

    private fun JsonObject.getStringValue(key: String): String {
        if (!has(key)) return ""
        val obj = get(key)
        return if (obj.isJsonObject) {
            obj.asJsonObject.get("value")?.asString ?: ""
        } else {
            obj.asString
        }
    }
}
