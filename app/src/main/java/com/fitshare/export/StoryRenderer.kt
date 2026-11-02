package com.fitshare.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.core.graphics.ColorUtils
import com.fitshare.data.hrZoneName
import com.fitshare.data.tr
import com.fitshare.data.LatLon
import com.fitshare.data.Route
import com.fitshare.data.Workout
import com.fitshare.data.bibNumber
import com.fitshare.data.formatStoryDate
import com.fitshare.data.storyContent
import com.fitshare.ui.theme.Fonts
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

enum class StoryStyle(private val de: String, private val en: String) {
    OVERLAY("Overlay", "Overlay"),
    NIGHT("Nacht", "Night"),
    BIB("Startnummer", "Race bib");

    val label: String get() = tr(de, en)
}

/**
 * Draws the 1080×1920 story. The editor preview calls this too (at a smaller scale),
 * so what you see is exactly what gets shared.
 */
object StoryRenderer {
    const val W = 1080f
    const val H = 1920f
    private const val M = 84f // side margin

    internal const val INK = 0xFF14140F.toInt()
    private const val PAPER = 0xFFEFEBE1.toInt()
    private const val BONE = 0xFFF4F1EA.toInt()
    private const val NIGHT = 0xFF0C0D0B.toInt()

    internal val ZONE_COLORS = intArrayOf(
        0xFF5AA9FF.toInt(), 0xFF3DDC97.toInt(), 0xFFFFD23F.toInt(), 0xFFFF8A3D.toInt(), 0xFFFF3D5A.toInt()
    )

    internal class Palette(val fg: Int, val muted: Int, val line: Int, val route: Int, val shadow: Boolean)

