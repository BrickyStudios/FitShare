package com.fitshare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FitParserTest {

    /** Minimal FIT writer: definition + data messages, little endian. */
    private class Fit {
        val body = ByteArrayOutputStream()
        fun def(local: Int, global: Int, vararg fields: Triple<Int, Int, Int>) {
            body.write(0x40 or local); body.write(0); body.write(0)
            body.write(global and 0xFF); body.write(global shr 8)
            body.write(fields.size)
            fields.forEach { (n, size, type) -> body.write(n); body.write(size); body.write(type) }
        }
        fun data(header: Int, vararg values: Pair<Long, Int>) {
            body.write(header)
            values.forEach { (v, size) -> val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array(); body.write(b, 0, size) }
        }
        fun bytes(): ByteArray {
            val d = body.toByteArray()
            val h = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
                .put(14).put(0x20).putShort(2100).putInt(d.size).put(".FIT".toByteArray()).putShort(0).array()
            return h + d + byteArrayOf(0, 0)
        }
    }

    private fun semi(deg: Double) = (deg / 180.0 * 2147483648.0).toLong()
    private val t0 = 1_150_000_000L // FIT seconds, 2026-06-09

    private fun ride(sport: Long) = Fit().apply {
        // record: timestamp, lat, lon, enhanced altitude
        def(0, 20, Triple(253, 4, 0x86), Triple(0, 4, 0x85), Triple(1, 4, 0x85), Triple(78, 4, 0x86))
        data(0, t0 to 4, semi(48.30) to 4, semi(9.90) to 4, ((100 + 500) * 5L) to 4)
        data(0, t0 + 10 to 4, semi(48.31) to 4, semi(9.90) to 4, ((110 + 500) * 5L) to 4)
        data(0, t0 + 600 to 4, semi(48.32) to 4, semi(9.90) to 4, 0xFFFFFFFFL to 4) // 10-min pause, invalid altitude
        // compressed timestamp header: local type 2, time = last + 5 s, payload without timestamp
        def(2, 20, Triple(0, 4, 0x85), Triple(1, 4, 0x85))
        data(0x80 or (2 shl 5) or ((t0 + 605) and 0x1F).toInt(), semi(48.33) to 4, semi(9.90) to 4)
        // session: start, sport, timer time, distance, calories, avg/max HR, ascent, laps, HR zones
        def(1, 18, Triple(2, 4, 0x86), Triple(5, 1, 0x00), Triple(8, 4, 0x86), Triple(9, 4, 0x86), Triple(11, 2, 0x84),
            Triple(16, 1, 0x02), Triple(17, 1, 0x02), Triple(22, 2, 0x84), Triple(26, 2, 0x84), Triple(65, 20, 0x86))
        data(1, t0 to 4, sport to 1, 3_600_000L to 4, 2_500_000L to 4, 900L to 2, 150L to 1, 181L to 1, 321L to 2, 1L to 2,
            0L to 4, 60_000L to 4, 3_000_000L to 4, 540_000L to 4, 0L to 4)
    }.bytes()

    @Test fun parsesSessionAndTrack() {
        val fit = FitParser.parse(ride(2))
        val s = fit.stats
        assertEquals(Sport.CYCLING, s.sport)
        assertEquals(3600, s.activeSeconds)
        assertEquals(25_000.0, s.distanceMeters, 0.01)
        assertEquals(900, s.calories)
        assertEquals(181, s.maxHR)
        assertEquals(0, s.laps) // one lap, but not a swim
        assertEquals(listOf(0.0, 60.0, 3000.0, 540.0, 0.0), s.hrZoneSeconds)
        assertEquals((t0 + 631_065_600L) * 1000, s.startMillis)

        val r = fit.route!!
        assertEquals(2, r.segments.size) // the 10-minute gap splits the track
        assertEquals(4, r.pointCount)
        assertEquals(2, r.segments[1].size) // compressed record stays in the second segment (5 s after the last)
        assertEquals(48.31, r.segments[0][1].lat, 1e-6)

        val w = mergeWorkout(s, fit.route, null)
        assertEquals(Sport.CYCLING, w.sport)
        assertEquals(321.0, w.elevationGainMeters, 0.01) // watch ascent beats GPS
    }

    @Test fun kmSplitsInterpolateAndSkipPauses() {
        // straight north: 0.009° lat ≈ 1000.8 m; 4 min per point, then a 30-min pause, then 5 min
        val step = 0.009
        val seg1 = List(3) { RouteParser.Pt(48.0 + it * step, 9.0, null, it * 240_000L) }
        val seg2 = listOf(RouteParser.Pt(48.0 + 2 * step, 9.0, null, 2_280_000L), RouteParser.Pt(48.0 + 3 * step, 9.0, null, 2_580_000L))
        val sp = RouteParser.splits(listOf(seg1, seg2))
        assertEquals(3, sp.size)
        assertTrue(sp.toString(), sp[0] in 239..240 && sp[1] in 239..240)
        assertTrue(sp.toString(), sp[2] in 299..301) // the pause between segments doesn't count
        assertTrue(RouteParser.splits(listOf(listOf(RouteParser.Pt(48.0, 9.0, null, null), RouteParser.Pt(48.1, 9.0, null, null)))).isEmpty())
    }

    @Test fun heartCurveResamples() {
        val hr = List(600) { it.toLong() to (100 + it / 10) }
        val c = FitParser.resample(hr, 60)
        assertEquals(60, c.size)
        assertTrue(c.first() < 102 && c.last() > 158)
        assertTrue(FitParser.resample(hr.take(5)).isEmpty())
    }

    @Test fun genericSportFallsBackToSpeed() {
        val s = FitParser.parse(ride(0)).stats
        assertNull(s.sport)
        assertEquals(Sport.CYCLING, mergeWorkout(s, null, null).sport) // 25 km/h
    }

    @Test fun detectsAndRejects() {
        assertTrue(FitParser.looksLikeFit(ride(2)))
        assertTrue(!FitParser.looksLikeFit("{\"a\":1}".toByteArray()))
        val noSession = Fit().apply { def(0, 20, Triple(253, 4, 0x86)); data(0, t0 to 4) }.bytes()
        assertTrue(runCatching { FitParser.parse(noSession) }.isFailure)
        val truncated = ride(2).copyOf(60) // cut inside the records: no session left
        assertTrue(runCatching { FitParser.parse(truncated) }.isFailure)
    }

    @Test fun realFitIfAvailable() {
        val dir = System.getenv("FITSHARE_REAL_DIR")?.let(::File)
        assumeTrue(dir?.isDirectory == true)
        dir!!.walk().filter { it.extension == "fit" }.forEach { f ->
            val s = FitParser.parse(f.readBytes()).stats
            assertNotNull(f.name, s.startMillis)
            assertTrue(f.name, s.activeSeconds > 0)
            assertTrue(f.name, s.hrCurve.isEmpty() || s.hrCurve.size == 120) // some exports carry only the summary, no records
            println("${f.parentFile.name}: ${s.activeSeconds / 60} min, ${s.averageHR}/${s.maxHR} bpm, ${s.calories} kcal, zones ${s.hrZoneSeconds.map { it.toInt() }}")
        }
    }
}
