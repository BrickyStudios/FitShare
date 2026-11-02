package com.fitshare.data

/**
 * Where a workout comes from. The start screen shows the steps for one source at a time;
 * Polar Flow, COROS and plain files live under [OTHER].
 */
enum class Source(val label: String, val packages: List<String>) {
    MI_FITNESS("Mi Fitness", listOf("com.xiaomi.wearable", "com.mi.health")),
    GARMIN("Garmin Connect", listOf("com.garmin.android.apps.connectmobile")),
    ZEPP("Zepp", listOf("com.huami.watch.hmwatchmanager", "com.xiaomi.hm.health")),
    HUAWEI("Huawei Health", listOf("com.huawei.health")),
    SAMSUNG("Samsung Health", listOf("com.sec.android.app.shealth")),
    STRAVA("Strava", listOf("com.strava")),
    GADGETBRIDGE("Gadgetbridge", listOf("nodomain.freeyourgadget.gadgetbridge", "nodomain.freeyourgadget.gadgetbridge.nightly")),
    OTHER("", emptyList());

    val title: String get() = if (this == OTHER) tr("Andere", "Other") else label

    /** How to get the numbers into FitShare. */
    val numbers: String get() = when (this) {
        MI_FITNESS -> tr("Training öffnen → Teilen → ⋯ → FitShare.", "Open the workout → Share → ⋯ → FitShare.")
        GARMIN -> tr("Aktivität öffnen → Teilen → FitShare.", "Open the activity → Share → FitShare.")
        ZEPP -> tr("Training öffnen → Teilen → FitShare.", "Open the workout → Share → FitShare.")
        HUAWEI -> tr("Training öffnen → Teilen → FitShare.", "Open the workout → Share → FitShare.")
        SAMSUNG -> tr("Training öffnen → Teilen → FitShare.", "Open the workout → Share → FitShare.")
        STRAVA -> tr("Aktivität öffnen → Teilen → FitShare.", "Open the activity → Share → FitShare.")
        GADGETBRIDGE -> tr("Training öffnen → ⋮ → „Fit-Datei teilen“ → FitShare.", "Open the workout → ⋮ → “Share FIT File” → FitShare.")
        OTHER -> tr(
            "Screenshot vom Training wählen, oder eine Datei aus Polar Flow, COROS & Co. (FIT, TCX, GPX).",
            "Pick a screenshot of the workout, or a file from Polar Flow, COROS and others (FIT, TCX, GPX)."
        )
    }

    /** How to add the map; null when the numbers step already brings it. */
    val map: String? get() = when (this) {
        MI_FITNESS -> tr(
            "Training → Exportieren → GPX → ⋯ → FitShare. Ohne GPS gibt es nur TCX: Werte, keine Karte.",
            "Workout → Export → GPX → ⋯ → FitShare. Without GPS there is only TCX: numbers, no map."
        )
        GARMIN -> tr("GPX gibt es nur auf connect.garmin.com: Aktivität → ⚙ → „Nach GPX exportieren“.", "GPX only on connect.garmin.com: activity → ⚙ → “Export to GPX”.")
        ZEPP -> tr("Training → GPX exportieren (Uhren mit GPS) → FitShare.", "Workout → Export GPX (watches with GPS) → FitShare.")
        HUAWEI -> tr("Routen-Symbol → Route exportieren → GPX → FitShare.", "Route icon → Export route → GPX → FitShare.")
        SAMSUNG -> tr("Ganz unten im Training: Als GPX exportieren → FitShare.", "At the bottom of the workout: Export as GPX file → FitShare.")
        STRAVA -> tr("GPX gibt es nur auf strava.com: Aktivität → ⋯ → GPX exportieren.", "GPX only on strava.com: activity → ⋯ → Export GPX.")
        GADGETBRIDGE -> tr("Steckt schon in der FIT, wenn die Uhr GPS hat. Sonst die Route mit OpenTracks aufzeichnen.", "Already in the FIT if the watch has GPS. Otherwise record the route with OpenTracks.")
        OTHER -> tr("GPX, TCX, KML oder KMZ dazuwählen.", "Pick a GPX, TCX, KML or KMZ as well.")
    }

    companion object {
        fun fromPackage(pkg: String?): Source? = pkg?.let { p -> entries.firstOrNull { p in it.packages } }

        fun fromName(name: String?): Source? = entries.firstOrNull { it.name == name }

        /**
         * The source to show first: the one picked last (or that last shared into FitShare) while it is still
         * installed, else the first installed fitness app, else [OTHER]. A deliberate [OTHER] stays.
         */
        fun preselect(installed: Set<Source>, last: Source?): Source = when {
            last == OTHER -> OTHER
            last != null && (last in installed || installed.isEmpty()) -> last
            else -> entries.firstOrNull { it in installed } ?: OTHER
        }

        /** Installed apps first, so the right chip is on screen without scrolling; [OTHER] always last. */
        fun ordered(installed: Set<Source>): List<Source> =
            entries.filter { it in installed } + entries.filter { it !in installed && it != OTHER } + OTHER
    }
}
