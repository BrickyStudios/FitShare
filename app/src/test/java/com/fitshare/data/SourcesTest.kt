package com.fitshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {
    @Test fun preselectsTheInstalledApp() {
        assertEquals(Source.MI_FITNESS, Source.preselect(setOf(Source.MI_FITNESS), null))
        assertEquals(Source.OTHER, Source.preselect(emptySet(), null))
    }

    @Test fun lastPickWinsWhileInstalled() {
        assertEquals(Source.GARMIN, Source.preselect(setOf(Source.MI_FITNESS, Source.GARMIN), Source.GARMIN))
        // switched from Garmin to Mi Fitness and uninstalled Garmin
        assertEquals(Source.MI_FITNESS, Source.preselect(setOf(Source.MI_FITNESS), Source.GARMIN))
        // nothing detectable (e.g. hidden by the system): keep what was picked
        assertEquals(Source.STRAVA, Source.preselect(emptySet(), Source.STRAVA))
        assertEquals(Source.OTHER, Source.preselect(setOf(Source.ZEPP), Source.OTHER))
    }

    @Test fun installedComeFirstAndOtherLast() {
        val order = Source.ordered(setOf(Source.STRAVA, Source.ZEPP))
        assertEquals(listOf(Source.ZEPP, Source.STRAVA, Source.MI_FITNESS), order.take(3))
        assertEquals(Source.OTHER, order.last())
        assertEquals(Source.entries.size, order.toSet().size)
    }

    @Test fun senderPackageMapsToSource() {
        assertEquals(Source.MI_FITNESS, Source.fromPackage("com.xiaomi.wearable"))
        assertEquals(Source.ZEPP, Source.fromPackage("com.xiaomi.hm.health"))
        assertEquals(Source.GADGETBRIDGE, Source.fromPackage("nodomain.freeyourgadget.gadgetbridge"))
        assertNull(Source.fromPackage("com.android.intentresolver"))
        assertNull(Source.fromPackage(null))
    }

    @Test fun everyAppHasSteps() {
        for (s in Source.entries) assertTrue(s.name, s.numbers.isNotBlank() && !s.map.isNullOrBlank())
    }

    @Test fun demoIsAPlausibleRide() {
        val (stats, route) = Demo.workout()
        assertTrue(route.distanceMeters in 25_000.0..40_000.0)
        assertTrue(stats.activeSeconds in 3_000..7_000)
        assertTrue(route.splitSeconds.size >= 25)
        assertEquals(120, stats.hrCurve.size)
        assertEquals(Sport.CYCLING, stats.sport)
    }
}
