package com.fitshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** OCR text in the shape the apps' share pictures produce: value and label on separate lines. */
class ShareCardParserTest {

    @Test fun miFitnessGerman() {
        val s = ShareCardParser.parse("""
            Radfahren im Freien
            02.11.2026 10:15
            42,18
            Kilometer
            1:38:22
            Dauer
            25,7 km/h
            Ø Geschwindigkeit
            142 bpm
            Ø Herzfrequenz
            171 bpm
            Max. Herzfrequenz
            812 kcal
            Kalorien
        """.trimIndent())!!
        assertEquals(42180.0, s.distanceMeters, 0.1)
        assertEquals(5902, s.activeSeconds)
        assertEquals(142, s.averageHR)
        assertEquals(171, s.maxHR)
        assertEquals(812, s.calories)
        assertEquals(Sport.CYCLING, s.sport)
    }

    @Test fun garminConnectEnglish() {
        val s = ShareCardParser.parse("""
            Cologne Running
            Distance
            10.02 km
            Time
            52:41
            Avg Pace
            5:15 /km
            Total Ascent
            84 m
            Calories
            1,034
            Avg HR
            151
        """.trimIndent())!!
        assertEquals(10020.0, s.distanceMeters, 0.1)
        assertEquals(52 * 60 + 41, s.activeSeconds)
        assertEquals(84.0, s.elevationGainMeters, 0.1)
        assertEquals(1034, s.calories)
        assertEquals(151, s.averageHR)
        assertEquals(Sport.RUNNING, s.sport)
    }

    @Test fun stravaRowLayoutAndMiles() {
        val s = ShareCardParser.parse("""
            Morning Ride
            Distance Elev Gain Time
            26.2 mi 289 m 1h 38m
        """.trimIndent())!!
        assertEquals(26.2 * 1609.344, s.distanceMeters, 1.0)
        assertEquals(289.0, s.elevationGainMeters, 0.1)
        assertEquals(5880, s.activeSeconds)
    }

    @Test fun paceSpeedAndClockAreNotDistanceOrTime() {
        val s = ShareCardParser.parse("""
            Run 18:45
            5:32 /km
            12,4 km/h
            3,5 km
        """.trimIndent())!!
        assertEquals(3500.0, s.distanceMeters, 0.1)
        assertEquals(0, s.activeSeconds)
    }

    @Test fun noNumbersMeansNoWorkout() {
        assertNull(ShareCardParser.parse("Holiday photo\nBeach"))
        assertNull(ShareCardParser.parse(""))
    }

    @Test fun detectsImageBytes() {
        assertTrue(ShareCardParser.looksLikeImage(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())))
        assertTrue(ShareCardParser.looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertFalse(ShareCardParser.looksLikeImage("{\"a\":1}".toByteArray()))
        assertFalse(ShareCardParser.looksLikeImage(".FIT".toByteArray()))
    }

    @Test fun tcxTrackBecomesRoute() {
        val tcx = """<?xml version="1.0"?><TrainingCenterDatabase><Activities><Activity Sport="Biking"><Lap><Track>
            <Trackpoint><Time>2026-06-21T10:00:00Z</Time><HeartRateBpm><Value>120</Value></HeartRateBpm></Trackpoint>
            <Trackpoint><Time>2026-06-21T10:00:10Z</Time><Position><LatitudeDegrees>48.30</LatitudeDegrees><LongitudeDegrees>9.90</LongitudeDegrees></Position><AltitudeMeters>500</AltitudeMeters></Trackpoint>
            <Trackpoint><Time>2026-06-21T10:10:10Z</Time><Position><LatitudeDegrees>48.31</LatitudeDegrees><LongitudeDegrees>9.90</LongitudeDegrees></Position><AltitudeMeters>510</AltitudeMeters></Trackpoint>
            </Track></Lap></Activity></Activities></TrainingCenterDatabase>""".toByteArray()
        assertTrue(RouteParser.looksLikeRoute(tcx))
        val r = RouteParser.parse(tcx)!!
        assertEquals(2, r.pointCount)
        assertEquals(1112.0, r.distanceMeters, 5.0)
        assertEquals(600, r.movingSeconds)
        assertEquals(10.0, r.elevationGainMeters, 0.1)
    }
}