    fun render(
        context: Context,
        workout: Workout,
        style: StoryStyle,
        background: Bitmap?,
        scale: Float = 1f,
        overlay: OverlayOptions = OverlayOptions()
    ): Bitmap {
        if (style == StoryStyle.OVERLAY) return OverlayRenderer.render(context, workout, overlay, scale)
        val bmp = Bitmap.createBitmap((W * scale).roundToInt(), (H * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        val accent = workout.sport.accent
        val pal = when (style) {
            StoryStyle.NIGHT -> Palette(BONE, alpha(BONE, 0.58f), alpha(BONE, 0.16f), accent, false)
            StoryStyle.BIB -> Palette(INK, alpha(INK, 0.6f), alpha(INK, 0.2f), INK, false)
            StoryStyle.OVERLAY -> error("drawn by renderOverlay")
        }
        drawBackground(c, style, accent, background)
        drawContent(c, context, workout, style, pal)
        return bmp
    }

    // ---------------------------------------------------------------- background

    private fun drawBackground(c: Canvas, style: StoryStyle, accent: Int, photo: Bitmap?) {
        when (style) {
            StoryStyle.NIGHT -> {
                if (photo != null) {
                    drawCropped(c, photo)
                    c.drawRect(0f, 0f, W, H, Paint().apply {
                        shader = LinearGradient(0f, 0f, 0f, H,
                            intArrayOf(alpha(NIGHT, 0.35f), alpha(NIGHT, 0.55f), alpha(NIGHT, 0.94f)),
                            floatArrayOf(0f, 0.35f, 0.7f), Shader.TileMode.CLAMP)
                    })
                } else {
                    c.drawColor(NIGHT)
                    c.drawRect(0f, 0f, W, H, Paint().apply {
                        shader = RadialGradient(W * 0.92f, H * 0.12f, 1150f,
                            alpha(accent, 0.34f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    })
                    c.drawRect(0f, 0f, W, H, Paint().apply {
                        shader = RadialGradient(0f, H * 0.95f, 900f,
                            alpha(accent, 0.12f), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    })
                }
                grain(c, Color.WHITE, 0.05f)
            }
            StoryStyle.BIB -> {
                c.drawColor(PAPER)
                if (photo != null) {
                    drawCropped(c, photo)
                    c.drawColor(alpha(PAPER, 0.82f))
                }
                grain(c, INK, 0.05f)
            }
            StoryStyle.OVERLAY -> Unit
        }
    }

    private fun grain(c: Canvas, color: Int, strength: Float) {
        val rnd = Random(7)
        val p = Paint().apply { this.color = alpha(color, strength); strokeWidth = 2.2f }
        val pts = FloatArray(18000) { if (it % 2 == 0) rnd.nextFloat() * W else rnd.nextFloat() * H }
        c.drawPoints(pts, p)
    }

    private fun drawCropped(c: Canvas, src: Bitmap) {
        val srcAspect = src.width.toFloat() / src.height
        val dstAspect = W / H
        val r = if (srcAspect > dstAspect) {
            val w = (src.height * dstAspect).toInt(); val x = (src.width - w) / 2
            Rect(x, 0, x + w, src.height)
        } else {
            val h = (src.width / dstAspect).toInt(); val y = (src.height - h) / 2
            Rect(0, y, src.width, y + h)
        }
        c.drawBitmap(src, r, RectF(0f, 0f, W, H), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // ---------------------------------------------------------------- content

    private fun drawContent(c: Canvas, ctx: Context, w: Workout, style: StoryStyle, pal: Palette) {
        val accent = w.sport.accent
        val content = w.storyContent()
        val display = { weight: Int -> Fonts.display(ctx, weight) }
        val text = { weight: Int -> Fonts.text(ctx, weight) }

        fun paint(tf: Typeface, size: Float, color: Int, tracking: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf; textSize = size; this.color = color; letterSpacing = tracking
                if (pal.shadow) setShadowLayer(14f, 0f, 3f, 0x99000000.toInt())
            }

        // --- header: wordmark + start time, sport title, date
        val headerBase = 150f
        val title = w.sport.label.uppercase(Locale.getDefault())
        val titlePaint = paint(display(900), 210f, if (style == StoryStyle.NIGHT) accent else pal.fg, -0.01f)
        fitWidth(titlePaint, title, W - 2 * M)
        val titleBase = headerBase + 40f + titlePaint.textSize * 0.78f
        val datePaint = paint(text(600), 32f, if (style == StoryStyle.BIB) alpha(INK, 0.72f) else pal.muted, 0.06f)
        val dateBase = titleBase + 66f
        val headerBottom = dateBase + 44f

        if (style == StoryStyle.BIB) {
            c.drawRect(0f, 0f, W, headerBottom + 26f, Paint().apply { color = accent })
            // perforated edge under the coloured band, like a tear-off start number
            val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
            var x = 18f
            while (x < W) { c.drawCircle(x, headerBottom + 26f, 7f, dots); x += 36f }
        }

        val brand = paint(text(800), 28f, pal.fg, 0.32f)
        c.drawText("FITSHARE", M, headerBase, brand)
        val bib = paint(display(800), 40f, pal.fg, 0.02f).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Nº ${bibNumber(w.startMillis)}", W - M, headerBase + 2f, bib)
        c.drawText(title, M - 6f, titleBase, titlePaint)
        w.startMillis?.let { c.drawText(formatStoryDate(it), M, dateBase, datePaint) }

        // --- footer + perforation (bottom-up layout from here)
        val footerBase = H - 96f
        val footer = paint(text(700), 24f, pal.muted, 0.24f)
        c.drawText("FITSHARE · GADGETBRIDGE", M, footerBase, footer)
        val perfY = footerBase - 66f
        c.drawLine(M, perfY, W - M, perfY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pal.line; strokeWidth = 3f; setStyle(Paint.Style.STROKE)
            pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        })

        // --- stat table: 3 columns, hairlines like a race ticket
        val cells = content.cells
        val rows = (cells.size + 2) / 3
        val rowH = 172f
        val gridBottom = perfY - 46f
        val gridTop = gridBottom - rows * rowH
        val linePaint = Paint().apply { color = pal.line; strokeWidth = 2f }
        val colW = (W - 2 * M) / 3
        val label = paint(text(700), 24f, pal.muted, 0.16f)
        cells.forEachIndexed { i, stat ->
            val row = i / 3; val col = i % 3
            val top = gridTop + row * rowH
            if (col == 0) c.drawLine(M, top, W - M, top, linePaint)
            if (col > 0) c.drawLine(M + col * colW, top + 26f, M + col * colW, top + rowH - 14f, linePaint)
            val x = M + col * colW + if (col > 0) 30f else 0f
            val maxW = colW - if (col > 0) 44f else 14f
            c.drawText(stat.label.uppercase(Locale.getDefault()), x, top + 56f, label)
            val value = paint(display(800), 96f, pal.fg)
            val unit = paint(text(700), 26f, pal.muted, 0.08f)
            val unitW = if (stat.unit.isEmpty()) 0f else unit.measureText(stat.unit) + 10f
            fitWidth(value, stat.value, maxW - unitW)
            c.drawText(stat.value, x, top + 150f, value)
            if (stat.unit.isNotEmpty()) c.drawText(stat.unit, x + value.measureText(stat.value) + 10f, top + 150f, unit)
        }

        // --- heart-rate zones
        var heroLimit = gridTop - 64f
        val zones = w.stats.hrZoneSeconds
        if (zones.sum() > 0) {
            val barTop = gridTop - 70f
            val labelBase = barTop - 26f
            val total = zones.sum()
            val top = zones.indices.maxBy { zones[it] }
            c.drawText(tr("PULSZONEN", "HEART RATE ZONES"), M, labelBase, label)
            val dominant = paint(text(700), 24f, pal.fg, 0.16f).apply { textAlign = Paint.Align.RIGHT }
            c.drawText("${hrZoneName(top).uppercase(Locale.getDefault())} ${(zones[top] / total * 100).roundToInt()} %", W - M, labelBase, dominant)
            var x = M
            val gap = 8f
            val usable = W - 2 * M - gap * (zones.count { it > 0 } - 1)
            val zp = Paint(Paint.ANTI_ALIAS_FLAG)
            zones.forEachIndexed { i, s ->
                if (s <= 0) return@forEachIndexed
                val segW = max(10f, (s / total * usable).toFloat())
                zp.color = ZONE_COLORS[i]
                c.drawRoundRect(x, barTop, x + segW, barTop + 22f, 11f, 11f, zp)
                x += segW + gap
            }
            heroLimit = labelBase - 72f
        }

        // --- hero number
        val hero = content.hero
        val hasRoute = w.route != null
        val heroPaint = paint(display(900), if (hasRoute) 330f else 460f, pal.fg, -0.02f)
        val unitPaint = paint(display(800), heroPaint.textSize * 0.34f, if (style == StoryStyle.BIB) INK else accent, 0.02f)
        val unitGap = 18f
        val totalW = { heroPaint.measureText(hero.value) + if (hero.unit.isEmpty()) 0f else unitGap + unitPaint.measureText(hero.unit) }
        while (totalW() > W - 2 * M && heroPaint.textSize > 120f) {
            heroPaint.textSize -= 8f; unitPaint.textSize = heroPaint.textSize * 0.34f
        }
        val heroBase = heroLimit
        c.drawText(hero.value, M - 8f, heroBase, heroPaint)
        if (hero.unit.isNotEmpty()) {
            val ux = M - 8f + heroPaint.measureText(hero.value) + unitGap
            if (style == StoryStyle.BIB) {
                c.drawRect(ux - 10f, heroBase - unitPaint.textSize * 0.82f, ux + unitPaint.measureText(hero.unit) + 10f,
                    heroBase + 10f, Paint().apply { color = accent })
            }
            c.drawText(hero.unit, ux, heroBase, unitPaint)
        }
        val heroTop = heroBase - heroPaint.textSize * 0.74f

        // --- route fills the space between header and hero
        val route = w.route
        val routeTop = headerBottom + if (style == StoryStyle.BIB) 110f else 70f
        val routeBottom = heroTop - 70f
        if (route != null && routeBottom - routeTop > 180f) {
            drawRoute(c, route, RectF(M, routeTop, W - M, routeBottom), style, pal, accent)
        }
    }

    internal fun drawRoute(c: Canvas, route: Route, box: RectF, style: StoryStyle, pal: Palette, accent: Int) {
        val all = route.segments.flatten()
        val minLat = all.minOf { it.lat }; val maxLat = all.maxOf { it.lat }
        val minLon = all.minOf { it.lon }; val maxLon = all.maxOf { it.lon }
        // equirectangular with latitude correction so the route isn't stretched sideways
        val kx = cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = (maxLon - minLon) * kx
        val spanY = maxLat - minLat
        if (spanX < 1e-7 && spanY < 1e-7) return
        val s = minOf(box.width() / spanX.coerceAtLeast(1e-9), box.height() / spanY.coerceAtLeast(1e-9))
        val ox = box.centerX() - spanX * s / 2
        val oy = box.centerY() - spanY * s / 2
        fun px(p: LatLon) = (ox + (p.lon - minLon) * kx * s).toFloat()
        fun py(p: LatLon) = (oy + (maxLat - p.lat) * s).toFloat()

        val path = Path()
        for (seg in route.segments) {
            val step = max(1, seg.size / 1500) // plenty for 1080 px, keeps drawing fast
            seg.forEachIndexed { i, p ->
                if (i % step != 0 && i != seg.lastIndex) return@forEachIndexed
                if (i == 0) path.moveTo(px(p), py(p)) else path.lineTo(px(p), py(p))
            }
        }
        fun stroke(width: Float, color: Int, blur: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeWidth = width; this.color = color
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            if (blur > 0) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            if (pal.shadow && blur == 0f) setShadowLayer(10f, 0f, 2f, 0x88000000.toInt())
        }
        if (style == StoryStyle.NIGHT) {
            c.drawPath(path, stroke(34f, alpha(pal.route, 0.28f), 22f))
            c.drawPath(path, stroke(16f, alpha(pal.route, 0.55f), 6f))
        }
        c.drawPath(path, stroke(9f, pal.route))

        val start = all.first(); val end = all.last()
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        if (style == StoryStyle.OVERLAY) { // transparent canvas: hollow start ring, solid end dot
            c.drawCircle(px(start), py(start), 14f, dot.apply { color = pal.fg; setStyle(Paint.Style.STROKE); strokeWidth = 6f })
            c.drawCircle(px(end), py(end), 16f, dot.apply { color = pal.fg; setStyle(Paint.Style.FILL) })
            return
        }
        c.drawCircle(px(start), py(start), 17f, dot.apply { color = pal.fg; setStyle(Paint.Style.FILL) })
        c.drawCircle(px(start), py(start), 9f, dot.apply { color = if (style == StoryStyle.BIB) PAPER else NIGHT })
        c.drawCircle(px(end), py(end), 19f, dot.apply { color = accent })
        c.drawCircle(px(end), py(end), 19f, dot.apply { color = pal.fg; setStyle(Paint.Style.STROKE); strokeWidth = 5f })
    }

    internal fun fitWidth(p: Paint, s: String, maxWidth: Float) {
        while (p.measureText(s) > maxWidth && p.textSize > 20f) p.textSize -= 4f
    }

    internal fun alpha(color: Int, a: Float) = ColorUtils.setAlphaComponent(color, (a * 255).roundToInt())

    // ---------------------------------------------------------------- photos

    /** Decodes a picked photo downsampled to story size, honouring EXIF rotation on API 28+. */
    fun decodeBackground(context: Context, uri: Uri): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                val k = maxOf(1, minOf(info.size.width / W.toInt(), info.size.height / H.toInt()))
                d.setTargetSampleSize(k)
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // hardware bitmaps can't be drawn on a software canvas
            }
        } else {
            val r = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val k = maxOf(1, minOf(bounds.outWidth / W.toInt(), bounds.outHeight / H.toInt()))
            r.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = k }) }
        }
    } catch (e: Exception) { null }
}
