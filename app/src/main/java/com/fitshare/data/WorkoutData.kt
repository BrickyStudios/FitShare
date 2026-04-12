package com.fitshare.data

import androidx.compose.ui.graphics.Color

data class WorkoutData(
    val filename: String = "",
    val activityType: ActivityType = ActivityType.UNKNOWN,
    val dateTimeMillis: Long = 0L,
    // Time
    val activeSeconds: Int = 0,
    // Distance
    val distanceMeters: Float = 0f,
    // Calories
    val calories: Int = 0,
    // Swimming specific
    val swimStyle: SwimStyle? = null,
    val laps: Int = 0,
    val strokes: Int = 0,
    val swolfAvg: Int = 0,
    // Running specific
    val steps: Int = 0,
    // Heart rate
    val averageHR: Int = 0,
    val maxHR: Int = 0,
    // Other
    val vitalityGain: Float = 0f,
    val gpsTrack: List<LatLon>? = null
)

enum class ActivityType(
    val labelDe: String,
    val emoji: String,
    val accentColor: Color,
    val gradientStart: Color,
    val gradientEnd: Color
) {
    SWIMMING(
        labelDe = "Beckenschwimmen",
        emoji = "🏊",
        accentColor = Color(0xFF2196F3),
        gradientStart = Color(0xFF0A1628),
        gradientEnd = Color(0xFF1A3A5C)
    ),
    RUNNING(
        labelDe = "Laufen",
        emoji = "🏃",
        accentColor = Color(0xFF4CAF50),
        gradientStart = Color(0xFF0A1A0A),
        gradientEnd = Color(0xFF1A3A1A)
    ),
    CYCLING(
        labelDe = "Radfahren",
        emoji = "🚴",
        accentColor = Color(0xFFFF9800),
        gradientStart = Color(0xFF1A1200),
        gradientEnd = Color(0xFF3A2A00)
    ),
    UNKNOWN(
        labelDe = "Training",
        emoji = "💪",
        accentColor = Color(0xFF9C27B0),
        gradientStart = Color(0xFF120A1A),
        gradientEnd = Color(0xFF2A1A3A)
    )
}

enum class SwimStyle(val labelDe: String) {
    FREESTYLE("Freistil"),
    BREASTSTROKE("Brust"),
    BACKSTROKE("Rücken"),
    BUTTERFLY("Schmetterling"),
    MIXED("Gemischt"),
    UNKNOWN("Unbekannt");

    companion object {
        fun fromString(value: String): SwimStyle = when (value.lowercase()) {
            "freestyle" -> FREESTYLE
            "breaststroke" -> BREASTSTROKE
            "backstroke" -> BACKSTROKE
            "butterfly" -> BUTTERFLY
            "mixed" -> MIXED
            else -> UNKNOWN
        }
    }
}

fun WorkoutData.formattedTime(): String {
    val hours = activeSeconds / 3600
    val minutes = (activeSeconds % 3600) / 60
    val seconds = activeSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

fun WorkoutData.formattedDistance(): Pair<String, String> {
    return if (distanceMeters >= 1000f) {
        val km = distanceMeters / 1000f
        if (km == km.toLong().toFloat()) {
            "%.0f".format(km) to "km"
        } else {
            "%.2f".format(km) to "km"
        }
    } else {
        "%.0f".format(distanceMeters) to "m"
    }
}

data class LatLon(val lat: Double, val lon: Double)

// Pace in Sekunden pro km (für Laufen)
fun WorkoutData.paceSecondsPerKm(): Int? {
    if (activityType != ActivityType.RUNNING) return null
    if (distanceMeters <= 0f || activeSeconds <= 0) return null
    val km = distanceMeters / 1000f
    return (activeSeconds / km).toInt()
}

// Formatierte Pace als "MM:SS /km"
fun WorkoutData.paceFormatted(): String? {
    val secs = paceSecondsPerKm() ?: return null
    val min = secs / 60
    val sec = secs % 60
    return "%d:%02d /km".format(min, sec)
}

// Geschwindigkeit in km/h (für Radfahren)
fun WorkoutData.speedKmh(): Float? {
    if (activityType != ActivityType.CYCLING) return null
    if (distanceMeters <= 0f || activeSeconds <= 0) return null
    val km = distanceMeters / 1000f
    val hours = activeSeconds / 3600f
    return km / hours
}

// Formatierte Geschwindigkeit als "XX.X km/h"
fun WorkoutData.speedFormatted(): String? {
    val kmh = speedKmh() ?: return null
    return "%.1f km/h".format(kmh)
}

// Pace für Schwimmen: Sekunden pro 100m
fun WorkoutData.swimPaceSeconds(): Int? {
    if (activityType != ActivityType.SWIMMING) return null
    if (distanceMeters <= 0f || activeSeconds <= 0) return null
    val per100m = activeSeconds / (distanceMeters / 100f)
    return per100m.toInt()
}

// Formatierte Schwimm-Pace als "MM:SS /100m"
fun WorkoutData.swimPaceFormatted(): String? {
    val secs = swimPaceSeconds() ?: return null
    val min = secs / 60
    val sec = secs % 60
    return "%d:%02d /100m".format(min, sec)
}

// Liefert die Hauptpace/Speed-Anzeige je nach Sportart
fun WorkoutData.primaryPaceFormatted(): String? = when (activityType) {
    ActivityType.RUNNING -> paceFormatted()
    ActivityType.CYCLING -> speedFormatted()
    ActivityType.SWIMMING -> swimPaceFormatted()
    else -> null
}

// Label für die Pace-Anzeige
fun WorkoutData.primaryPaceLabel(): String = when (activityType) {
    ActivityType.RUNNING -> "Pace"
    ActivityType.CYCLING -> "Tempo"
    ActivityType.SWIMMING -> "Pace/100m"
    else -> ""
}
