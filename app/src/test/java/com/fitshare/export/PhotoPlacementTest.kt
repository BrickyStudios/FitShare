package com.fitshare.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoPlacementTest {
    @Test fun stopsStayOrderedAndInRange() {
        for (place in 0..2) for ((y0, y1) in listOf(0f to 400f, 150f to 900f, 1200f to 1920f, 700f to 700f)) {
            val (pos, share) = fadeStops(place, y0, y1, 1920f)
            assertEquals(6, pos.size); assertEquals(6, share.size)
            assertTrue("$place $y0..$y1 → ${pos.toList()}", pos.toList().zipWithNext().all { (a, b) -> a <= b } && pos.all { it in 0f..1f })
        }
    }

    @Test fun shadeComesFromTheNearestEdge() {
        assertEquals(1f, fadeStops(2, 1300f, 1760f, 1920f).second.last())  // bottom: darkest at the bottom edge
        assertEquals(1f, fadeStops(1, 150f, 600f, 1920f).second.first())   // top: darkest at the top edge
        assertEquals(0f, fadeStops(0, 700f, 1200f, 1920f).second.first())  // middle: clear sky above
    }

    @Test fun stickerStaysOnTheStory() {
        for (place in 0..2) for (h in listOf(300f, 1700f, 2500f)) {
            val top = stickerTop(place, h, 1920f)
            assertTrue("place $place h $h top $top", top >= 0f && (top + h <= 1920f || top == 0f))
        }
        assertEquals((1920f - 400f) / 2, stickerTop(0, 400f, 1920f))
    }
}
