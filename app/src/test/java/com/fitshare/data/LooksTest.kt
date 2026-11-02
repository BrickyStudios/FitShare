package com.fitshare.data

import com.fitshare.export.InkColor
import com.fitshare.export.OverlayLayout
import com.fitshare.export.OverlayOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class LooksTest {
    private val a = OverlayOptions(layout = OverlayLayout.CARD)
    private val b = OverlayOptions(ink = InkColor.SPORT)

    @Test fun historyDedupesAndCaps() {
        var h = emptyList<Look>()
        h = h.pushed(Look(options = a, savedAt = 1))
        h = h.pushed(Look(options = b, savedAt = 2))
        h = h.pushed(Look(options = a, savedAt = 3))
        assertEquals(listOf(3L, 2L), h.map { it.savedAt })
        repeat(20) { h = h.pushed(Look(options = OverlayOptions(stats = List(it) { StatKey.entries[0] }), savedAt = 10L + it)) }
        assertEquals(HISTORY_SIZE, h.size)
    }

    @Test fun presetNameReplaces() {
        val p = listOf(Look("Gravel", a)).withPreset(Look("gravel", b)).withPreset(Look("Lauf", a))
        assertEquals(listOf("gravel", "Lauf"), p.map { it.name })
        assertEquals(b, p[0].options)
    }
}
