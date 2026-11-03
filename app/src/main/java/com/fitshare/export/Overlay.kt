package com.fitshare.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.fitshare.data.Route
import com.fitshare.data.Stat
import com.fitshare.data.StatKey
import com.fitshare.data.Workout
import com.fitshare.data.formatShortDate
import com.fitshare.data.hrZoneName
import com.fitshare.data.pickStats
import com.fitshare.data.availableStats
import com.fitshare.data.tr
import com.fitshare.export.StoryRenderer.INK
import com.fitshare.export.StoryRenderer.W
import com.fitshare.export.StoryRenderer.alpha
import com.fitshare.export.StoryRenderer.fitWidth
import com.fitshare.ui.theme.Fonts
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

enum class OverlayLayout(private val de: String, private val en: String, val maxStats: Int) {
    STACK("Stapel", "Stack", 3),
    ROW("Reihe", "Row", 3),
    CARD("Karte", "Card", 4),
    ROUTE("Route", "Map", 3);

    val label: String get() = tr(de, en)
}

enum class InkColor(private val de: String, private val en: String) {
    WHITE("Weiß", "White"),
    BLACK("Schwarz", "Black"),
    SPORT("Sportfarbe", "Sport colour");

    val label: String get() = tr(de, en)
}

/** Everything the user can tune on the transparent overlay. Saved between sessions. */
data class OverlayOptions(
    val layout: OverlayLayout = OverlayLayout.STACK,
    val ink: InkColor = InkColor.WHITE,
    /** Chosen stats in tap order; null = the sport's default. */
    val stats: List<StatKey>? = null,
    val showRoute: Boolean = true,
    val showProfile: Boolean = false,
    val showZones: Boolean = false,
    val showHeart: Boolean = false,
    val showSplits: Boolean = false,
    val showDate: Boolean = false,
    val showBrand: Boolean = true,
    /** Index into [DIM_LEVELS]: darkens behind the numbers (lightens with black ink). */
    val dim: Int = 0,
    /** Bake the picked photo into a full 9:16 story instead of exporting a transparent sticker. */
    val withPhoto: Boolean = false,
    // photo-in-PNG only. Index 0 is always the original look, because Gson fills missing fields with 0.
    /** Index into [PLACES]. */
    val place: Int = 0,
    /** Index into [SIZES]. */
    val size: Int = 0,
    /** Darken as a gradient around the numbers instead of evenly. */
    val fade: Boolean = false,
    /** Blur the photo so the numbers stand out. */
    val soft: Boolean = false
)

/** Off, light, medium, strong. */
val DIM_LEVELS = floatArrayOf(0f, 0.25f, 0.45f, 0.65f)

fun dimLabel(i: Int) = listOf(tr("Aus", "Off"), tr("Leicht", "Light"), tr("Mittel", "Medium"), tr("Stark", "Strong"))[i]

/** Middle, top, bottom; shown top → middle → bottom via [PLACE_ORDER]. */
val PLACE_ORDER = intArrayOf(1, 0, 2)
fun placeLabel(i: Int) = listOf(tr("Mitte", "Middle"), tr("Oben", "Top"), tr("Unten", "Bottom"))[i]

/** Width share of the story: L, M, S; shown S → M → L via [SIZE_ORDER]. */
val SIZES = floatArrayOf(1f, 0.8f, 0.6f)
val SIZE_ORDER = intArrayOf(2, 1, 0)
fun sizeLabel(i: Int) = listOf("L", "M", "S")[i]

/** Top edge of the sticker on the photo for [PLACES] index [place]. */
fun stickerTop(place: Int, stickerH: Float, h: Float): Float = when (place) {
    1 -> h * 0.08f
    2 -> h * 0.92f - stickerH
    else -> (h - stickerH) / 2
}.coerceIn(0f, (h - stickerH).coerceAtLeast(0f))

/**
 * Shade for "gradient": comes in from the story edge nearest the sticker (bottom for middle and bottom,
 * top for top), stays full up to the sticker and eases out a bit past its far side, so it reads as light, not a band.
 * Returns positions (fractions of [h], ascending) and the share of the darken level at each.
 */
