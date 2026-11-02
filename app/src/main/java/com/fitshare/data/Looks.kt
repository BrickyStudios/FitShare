package com.fitshare.data

import com.fitshare.export.OverlayOptions

/** A saved overlay setup: a named preset, or (name = null) an entry in the history of past exports. */
data class Look(val name: String? = null, val options: OverlayOptions = OverlayOptions(), val savedAt: Long = 0)

data class Looks(val presets: List<Look> = emptyList(), val history: List<Look> = emptyList())

const val HISTORY_SIZE = 8

/** Newest first; the same setup used again moves to the front instead of appearing twice. */
fun List<Look>.pushed(look: Look, max: Int = HISTORY_SIZE): List<Look> =
    (listOf(look) + filter { it.options != look.options }).take(max)

/** Saving under an existing name (any case) replaces that preset. */
fun List<Look>.withPreset(look: Look): List<Look> =
    filter { !it.name.equals(look.name, ignoreCase = true) } + look
