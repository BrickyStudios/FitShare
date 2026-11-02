package com.fitshare.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val DE = Locale.GERMAN

data class Stat(val label: String, val value: String, val unit: String = "")

/** The hero number plus up to six table cells, chosen per sport. Shared by preview and export. */
data class StoryContent(val hero: Stat, val cells: List<Stat>)

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDistance(meters: Double): Stat =
    if (meters < 1000) Stat("Distanz", meters.roundToInt().toString(), "M")
    else Stat("Distanz", String.format(DE, "%.2f", meters / 1000), "KM")

fun formatInt(n: Int): String = String.format(DE, "%,d", n)

private fun paceMinSec(secondsPerUnit: Double): String {
    val total = secondsPerUnit.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

fun Workout.storyContent(): StoryContent {
    val s = stats
    val hasMove = distanceMeters > 0 && activeSeconds > 0
    val time = if (activeSeconds > 0) Stat("Zeit", formatDuration(activeSeconds)) else null
    val speed = when {
        !hasMove -> null
        sport == Sport.CYCLING ->
            Stat("Ø Tempo", String.format(DE, "%.1f", distanceMeters / activeSeconds * 3.6), "KM/H")
        sport == Sport.SWIMMING ->
            Stat("Pace", paceMinSec(activeSeconds / (distanceMeters / 100)), "/100M")
        else -> Stat("Ø Pace", paceMinSec(activeSeconds / (distanceMeters / 1000)), "/KM")
    }
    val avgHr = s.averageHR.takeIf { it > 0 }?.let { Stat("Ø Puls", it.toString(), "BPM") }
    val maxHr = s.maxHR.takeIf { it > 0 }?.let { Stat("Max Puls", it.toString(), "BPM") }
    val kcal = s.calories.takeIf { it > 0 }?.let { Stat("Energie", formatInt(it), "KCAL") }
    val elev = elevationGainMeters.roundToInt().takeIf { it > 0 }?.let { Stat("Anstieg", formatInt(it), "HM") }
    val steps = s.steps.takeIf { it > 0 }?.let { Stat("Schritte", formatInt(it)) }
    val laps = s.laps.takeIf { it > 0 }?.let { Stat("Bahnen", it.toString()) }
    val swolf = s.swolf.takeIf { it > 0 }?.let { Stat("SWOLF", it.toString()) }
    val strokes = s.strokes.takeIf { it > 0 }?.let { Stat("Züge", formatInt(it)) }
    val style = s.swimStyle?.let { Stat("Stil", swimStyleLabel(it)) }

    val hero = when {
        distanceMeters > 0 -> formatDistance(distanceMeters)
        time != null -> time
        kcal != null -> kcal
        else -> Stat("Training", "—")
    }
    val order = when (sport) {
        Sport.CYCLING -> listOf(time, speed, elev, avgHr, maxHr, kcal)
        Sport.RUNNING -> listOf(time, speed, avgHr, steps, elev, kcal, maxHr)
        Sport.WALKING -> listOf(time, speed, steps, elev, avgHr, kcal)
        Sport.SWIMMING -> listOf(time, speed, laps, swolf, strokes, kcal, style, avgHr)
        Sport.WORKOUT -> listOf(time, avgHr, maxHr, kcal, steps)
    }
    return StoryContent(hero, order.filterNotNull().filter { it != hero }.take(6))
}

/** "SONNTAG, 21. JUNI 2026 · 17:57" */
fun formatStoryDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy · HH:mm", DE)
        .format(Instant.ofEpochMilli(millis).atZone(zone)).uppercase(DE)

/** Start number on the "bib": month + day, e.g. 0621. */
fun bibNumber(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
    millis?.let { DateTimeFormatter.ofPattern("MMdd").format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "0001"

val HR_ZONE_NAMES = listOf("Aufwärmen", "Fettverbrennung", "Aerob", "Anaerob", "Maximal")
