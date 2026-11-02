package com.fitshare.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** An invented gravel loop for "See an example", so people see the sticker before they have a file. */
object Demo {
    private const val START = 1_782_036_000_000L // 2026-06-21 10:00 UTC

    fun workout(): Pair<WorkoutStats, Route> {
        val pts = mutableListOf<RouteParser.Pt>()
        var time = START.toDouble()
        var prev: RouteParser.Pt? = null
        for (i in 0..600) {
            val t = 2 * PI * i / 600
            val lat = 48.30 + 0.75 * (0.045 * sin(t) + 0.014 * sin(2 * t + 1.1) + 0.004 * sin(9 * t + 0.4) + 0.002 * sin(23 * t))
            val lon = 9.90 + 0.75 * (0.068 * cos(t) + 0.018 * cos(3 * t + 0.5) + 0.005 * sin(7 * t + 2.0) + 0.002 * cos(19 * t)) // ~35 km
            val ele = 480 + 55 * sin(2 * t) + 18 * sin(5 * t + 1)
            val p = RouteParser.Pt(lat, lon, ele, time.toLong())
            prev?.let { time += RouteParser.haversine(it, p) / (6.6 + 1.4 * cos(3 * t)) * 1000 }
            pts += RouteParser.Pt(lat, lon, ele, time.toLong())
            prev = p
        }
        val route = RouteParser.build(listOf(pts))!!
        val hr = List(120) { i -> 128 + 22 * sin(PI * i / 60) + 9 * sin(2 * PI * i / 13) }
        val stats = WorkoutStats(
            startMillis = START,
            activeSeconds = route.movingSeconds,
            distanceMeters = route.distanceMeters,
            calories = 812,
            averageHR = hr.average().toInt(),
            maxHR = hr.max().toInt() + 4,
            hrZoneSeconds = listOf(0.08, 0.27, 0.41, 0.19, 0.05).map { it * route.movingSeconds },
            sport = Sport.CYCLING,
            elevationGainMeters = route.elevationGainMeters,
            hrCurve = hr
        )
        return stats to route
    }
}
