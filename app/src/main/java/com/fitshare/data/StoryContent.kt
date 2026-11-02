package com.fitshare.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Every number FitShare can put on an image. The key stays stable; the label follows the phone's language. */
enum class StatKey(private val de: String, private val en: String) {
    DISTANCE("Distanz", "Distance"),
    TIME("Zeit", "Time"),
    SPEED("Ø Tempo", "Avg speed"),
    PACE("Ø Pace", "Avg pace"),
    ELEVATION("Anstieg", "Elev. gain"),
    AVG_HR("Ø Puls", "Avg HR"),
    MAX_HR("Max Puls", "Max HR"),
    ENERGY("Energie", "Calories"),
    STEPS("Schritte", "Steps"),
    LAPS("Bahnen", "Laps"),
    SWOLF("SWOLF", "SWOLF"),
    STROKES("Züge", "Strokes"),
    STYLE("Stil", "Stroke");

    val label: String get() = tr(de, en)
}

data class Stat(val key: StatKey, val value: String, val unit: String = "") {
    val label: String get() = key.label
}

/** The hero number plus up to six table cells, chosen per sport. */
data class StoryContent(val hero: Stat, val cells: List<Stat>)

private fun loc(): Locale = Locale.getDefault()

fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDistance(meters: Double): Stat =
    if (meters < 1000) Stat(StatKey.DISTANCE, meters.roundToInt().toString(), "M")
    else Stat(StatKey.DISTANCE, String.format(loc(), "%.2f", meters / 1000), "KM")

fun formatInt(n: Int): String = String.format(loc(), "%,d", n)

private fun paceMinSec(secondsPerUnit: Double): String {
    val total = secondsPerUnit.roundToInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/** All stats this workout has data for, in the order that matters most for its sport. */
fun Workout.availableStats(): List<Stat> {
    val s = stats
    val hasMove = distanceMeters > 0 && activeSeconds > 0
    val distance = if (distanceMeters > 0) formatDistance(distanceMeters) else null
    val time = if (activeSeconds > 0) Stat(StatKey.TIME, formatDuration(activeSeconds)) else null
    val speed = when {
        !hasMove -> null
        sport == Sport.CYCLING ->
            Stat(StatKey.SPEED, String.format(loc(), "%.1f", distanceMeters / activeSeconds * 3.6), "KM/H")
        sport == Sport.SWIMMING ->
            Stat(StatKey.PACE, paceMinSec(activeSeconds / (distanceMeters / 100)), "/100M")
        else -> Stat(StatKey.PACE, paceMinSec(activeSeconds / (distanceMeters / 1000)), "/KM")
    }
    val avgHr = s.averageHR.takeIf { it > 0 }?.let { Stat(StatKey.AVG_HR, it.toString(), "BPM") }
    val maxHr = s.maxHR.takeIf { it > 0 }?.let { Stat(StatKey.MAX_HR, it.toString(), "BPM") }
    val kcal = s.calories.takeIf { it > 0 }?.let { Stat(StatKey.ENERGY, formatInt(it), "KCAL") }
    val elev = elevationGainMeters.roundToInt().takeIf { it > 0 }?.let { Stat(StatKey.ELEVATION, formatInt(it), tr("HM", "M")) }
    val steps = s.steps.takeIf { it > 0 }?.let { Stat(StatKey.STEPS, formatInt(it)) }
    val laps = s.laps.takeIf { it > 0 }?.let { Stat(StatKey.LAPS, it.toString()) }
    val swolf = s.swolf.takeIf { it > 0 }?.let { Stat(StatKey.SWOLF, it.toString()) }
    val strokes = s.strokes.takeIf { it > 0 }?.let { Stat(StatKey.STROKES, formatInt(it)) }
    val style = s.swimStyle?.let { Stat(StatKey.STYLE, swimStyleLabel(it)) }

    val order = when (sport) {
        Sport.CYCLING -> listOf(distance, time, speed, elev, avgHr, maxHr, kcal)
        Sport.RUNNING -> listOf(distance, time, speed, avgHr, steps, elev, kcal, maxHr)
        Sport.WALKING -> listOf(distance, time, speed, steps, elev, avgHr, kcal, maxHr)
        Sport.SWIMMING -> listOf(distance, time, speed, laps, swolf, strokes, kcal, style, avgHr, maxHr)
        Sport.WORKOUT -> listOf(time, avgHr, maxHr, kcal, steps, distance)
    }
    return order.filterNotNull()
}

fun Workout.storyContent(): StoryContent {
    val all = availableStats()
    val hero = all.firstOrNull { it.key == StatKey.DISTANCE }
        ?: all.firstOrNull { it.key == StatKey.TIME }
        ?: all.firstOrNull { it.key == StatKey.ENERGY }
        ?: Stat(StatKey.TIME, "—")
    return StoryContent(hero, all.filter { it != hero }.take(6))
}

/**
 * The stats to show when the user may pick up to [max]. [chosen] keeps the tap order;
 * keys this workout has no data for are skipped. Nothing chosen (or nothing usable) falls back to the sport's default.
 */
fun Workout.pickStats(chosen: List<StatKey>?, max: Int): List<Stat> {
    val all = availableStats()
    val picked = chosen.orEmpty().mapNotNull { k -> all.firstOrNull { it.key == k } }.take(max)
    return picked.ifEmpty { all.take(max) }
}

/** "SONNTAG, 21. JUNI 2026 · 17:57" / "SUNDAY, 21 JUNE 2026 · 17:57" */
fun formatStoryDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern(tr("EEEE, d. MMMM yyyy · HH:mm", "EEEE, d MMMM yyyy · HH:mm"), loc())
        .format(Instant.ofEpochMilli(millis).atZone(zone)).uppercase(loc())

/** Short date for overlays: "21.06.2026" / "21 Jun 2026" */
fun formatShortDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern(tr("d. MMM yyyy", "d MMM yyyy"), loc())
        .format(Instant.ofEpochMilli(millis).atZone(zone)).uppercase(loc())

/** Start number on the "bib": month + day, e.g. 0621. */
fun bibNumber(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
    millis?.let { DateTimeFormatter.ofPattern("MMdd").format(Instant.ofEpochMilli(it).atZone(zone)) } ?: "0001"

fun hrZoneName(index: Int): String = listOf(
    tr("Aufwärmen", "Warm-up"), tr("Fettverbrennung", "Fat burn"), tr("Aerob", "Aerobic"),
    tr("Anaerob", "Anaerobic"), tr("Maximal", "Maximum")
)[index]
