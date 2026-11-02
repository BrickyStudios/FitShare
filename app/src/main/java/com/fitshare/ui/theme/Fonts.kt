package com.fitshare.ui.theme

import android.content.Context
import android.graphics.Typeface

/**
 * Bundled variable fonts (OFL, see assets/fonts). One cached Typeface per weight,
 * shared by the Canvas story renderer and the Compose UI so both look identical.
 */
object Fonts {
    private val cache = HashMap<String, Typeface>()

    private fun get(context: Context, file: String, weight: Int): Typeface = synchronized(cache) {
        cache.getOrPut("$file@$weight") {
            Typeface.Builder(context.assets, "fonts/$file")
                .setFontVariationSettings("'wght' $weight")
                .build()
        }
    }

    /** Big Shoulders Display: condensed stadium-signage numerals. */
    fun display(context: Context, weight: Int = 800) = get(context, "bigshoulders.ttf", weight)

    /** Archivo: sturdy grotesk for labels and body copy. */
    fun text(context: Context, weight: Int = 500) = get(context, "archivo.ttf", weight)
}
