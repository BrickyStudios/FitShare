package com.fitshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ParsingTest {

    @Before fun german() = Locale.setDefault(Locale.GERMAN)

    // Shape of a real Mi Band 10 cycling export: no distance, no sport, HR zones.
    private val cyclingJson = """
        {"activeSeconds":{"unit":"seconds","value":9663.0},"active_calories":{"unit":"calories_unit","value":1310.0},
         "averageHR":{"unit":"bpm","value":158.0},"maxHR":{"unit":"bpm","value":185.0},
         "hrZoneWarmUp":{"unit":"seconds","value":0.0},"hrZoneFatBurn":{"unit":"seconds","value":102.0},
         "hrZoneAerobic":{"unit":"seconds","value":6488.0},"hrZoneAnaerobic":{"unit":"seconds","value":2934.0},
         "hrZoneExtreme":{"unit":"seconds","value":121.0},"internal_hasGps":{"unit":"string","value":"false"}}
    """.trimIndent()

    // OpenTracks quirk: an empty <coord/> follows the first <when>; two tracks = one pause.
    private val kml = """
        <kml><Document><Placemark><MultiTrack>
        <Track>
          <when>2026-06-21T17:57:21.671+02:00</when><coord/>
          <when>2026-06-21T17:57:22+02:00</when><coord>6.8700 50.8800 100</coord>
          <when>2026-06-21T18:07:22+02:00</when><coord>6.8700 50.8900 110</coord>
        </Track>
        <Track>
          <when>2026-06-21T18:30:00+02:00</when><coord>6.8700 50.9000 120</coord>
          <when>2026-06-21T18:40:00+02:00</when><coord>6.8700 50.9100 121</coord>
        </Track>
        </MultiTrack></Placemark></Document></kml>
    """.trimIndent()

    @Test fun parsesGadgetbridgeJsonAndFilenameDate() {
        val s = WorkoutParser.parse(cyclingJson, "2026-06-21T17_57_19+02_00.json").getOrThrow()
        assertEquals(9663, s.activeSeconds)
        assertEquals(1310, s.calories)
        assertEquals(185, s.maxHR)
        assertEquals(5, s.hrZoneSeconds.size)
        assertEquals(1782057439000L, s.startMillis) // 2026-06-21T15:57:19Z
        assertEquals(Sport.WORKOUT, mergeWorkout(s, null, null).sport) // nothing to tell yet
    }

    @Test fun rejectsUnrelatedJson() {
        assertTrue(WorkoutParser.parse("""{"foo":1}""").isFailure)
        val broken = WorkoutParser.parse("""{"activeSeconds": """).exceptionOrNull()!!.message!!
        assertTrue(broken, broken.startsWith("Die JSON-Datei ist unvollständig"))
        assertTrue(WorkoutParser.parse("[1,2]").isFailure)
        assertNull(WorkoutParser.parseFilenameDate("export.json"))
    }

    @Test fun kmlPairsWhenAndCoordAndSkipsPauses() {
        val r = RouteParser.parse(kml.toByteArray())!!
        assertEquals(2, r.segments.size)
        assertEquals(4, r.pointCount)
        // two 0.01° latitude steps ≈ 2 × 1112 m; the pause gap (0.01°) must not count
        assertEquals(2224.0, r.distanceMeters, 5.0)
        assertEquals(20 * 60, r.movingSeconds)
        assertEquals(10.0, r.elevationGainMeters, 0.01) // 100→110 counts, 120→121 is noise
    }

    @Test fun elevationProfileFollowsDistance() {
        val p = RouteParser.parse(kml.toByteArray())!!.profile
        assertEquals(120, p.size)
        assertEquals(100.0, p.first(), 0.01)
        assertEquals(121.0, p.last(), 0.01)
        assertTrue(p.all { it in 100.0..121.0 })
    }

    @Test fun pickStatsKeepsTapOrderAndSkipsMissing() {
        val w = mergeWorkout(WorkoutStats(activeSeconds = 3600, distanceMeters = 30000.0, averageHR = 140), null, Sport.CYCLING)
        assertEquals(listOf(StatKey.DISTANCE, StatKey.TIME, StatKey.SPEED), w.pickStats(null, 3).map { it.key })
        assertEquals(listOf(StatKey.AVG_HR, StatKey.DISTANCE), w.pickStats(listOf(StatKey.AVG_HR, StatKey.LAPS, StatKey.DISTANCE), 3).map { it.key })
        assertEquals(listOf(StatKey.DISTANCE, StatKey.TIME), w.pickStats(listOf(StatKey.LAPS), 2).map { it.key }) // nothing usable → default
    }

    @Test fun statsPickedWithoutDistanceMakeWayForTheRoute() {
        val watch = WorkoutStats(activeSeconds = 3600, averageHR = 140, maxHR = 170)
        val before = mergeWorkout(watch, null, null) // watch file alone: no distance
        val after = mergeWorkout(watch.copy(distanceMeters = 30000.0), null, null)
        val hrOnly = listOf(StatKey.AVG_HR, StatKey.MAX_HR)
        assertTrue(pickMissesDistance(hrOnly, before, after))
        assertTrue(pickMissesDistance(hrOnly, null, after)) // both files at once, HR picked on an earlier watch-only workout
        assertFalse(pickMissesDistance(null, before, after)) // Auto shows distance anyway
        assertFalse(pickMissesDistance(listOf(StatKey.AVG_HR, StatKey.PACE), before, after)) // a look with pace stays
        assertFalse(pickMissesDistance(hrOnly, after, after)) // HR only on a ride with distance: on purpose
    }

    @Test fun englishLabelsAndNumbers() {
        Locale.setDefault(Locale.ENGLISH)
        val w = mergeWorkout(WorkoutStats(activeSeconds = 3600, distanceMeters = 30500.0, calories = 1310), null, Sport.CYCLING)
        val c = w.storyContent()
        assertEquals("30.50", c.hero.value)
        assertEquals("Avg speed", c.cells[1].label)
        assertEquals("1,310", c.cells.first { it.key == StatKey.ENERGY }.value)
        assertEquals("Cycling", Sport.CYCLING.label)
    }

    @Test fun kmzIsUnzipped() {
        val buf = ByteArrayOutputStream()
        ZipOutputStream(buf).use { it.putNextEntry(ZipEntry("doc.kml")); it.write(kml.toByteArray()) }
        assertTrue(RouteParser.isZip(buf.toByteArray()))
        assertEquals(4, RouteParser.parse(buf.toByteArray())!!.pointCount)
    }

    @Test fun gpxWithAttributesInAnyOrder() {
        val gpx = """<gpx><trk><trkseg>
            <trkpt lon="7.0" lat="51.0"><ele>50</ele><time>2026-01-01T10:00:00Z</time></trkpt>
            <trkpt lat='51.001' lon='7.0'><time>2026-01-01T10:01:00Z</time></trkpt>
            <trkpt lat="51.002" lon="7.0"/>
        </trkseg></trk></gpx>"""
        val r = RouteParser.parse(gpx.toByteArray())!!
        assertEquals(3, r.pointCount)
        assertEquals(222.4, r.distanceMeters, 1.0)
        assertEquals(60, r.movingSeconds)
    }

    @Test fun routeFillsMissingDistanceAndPicksCycling() {
        val s = WorkoutParser.parse(cyclingJson, "x.json").getOrThrow()
        val route = Route(listOf(listOf(LatLon(0.0, 0.0), LatLon(0.0, 0.1))), 67_320.0, 410.0, null, 9000)
        val w = mergeWorkout(s, route, null)
        assertEquals(Sport.CYCLING, w.sport) // 67 km in 2:41 h ≈ 25 km/h
        assertEquals(9000, w.activeSeconds) // Mi Band counts every stop; the track's moving time wins
        val c = w.storyContent()
        assertEquals(Stat(StatKey.DISTANCE, "67,32", "KM"), c.hero)
        assertEquals(listOf("Zeit", "Ø Tempo", "Anstieg", "Ø Puls", "Max Puls", "Energie"), c.cells.map { it.label })
        assertEquals("2:30:00", c.cells[0].value)
        assertEquals("26,9", c.cells[1].value)
        assertEquals("1.310", c.cells[5].value)
        assertEquals(Sport.RUNNING, mergeWorkout(s, route, Sport.RUNNING).sport) // manual choice wins
        // the bike offers min/km as well, last so Auto keeps km/h: 67.32 km in 2:30:00 = 2:14 /km
        assertEquals(Stat(StatKey.PACE, "2:14", "/KM"), w.availableStats().last())
    }

    // Points along a meridian; each step is (metres, seconds) after the previous point.
    private fun track(steps: List<Pair<Double, Int>>): List<RouteParser.Pt> {
        var m = 0.0; var t = 0L
        return listOf(RouteParser.Pt(48.3, 9.9, null, 0L)) + steps.map { (dm, dt) ->
            m += dm; t += dt * 1000L
            RouteParser.Pt(48.3 + m / 111_195.0, 9.9, null, t)
        }
    }

    @Test fun standingStillIsNotMovingTime() {
        // ride, roll slowly up to a red light, three minutes of GPS drift, ride on
        val ride = List(60) { 14.0 to 2 } + (8.0 to 10) + List(3) { 1.0 to 60 } + List(60) { 14.0 to 2 }
        val r = RouteParser.build(listOf(track(ride)))!!
        assertEquals(240, r.movingSeconds)
        assertEquals(1, r.splitSeconds.size)
        assertTrue(r.splitSeconds.toString(), r.splitSeconds[0] in 140..142) // not 331 s with the stop
        // on foot a steep climb at 0.8 m/s still counts
        assertEquals(1000, RouteParser.build(listOf(track(List(100) { 8.0 to 10 })))!!.movingSeconds)
    }

    @Test fun gpsAltitudeJitterIsNotClimbing() {
        // 10 km flat with ±4 m GPS jitter, then a real 100 m climb over 2 km
        val flat = List(1000) { i -> RouteParser.Pt(48.3 + i * 10 / 111_195.0, 9.9, 100.0 + if (i % 2 == 0) 4.0 else -4.0, null) }
        val climb = List(200) { i -> RouteParser.Pt(48.3 + (10_000 + i * 10) / 111_195.0, 9.9, 100.0 + i * 0.5, null) }
        val r = RouteParser.build(listOf(flat + climb))!!
        assertTrue("${r.elevationGainMeters}", r.elevationGainMeters in 85.0..105.0) // raw with a 3 m step: over 4000 hm
        // the graph over the flat part is flat too, not a ±4 m zigzag
        assertTrue(r.profile.take(80).toString(), r.profile.take(80).all { it in 99.0..101.0 })
        // the top still shows; at the very end only the last 200 m are averaged, so it reads a few metres low
        assertTrue("${r.profile.last()}", r.profile.last() in 190.0..200.0)
    }

    @Test fun movingTimeReplacesTimeOnlyIfTheWatchNeverPaused() {
        val route = Route(listOf(listOf(LatLon(0.0, 0.0), LatLon(0.0, 0.1))), 64_400.0, 0.0, null, 9030)
        val mi = WorkoutStats(activeSeconds = 9663, stopsIncluded = true)
        assertEquals(9030, mergeWorkout(mi, route, null).activeSeconds)
        assertEquals(9663, mergeWorkout(mi.copy(stopsIncluded = false), route, null).activeSeconds) // auto-pause watch knows best
        assertEquals(9663, mergeWorkout(mi, route.copy(movingSeconds = 4000), null).activeSeconds) // track covers too little
    }

    @Test fun sameStartPairsFilesOfOneWorkout() {
        val route = Route(emptyList(), 0.0, 0.0, 1_000_000_000L, 0)
        assertEquals(true, sameStart(WorkoutStats(startMillis = 1_000_000_000L - 7_000), route)) // watch started 7 s earlier
        assertEquals(false, sameStart(WorkoutStats(startMillis = 1_000_000_000L + 86_400_000), route)) // a day apart
        assertNull(sameStart(WorkoutStats(), route))
    }

    @Test fun swimmingShowsPacePer100AndLaps() {
        val s = WorkoutStats(activeSeconds = 1800, distanceMeters = 1000.0, laps = 40, swolf = 38, swimStyle = "freestyle")
        val w = mergeWorkout(s, null, null)
        assertEquals(Sport.SWIMMING, w.sport)
        val c = w.storyContent()
        assertEquals(Stat(StatKey.DISTANCE, "1,00", "KM"), c.hero)
        assertEquals(Stat(StatKey.PACE, "3:00", "/100M"), c.cells[1])
        assertEquals("Bahnen", c.cells[2].label)
    }

    @Test fun noDistanceMakesTimeTheHero() {
        val c = mergeWorkout(WorkoutStats(activeSeconds = 2892, averageHR = 120), null, null).storyContent()
        assertEquals(Stat(StatKey.TIME, "48:12"), c.hero)
        assertTrue(c.cells.none { it.label == "Zeit" })
    }

    /** Runs against Jonas' real rides when FITSHARE_REAL_DIR is set; never part of the public fixtures. */
    @Test fun realExportsIfAvailable() {
        val dir = System.getenv("FITSHARE_REAL_DIR")?.let(::File)
        assumeTrue(dir?.isDirectory == true)
        dir!!.walk().filter { it.extension == "json" }.forEach { f ->
            val s = WorkoutParser.parse(f.readText(), f.name).getOrThrow()
            assertNotNull(f.name, s.startMillis)
            assertTrue(f.name, s.activeSeconds > 0)
        }
        dir.walk().filter { it.extension == "kmz" }.forEach { f ->
            val r = RouteParser.parse(f.readBytes())
            assertNotNull(f.name, r)
            assertTrue(f.name, r!!.splitSeconds.size >= (r.distanceMeters / 1000).toInt() - 1)
            println("${f.parentFile.name}: ${r.splitSeconds.size} splits, ${"%.2f".format(r.distanceMeters / 1000)} km, ${r.elevationGainMeters.toInt()} hm, ${r.pointCount} pts, ${r.movingSeconds / 60} min")
        }
    }
}
