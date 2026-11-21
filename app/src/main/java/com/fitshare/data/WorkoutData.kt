package com.fitshare.data

data class LatLon(val lat: Double, val lon: Double)

/** A GPS track from GPX/KML/KMZ. Segments are kept apart so pauses don't draw a jump line. */
data class Route(
    val segments: List<List<LatLon>>,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val startMillis: Long?,
    val movingSeconds: Int,
    /** Elevation in metres, resampled to evenly spaced distance steps; empty if the file has none. */
    val profile: List<Double> = emptyList(),
    /** Seconds for each full kilometre, in order; empty without timestamps. */
    val splitSeconds: List<Int> = emptyList()
) {
    val pointCount: Int get() = segments.sumOf { it.size }
}

/** What a Gadgetbridge workout JSON contains. Missing values are 0 / null. */
data class WorkoutStats(
    val startMillis: Long? = null,
    val activeSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val calories: Int = 0,
    val averageHR: Int = 0,
    val maxHR: Int = 0,
    val laps: Int = 0,
    val strokes: Int = 0,
    val swolf: Int = 0,
    val steps: Int = 0,
    val swimStyle: String? = null,
    /** Seconds in the five Gadgetbridge HR zones (warm-up … extreme), or empty. */
    val hrZoneSeconds: List<Double> = emptyList(),
    /** Only FIT files say which sport it was; JSON leaves it null. */
    val sport: Sport? = null,
    /** Barometric climb from the watch (FIT); 0 if unknown. */
    val elevationGainMeters: Double = 0.0,
    /** Heart rate over the workout, resampled to evenly spaced points (FIT only). */
    val hrCurve: List<Double> = emptyList(),
    /** The watch never paused (Mi Band): [activeSeconds] includes every stop, so the route's moving time is better. */
    val stopsIncluded: Boolean = false
) {
    val hasSwimData: Boolean get() = laps > 0 || strokes > 0 || swolf > 0 || swimStyle != null
}

enum class Sport(private val de: String, private val en: String, val accent: Int) {
    CYCLING("Radfahren", "Cycling", 0xFFFF5B1F.toInt()),
    RUNNING("Laufen", "Running", 0xFFC6F432.toInt()),
    SWIMMING("Schwimmen", "Swimming", 0xFF27D3F5.toInt()),
    WALKING("Wandern", "Hiking", 0xFFFFB423.toInt()),
    WORKOUT("Training", "Workout", 0xFFFF4F8B.toInt());

    val label: String get() = tr(de, en)
}

/** Everything the story shows: JSON values, filled up from the route where the JSON has gaps. */
data class Workout(
    val sport: Sport,
    val startMillis: Long?,
    val activeSeconds: Int,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val stats: WorkoutStats,
    val route: Route?
)

fun mergeWorkout(stats: WorkoutStats?, route: Route?, sportOverride: Sport?): Workout {
    val s = stats ?: WorkoutStats()
    val distance = if (s.distanceMeters > 0) s.distanceMeters else route?.distanceMeters ?: 0.0
    val moving = route?.movingSeconds ?: 0
    val seconds = when {
        s.activeSeconds <= 0 -> moving
        // pauses out, but only if the track covers at least half the workout (a late GPS start mustn't shrink it)
        s.stopsIncluded && moving in s.activeSeconds / 2 until s.activeSeconds -> moving
        else -> s.activeSeconds
    }
    return Workout(
        sport = sportOverride ?: guessSport(s, distance, seconds),
        startMillis = s.startMillis ?: route?.startMillis,
        activeSeconds = seconds,
        distanceMeters = distance,
        elevationGainMeters = s.elevationGainMeters.takeIf { it > 0 } ?: route?.elevationGainMeters ?: 0.0,
        stats = s,
        route = route
    )
}

/** Gadgetbridge JSON doesn't export the sport, so infer it from what the file contains and the speed. */
fun guessSport(s: WorkoutStats, distanceMeters: Double, seconds: Int): Sport {
    s.sport?.let { return it }
    if (s.hasSwimData) return Sport.SWIMMING
    val kmh = if (distanceMeters > 0 && seconds > 0) distanceMeters / seconds * 3.6 else 0.0
    return when {
        kmh >= 13.0 -> Sport.CYCLING
        kmh >= 6.5 -> Sport.RUNNING
        kmh > 0.0 -> Sport.WALKING
        s.steps > 0 -> Sport.RUNNING
        else -> Sport.WORKOUT
    }
}

fun swimStyleLabel(raw: String): String = when (raw.lowercase()) {
    "freestyle" -> tr("Kraul", "Freestyle")
    "breaststroke" -> tr("Brust", "Breaststroke")
    "backstroke" -> tr("Rücken", "Backstroke")
    "butterfly" -> tr("Delfin", "Butterfly")
    "mixed", "medley" -> tr("Lagen", "Medley")
    else -> raw.replaceFirstChar { it.uppercase() }
}

/**
 * Whether a workout file and a route are the same outing, judged by start time. Watch and GPS app are
 * started by hand, so 30 minutes of slack. Null if one of them has no start time.
 */
fun sameStart(stats: WorkoutStats?, route: Route?): Boolean? {
    val a = stats?.startMillis ?: return null
    val b = route?.startMillis ?: return null
    return kotlin.math.abs(a - b) <= 30 * 60_000L
}
