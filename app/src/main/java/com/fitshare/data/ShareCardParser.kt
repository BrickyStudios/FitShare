package com.fitshare.data

/**
 * Pulls workout numbers out of the text on a share picture (Mi Fitness, Garmin Connect, Zepp,
 * Huawei Health, Samsung Health, Strava, …). Input is the OCR text, one line per text line.
 * Units do most of the work, so it doesn't matter which language the app was set to.
 */
object ShareCardParser {

    fun looksLikeImage(bytes: ByteArray): Boolean {
        fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        val ascii = String(bytes, 0, minOf(bytes.size, 12), Charsets.ISO_8859_1)
        return (at(0) == 0x89 && ascii.startsWith("PNG", 1)) ||           // PNG
            (at(0) == 0xFF && at(1) == 0xD8) ||                           // JPEG
            (ascii.startsWith("RIFF") && ascii.startsWith("WEBP", 8)) ||  // WebP
            ascii.startsWith("ftyp", 4)                                   // HEIC/AVIF
    }

    private val ws = "[\\s\\u00A0\\u202F]*"
    private const val INT = "(\\d{1,3}(?:[.,'\\u00A0\\u202F ]\\d{3})+|\\d+)"

    // "12,34 km" but not "24,5 km/h" and not the "32" of "5:32 km"
    private val km = Regex("(?<![\\d:.,])(\\d+(?:[.,]\\d{1,3})?)${ws}(km|mi|kilometer|meilen|miles)\\b(?!$ws/)", RegexOption.IGNORE_CASE)
    private val meters = Regex("(?<![\\d:.,])$INT${ws}m\\b(?!$ws/)")
    private val hms = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2}):(\\d{2})(?![\\d:])")
    private val hm = Regex("(\\d{1,2})$ws(?:h|std\\.?|hr)$ws(\\d{1,2})$ws(?:m|min)\\b(?:$ws(\\d{1,2})${ws}s\\b)?", RegexOption.IGNORE_CASE)
    private val minOnly = Regex("(?<![\\d:.,])(\\d{1,3})$ws(?:min|minuten|minutes)\\b(?!$ws/)", RegexOption.IGNORE_CASE)
    private val ms = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})(?![\\d:])(?!$ws(?:/|min/|'|\"|″|′|/km|/mi))")
    private val bpm = Regex("(?<!\\d)(\\d{2,3})$ws(?:bpm|s/min|schläge|♥)", RegexOption.IGNORE_CASE)
    private val kcal = Regex("(?<![\\d.,])$INT$ws(?:kcal|kalorien|calories|cal)\\b", RegexOption.IGNORE_CASE)
    private val hmUnit = Regex("(?<![\\d.,])$INT${ws}hm\\b", RegexOption.IGNORE_CASE)
    private val intAlone = Regex("^\\D{0,3}$INT\\D{0,6}$")

    private val distanceLabel = Regex("distanz|distance|strecke|entfernung|距离", RegexOption.IGNORE_CASE)
    private val timeLabel = Regex("dauer|zeit|time|duration|bewegungszeit|trainingszeit|moving", RegexOption.IGNORE_CASE)
    private val hrLabel = Regex("herzfrequenz|heart\\s*rate|puls|\\bhf\\b|\\bhr\\b|心率", RegexOption.IGNORE_CASE)
    private val maxLabel = Regex("max", RegexOption.IGNORE_CASE)
    private val avgLabel = Regex("ø|⌀|avg|average|durchschn|mittl|mittel|schnitt", RegexOption.IGNORE_CASE)
    private val calLabel = Regex("kalorien|calories|energie|kcal|verbrauch", RegexOption.IGNORE_CASE)
    private val elevLabel = Regex("anstieg|aufstieg|höhenmeter|höhengewinn|elev|ascent|climb|gain|bergauf", RegexOption.IGNORE_CASE)
    private val stepsLabel = Regex("schritte|steps", RegexOption.IGNORE_CASE)

    private val sports = listOf(
        Sport.CYCLING to Regex("rad|fahrrad|cycling|ride|bike|biking|gravel|mtb|velo", RegexOption.IGNORE_CASE),
        Sport.RUNNING to Regex("lauf|laufen|run|running|jogging|trail", RegexOption.IGNORE_CASE),
        Sport.SWIMMING to Regex("schwimm|swim", RegexOption.IGNORE_CASE),
        Sport.WALKING to Regex("wander|hike|hiking|walk|gehen|spazier", RegexOption.IGNORE_CASE)
    )

    /** Null when the picture holds no workout numbers at all. */
    fun parse(text: String): WorkoutStats? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val all = lines.joinToString("\n")

        val distance = (labeled(lines, distanceLabel, km) ?: km.find(all))?.let { m ->
            val v = decimal(m.groupValues[1])
            if (m.groupValues[2].lowercase().startsWith("mi") || m.groupValues[2].lowercase() == "meilen") v * 1609.344 else v * 1000
        } ?: labeled(lines, distanceLabel, meters)?.let { int(it.groupValues[1]).toDouble() } ?: 0.0

        val seconds = duration(lines, all)

        val hrValues = lines.indices.flatMap { i -> bpm.findAll(lines[i]).map { i to it.groupValues[1].toInt() }.toList() }
            .filter { it.second in 30..240 }
        fun hrNear(label: Regex): Int? = hrValues.firstOrNull { (i, _) -> around(i).any { j -> j in lines.indices && label.containsMatchIn(lines[j]) } }?.second
            ?: labeled(lines, label, intAlone, needs = hrLabel)?.let { int(it.groupValues[1]) }?.takeIf { it in 30..240 }
        val maxHr = hrNear(maxLabel) ?: 0
        val avgHr = hrNear(avgLabel)?.takeIf { it != maxHr || hrValues.size == 1 }
            ?: hrValues.firstOrNull { it.second != maxHr }?.second
            ?: labeled(lines, hrLabel, intAlone)?.let { int(it.groupValues[1]) }?.takeIf { it in 30..240 && it != maxHr }
            ?: 0

        val calories = (kcal.find(all) ?: labeled(lines, calLabel, intAlone))?.let { int(it.groupValues[1]) }?.takeIf { it < 20_000 } ?: 0
        val elevation = (labeled(lines, elevLabel, meters) ?: hmUnit.find(all) ?: labeled(lines, elevLabel, intAlone))
            ?.let { int(it.groupValues[1]).toDouble() }?.takeIf { it < 10_000 } ?: 0.0
        val steps = labeled(lines, stepsLabel, intAlone)?.let { int(it.groupValues[1]) } ?: 0
        val sport = lines.firstNotNullOfOrNull { line -> sports.firstOrNull { it.second.containsMatchIn(line) }?.first }

        if (distance <= 0 && seconds <= 0 && avgHr <= 0 && calories <= 0) return null
        return WorkoutStats(
            activeSeconds = seconds,
            distanceMeters = distance.takeIf { it < 1_000_000 } ?: 0.0,
            calories = calories,
            averageHR = avgHr,
            maxHR = maxHr,
            steps = steps,
            sport = sport,
            elevationGainMeters = elevation
        )
    }

    private fun duration(lines: List<String>, all: String): Int {
        hms.find(all)?.let { (h, m, s) -> return h.toInt() * 3600 + m.toInt() * 60 + s.toInt() }
        hm.find(all)?.let { m -> return m.groupValues[1].toInt() * 3600 + m.groupValues[2].toInt() * 60 + (m.groupValues[3].toIntOrNull() ?: 0) }
        // "45:12" is a time of day or a pace just as often, so only trust it next to a time label
        labeled(lines, timeLabel, ms)?.let { (m, s) -> return m.toInt() * 60 + s.toInt() }
        labeled(lines, timeLabel, minOnly)?.let { return it.groupValues[1].toInt() * 60 }
        return 0
    }

    private operator fun MatchResult.component1() = groupValues[1]
    private operator fun MatchResult.component2() = groupValues[2]
    private operator fun MatchResult.component3() = groupValues[3]

    private fun around(i: Int) = listOf(i, i + 1, i - 1, i + 2, i - 2)

    /** First [value] on a line with [label] or up to two lines away; [needs] must also appear nearby if given. */
    private fun labeled(lines: List<String>, label: Regex, value: Regex, needs: Regex? = null): MatchResult? {
        for (i in lines.indices) {
            if (!label.containsMatchIn(lines[i])) continue
            if (needs != null && around(i).none { j -> j in lines.indices && needs.containsMatchIn(lines[j]) }) continue
            for (j in around(i)) {
                if (j !in lines.indices) continue
                // a label line can carry digits of its own ("Ø HF"), so strip the label before matching
                val text = if (j == i) lines[j].replace(label, " ").trim() else lines[j]
                value.find(text)?.let { return it }
            }
        }
        return null
    }

    private fun decimal(s: String) = s.replace(',', '.').toDouble()
    // OCR can glue numbers together; anything too long to be a stat counts as nothing
    private fun int(s: String) = s.filter { it.isDigit() }.take(9).toInt()
}
