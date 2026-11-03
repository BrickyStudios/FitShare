package com.fitshare.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoPlacementTest {
    @Test fun stopsStayOrderedAndInRange() {
        for ((y0, y1) in listOf(0f to 400f, 150f to 900f, 1200f to 1920f, 700f to 700f)) {
            val s = fadeStops(y0, y1, 1920f)
            assertEquals(4, s.size)
            assertTrue("$y0..$y1 → ${s.toList()}", s.toList().zipWithNext().all { (a, b) -> a <= b } && s.all { it in 0f..1f })
        }
    }

    @Test fun stickerStaysOnTheStory() {
        for (place in 0..2) for (h in listOf(300f, 1700f, 2500f)) {
            val top = stickerTop(place, h, 1920f)
            assertTrue("place $place h $h top $top", top >= 0f && (top + h <= 1920f || top == 0f))
        }
        assertEquals((1920f - 400f) / 2, stickerTop(0, 400f, 1920f))
    }
}