fun fadeStops(place: Int, y0: Float, y1: Float, h: Float): Pair<FloatArray, FloatArray> {
    val tail = h * 0.14f
    val fromTop = place == 1
    val far = if (fromTop) y1 else y0
    val reach = if (fromTop) (y1 + tail).coerceAtMost(h) else (y0 - tail).coerceAtLeast(0f)
    val mid = (y0 + y1) / 2
    val tailMid = (far + reach) / 2
    val pos = if (fromTop) floatArrayOf(0f, y0, mid, y1, tailMid, reach) else floatArrayOf(reach, tailMid, y0, mid, y1, h)
    val share = if (fromTop) floatArrayOf(1f, 1f, 0.8f, 0.45f, 0.15f, 0f) else floatArrayOf(0f, 0.15f, 0.45f, 0.8f, 1f, 1f)
    var last = 0f
    return Pair(pos.map { v -> (v / h).coerceIn(last, 1f).also { last = it } }.toFloatArray(), share)
}

/**
 * Transparent PNG cropped to its content, meant to be added as a photo sticker on top of
 * your own Instagram story. Layout is built as a stack of blocks so every option combination fits.
 */
object OverlayRenderer {

    private class Block(val height: Float, val draw: (Canvas, Float) -> Unit)

    private const val GAP = 36f
    private const val PAD = 44f
    private const val SCRIM_PAD = 120f // the soft shade needs room to fade out before the PNG's edge

    fun render(context: Context, workout: Workout, o: OverlayOptions, scale: Float = 1f): Bitmap {
        val accent = workout.sport.accent
        val ink = when (o.ink) { InkColor.WHITE -> Color.WHITE; InkColor.BLACK -> INK; InkColor.SPORT -> accent }
        val shade = if (o.ink == InkColor.BLACK) 0x55FFFFFF else 0x66000000
        val stats = workout.pickStats(o.stats, o.layout.maxStats)
        val route = workout.route?.takeIf { o.showRoute }
        val profile = workout.route?.profile.orEmpty().takeIf { o.showProfile && it.size > 1 }
        val zones = workout.stats.hrZoneSeconds.takeIf { o.showZones && it.sum() > 0 }
        val heart = workout.stats.hrCurve.takeIf { o.showHeart && it.size > 1 }
        val splits = workout.route?.splitSeconds.orEmpty().takeIf { o.showSplits && it.size >= 2 }
        val shadow = o.layout != OverlayLayout.CARD // the card itself gives contrast

        fun paint(tf: Typeface, size: Float, color: Int = ink, tracking: Float = 0f, align: Paint.Align = Paint.Align.CENTER) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf; textSize = size; this.color = color; letterSpacing = tracking; textAlign = align
                if (shadow) setShadowLayer(10f, 0f, 2f, shade)
            }
        val text = { w: Int -> Fonts.text(context, w) }
        val display = { w: Int -> Fonts.display(context, w) }
        val cx = W / 2
        val card = o.layout == OverlayLayout.CARD
        val cw = if (card) 848f else 920f
        val left = cx - cw / 2

        val blocks = mutableListOf<Block>()

        fun dateLine(): Block? {
            val millis = workout.startMillis ?: return null
            val p = paint(text(700), 30f, alpha(ink, 0.85f), 0.14f)
            val s = "${workout.sport.label.uppercase(Locale.getDefault())} · ${formatShortDate(millis)}"
            return Block(44f) { c, y -> c.drawText(s, cx, y + 32f, p) }
        }

        fun routeBlock(maxW: Float, maxH: Float): Block? {
            val r = route ?: return null
            val (rw, rh) = fitRouteBox(r, maxW, maxH) ?: return null
            val pal = StoryRenderer.Palette(ink, ink, ink, ink, shadow)
            return Block(rh) { c, y -> StoryRenderer.drawRoute(c, r, RectF(cx - rw / 2, y, cx + rw / 2, y + rh), StoryStyle.OVERLAY, pal, ink) }
        }

