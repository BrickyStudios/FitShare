package com.fitshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ParsingTest {

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
        assertEquals(9663, w.activeSeconds) // JSON wins over GPS time
        val c = w.storyContent()
        assertEquals(Stat("Distanz", "67,32", "KM"), c.hero)
        assertEquals(listOf("Zeit", "Ø Tempo", "Anstieg", "Ø Puls", "Max Puls", "Energie"), c.cells.map { it.label })
        assertEquals("2:41:03", c.cells[0].value)
        assertEquals("25,1", c.cells[1].value)
        assertEquals("1.310", c.cells[5].value)
        assertEquals(Sport.RUNNING, mergeWorkout(s, route, Sport.RUNNING).sport) // manual choice wins
    }

    @Test fun swimmingShowsPacePer100AndLaps() {
        val s = WorkoutStats(activeSeconds = 1800, distanceMeters = 1000.0, laps = 40, swolf = 38, swimStyle = "freestyle")
        val w = mergeWorkout(s, null, null)
        assertEquals(Sport.SWIMMING, w.sport)
        val c = w.storyContent()
        assertEquals(Stat("Distanz", "1,00", "KM"), c.hero)
        assertEquals(Stat("Pace", "3:00", "/100M"), c.cells[1])
        assertEquals("Bahnen", c.cells[2].label)
    }

    @Test fun noDistanceMakesTimeTheHero() {
        val c = mergeWorkout(WorkoutStats(activeSeconds = 2892, averageHR = 120), null, null).storyContent()
        assertEquals(Stat("Zeit", "48:12"), c.hero)
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
            println("${f.parentFile.name}: ${"%.2f".format(r!!.distanceMeters / 1000)} km, ${r.elevationGainMeters.toInt()} hm, ${r.pointCount} pts, ${r.movingSeconds / 60} min")
        }
    }
}