        // filled line chart under a label row; shared by elevation and heart rate
        fun curveBlock(title: String, rightText: String?, pts: List<Double>, minSpan: Double): Block {
            val label = paint(text(700), 26f, alpha(ink, 0.85f), 0.14f, Paint.Align.LEFT)
            val right = paint(text(700), 26f, ink, 0.08f, Paint.Align.RIGHT)
            val h = 150f
            return Block(44f + h) { c, y ->
                c.drawText(title, left, y + 26f, label)
                rightText?.let { c.drawText(it, left + cw, y + 26f, right) }
                val top = y + 44f
                val min = pts.min(); val span = (pts.max() - min).coerceAtLeast(minSpan)
                val path = Path()
                pts.forEachIndexed { i, e ->
                    val x = left + cw * i / (pts.size - 1)
                    val py = (top + h - (e - min) / span * (h - 8f)).toFloat()
                    if (i == 0) path.moveTo(x, py) else path.lineTo(x, py)
                }
                val fill = Path(path).apply { lineTo(left + cw, top + h); lineTo(left, top + h); close() }
                c.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, top, 0f, top + h, alpha(ink, 0.45f), alpha(ink, 0.04f), Shader.TileMode.CLAMP)
                })
                c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; strokeWidth = 5f; color = ink; strokeJoin = Paint.Join.ROUND
                    if (shadow) setShadowLayer(8f, 0f, 2f, shade)
                })
            }
        }

        fun profileBlock(): Block? {
            val pts = profile ?: return null
            val gain = workout.availableStats().firstOrNull { it.key == StatKey.ELEVATION }
            return curveBlock(tr("HÖHENPROFIL", "ELEVATION"), gain?.let { "+${it.value} ${it.unit}" }, pts, 10.0)
        }

        fun heartBlock(): Block? {
            val pts = heart ?: return null
            val s = workout.stats
            val right = if (s.averageHR > 0) "Ø ${s.averageHR}" + (if (s.maxHR > 0) " · MAX ${s.maxHR}" else "") + " BPM" else null
            return curveBlock(tr("PULS", "HEART RATE"), right, pts, 20.0)
        }

        // one bar per km, taller = faster; the fastest km in full ink
        fun splitsBlock(): Block? {
            val sp = splits ?: return null
            val label = paint(text(700), 26f, alpha(ink, 0.85f), 0.14f, Paint.Align.LEFT)
            val right = paint(text(700), 26f, ink, 0.08f, Paint.Align.RIGHT)
            val best = sp.indices.minBy { sp[it] }
            val h = 120f
            return Block(44f + h) { c, y ->
                c.drawText(tr("KM-SPLITS", "KM SPLITS"), left, y + 26f, label)
                c.drawText(tr("SCHNELLSTER KM ", "FASTEST KM ") + "${best + 1} · ${formatSplit(sp[best])}", left + cw, y + 26f, right)
                val slowest = sp.max().toFloat(); val fastest = sp.min().toFloat()
                val gap = if (sp.size > 40) 2f else 6f
                val bw = (cw - gap * (sp.size - 1)) / sp.size
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                if (shadow) p.setShadowLayer(6f, 0f, 2f, shade)
                sp.forEachIndexed { i, t ->
                    // speed relative to the range, never shorter than a quarter so slow kms stay visible
                    val k = if (slowest > fastest) (slowest - t) / (slowest - fastest) else 1f
                    val bh = h * (0.25f + 0.75f * k)
                    p.color = if (i == best) ink else alpha(ink, 0.5f)
                    val x = left + i * (bw + gap)
                    c.drawRoundRect(x, y + 44f + h - bh, x + bw, y + 44f + h, minOf(6f, bw / 2), minOf(6f, bw / 2), p)
                }
            }
        }

        fun zonesBlock(): Block? {
            val z = zones ?: return null
            val total = z.sum()
            val top = z.indices.maxBy { z[it] }
            val label = paint(text(700), 26f, alpha(ink, 0.85f), 0.14f, Paint.Align.LEFT)
            val right = paint(text(700), 26f, ink, 0.08f, Paint.Align.RIGHT)
            return Block(44f + 20f) { c, y ->
                c.drawText(tr("PULSZONEN", "HR ZONES"), left, y + 26f, label)
                c.drawText("${hrZoneName(top).uppercase(Locale.getDefault())} ${(z[top] / total * 100).roundToInt()} %", left + cw, y + 26f, right)
                val gap = 8f
                val usable = cw - gap * (z.count { it > 0 } - 1)
                var x = left
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                z.forEachIndexed { i, s ->
                    if (s <= 0) return@forEachIndexed
                    val w = max(10f, (s / total * usable).toFloat())
                    p.color = StoryRenderer.ZONE_COLORS[i]
                    c.drawRoundRect(x, y + 44f, x + w, y + 64f, 10f, 10f, p)
                    x += w + gap
                }
            }
        }

        fun brandBlock(): Block? {
            if (!o.showBrand) return null
            val p = paint(text(800), 28f, alpha(ink, 0.85f), 0.34f)
            return Block(36f) { c, y -> c.drawText("FITSHARE", cx + 5f, y + 30f, p) } // +5: tracking trails the last glyph
        }

        // value + smaller unit, drawn as one group around x (centre) or from x (left)
        fun drawValue(c: Canvas, st: Stat, x: Float, base: Float, value: Paint, unit: Paint, maxW: Float, centered: Boolean) {
            val gap = if (st.unit.isEmpty()) 0f else 14f
            val size = value.textSize; val uSize = unit.textSize
            while (value.measureText(st.value) + gap + unit.measureText(st.unit) > maxW && value.textSize > 30f) {
                value.textSize -= 4f; unit.textSize = uSize * value.textSize / size
            }
            val vw = value.measureText(st.value); val total = vw + gap + unit.measureText(st.unit)
            val start = if (centered) x - total / 2 else x
            val va = value.textAlign; val ua = unit.textAlign
            value.textAlign = Paint.Align.LEFT; unit.textAlign = Paint.Align.LEFT
            c.drawText(st.value, start, base, value)
            if (st.unit.isNotEmpty()) c.drawText(st.unit, start + vw + gap, base, unit)
            value.textAlign = va; unit.textAlign = ua
            value.textSize = size; unit.textSize = uSize
        }

        when (o.layout) {
            OverlayLayout.STACK -> {
                if (o.showDate) dateLine()?.let(blocks::add)
                val label = paint(text(700), 34f, alpha(ink, 0.85f), 0.16f)
                for (st in stats) {
                    blocks += Block(62f + 150f * 0.74f + 24f) { c, y ->
                        c.drawText(st.label.uppercase(Locale.getDefault()), cx, y + 34f, label)
                        drawValue(c, st, cx, y + 62f + 150f * 0.74f, paint(display(800), 150f, tracking = -0.01f),
                            paint(display(700), 70f, tracking = 0.02f), cw, true)
                    }
                }
                routeBlock(760f, 560f)?.let(blocks::add)
            }
            OverlayLayout.ROW -> {
                if (o.showDate) dateLine()?.let(blocks::add)
                routeBlock(760f, 480f)?.let(blocks::add)
                val n = stats.size.coerceAtLeast(1)
                val colW = cw / n
                val label = paint(text(700), 26f, alpha(ink, 0.85f), 0.14f)
                val line = Paint().apply { color = alpha(ink, 0.4f); strokeWidth = 3f }
                blocks += Block(36f + 110f * 0.74f + 10f) { c, y ->
                    stats.forEachIndexed { i, st ->
                        val mid = left + colW * i + colW / 2
                        if (i > 0) c.drawLine(left + colW * i, y + 8f, left + colW * i, y + 36f + 110f * 0.74f, line)
                        c.drawText(st.label.uppercase(Locale.getDefault()), mid, y + 26f, label)
                        drawValue(c, st, mid, y + 36f + 110f * 0.74f, paint(display(800), 100f),
                            paint(display(700), 40f), colW - 36f, true)
                    }
                }
            }
            OverlayLayout.ROUTE -> {
                routeBlock(920f, 900f)?.let(blocks::add)
                val line = stats.joinToString("   ·   ") { "${it.value} ${it.unit}".trim() }
                val p = paint(display(800), 64f, tracking = 0.02f)
                fitWidth(p, line, cw)
                blocks += Block(p.textSize * 0.8f) { c, y -> c.drawText(line, cx, y + p.textSize * 0.76f, p) }
                if (o.showDate) dateLine()?.let(blocks::add)
            }
            OverlayLayout.CARD -> {
                // header: sport dot + name left, date right
                val name = paint(text(700), 34f, ink, 0.0f, Paint.Align.LEFT)
                val date = paint(text(500), 30f, alpha(ink, 0.7f), 0.0f, Paint.Align.RIGHT)
                blocks += Block(44f) { c, y ->
                    c.drawCircle(left + 12f, y + 22f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                    c.drawText(workout.sport.label, left + 38f, y + 34f, name)
                    if (o.showDate) workout.startMillis?.let { c.drawText(formatShortDate(it), left + cw, y + 34f, date) }
                }
                routeBlock(cw, 380f)?.let(blocks::add)
                val rows = (stats.size + 1) / 2
                val cellW = cw / 2
                val labelColor = if (o.ink == InkColor.BLACK) alpha(ink, 0.6f) else accent
                val label = paint(text(650), 28f, labelColor, 0.0f, Paint.Align.LEFT)
                val hair = Paint().apply { color = alpha(ink, 0.16f); strokeWidth = 2f }
                blocks += Block(rows * 150f) { c, y ->
                    stats.forEachIndexed { i, st ->
                        val x = left + (i % 2) * cellW
                        val top = y + (i / 2) * 150f
                        if (i % 2 == 0 && i > 0) c.drawLine(left, top, left + cw, top, hair)
                        c.drawText(st.label, x, top + 44f, label)
                        val value = paint(text(650), 84f, tracking = -0.01f, align = Paint.Align.LEFT).apply { fontFeatureSettings = "'tnum'" }
                        val unit = paint(text(600), 34f, alpha(ink, 0.65f), 0.02f, Paint.Align.LEFT)
                        drawValue(c, st.copy(unit = st.unit.lowercase(Locale.getDefault())), x, top + 126f, value, unit, cellW - 20f, false)
                    }
                }
            }
        }
        profileBlock()?.let(blocks::add)
        heartBlock()?.let(blocks::add)
        splitsBlock()?.let(blocks::add)
        zonesBlock()?.let(blocks::add)
        brandBlock()?.let(blocks::add)

        val inner = PAD + blocks.sumOf { it.height.toDouble() }.toFloat() + GAP * (blocks.size - 1).coerceAtLeast(0) + PAD
        val cardPad = 56f
        val margin = 60f // room for the card's shadow
        val contentH = if (card) inner + 2 * cardPad + 2 * margin - 2 * PAD else inner
        val scrim = DIM_LEVELS.getOrElse(o.dim) { 0f }
        val sPad = if (scrim > 0f) SCRIM_PAD else 0f
        val height = contentH + 2 * sPad

        val bmp = Bitmap.createBitmap((W * scale).roundToInt(), (height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        if (scrim > 0f) {
            // elliptical shade that reaches zero at the PNG's edges, so it never shows as a box in Instagram
            val base = if (o.ink == InkColor.BLACK) Color.WHITE else Color.BLACK
            val g = RadialGradient(0f, 0f, 1f, intArrayOf(alpha(base, scrim), alpha(base, scrim * 0.85f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            g.setLocalMatrix(Matrix().apply { setScale(W / 2, height / 2); postTranslate(W / 2, height / 2) })
            c.drawRect(0f, 0f, W, height, Paint().apply { shader = g })
            c.translate(0f, sPad)
        }
        var y = PAD
        if (card) {
            val rect = RectF(cx - cw / 2 - cardPad, margin, cx + cw / 2 + cardPad, contentH - margin)
            val glass = when (o.ink) { InkColor.BLACK -> alpha(Color.WHITE, 0.66f); else -> alpha(Color.BLACK, 0.42f) }
            c.drawRoundRect(rect, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glass; setShadowLayer(48f, 0f, 18f, 0x38000000)
            })
            c.drawRoundRect(RectF(rect.left + 1f, rect.top + 1f, rect.right - 1f, rect.bottom - 1f), 63f, 63f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = alpha(ink, 0.18f) })
            y = margin + cardPad
        }
        for (b in blocks) { b.draw(c, y); y += b.height + GAP }
        return bmp
    }

    private fun formatSplit(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    /** Size of the box the route needs inside maxW × maxH, keeping its real proportions. */
    private fun fitRouteBox(route: Route, maxW: Float, maxH: Float): Pair<Float, Float>? {
        val all = route.segments.flatten()
        if (all.size < 2) return null
        val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }
        val kx = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = (all.maxOf { it.lon } - all.minOf { it.lon }) * kx
        val spanY = maxLat - minLat
        if (spanX < 1e-7 && spanY < 1e-7) return null
        val s = minOf(maxW / spanX.coerceAtLeast(1e-9), maxH / spanY.coerceAtLeast(1e-9))
        return Pair(max(60f, (spanX * s).toFloat()), max(60f, (spanY * s).toFloat()))
    }
}
