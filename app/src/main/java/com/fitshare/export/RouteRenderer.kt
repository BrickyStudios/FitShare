package com.fitshare.export

import android.graphics.*
import com.fitshare.data.ActivityType
import com.fitshare.data.LatLon

/**
 * Renders a GPS track as a route silhouette on an Android Canvas.
 *
 * Design: Strava-inspired — bright colored polyline with subtle glow,
 * no map background (the route IS the visual hero).
 *
 * Usage: Call drawRoute() in StoryRenderer after the background is drawn.
 */
object RouteRenderer {

    /**
     * Draws the GPS route into [canvas] within the given [rect].
     *
     * @param canvas  The canvas to draw on (e.g., the 1080x1920 story bitmap canvas)
     * @param points  GPS track points (lat/lon)
     * @param rect    The area within the canvas to fill with the route map
     * @param activityType Used to pick the accent color matching the sport
     */
    fun drawRoute(
        canvas: Canvas,
        points: List<LatLon>,
        rect: RectF,
        activityType: ActivityType
    ) {
        if (points.size < 2) return

        val accentColor = accentColorFor(activityType)
        val path = buildPath(points, rect)

        // Dark background for the route area
        val bgPaint = Paint().apply {
            color = Color.parseColor("#0D1117")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 24f, 24f, bgPaint)

        // Subtle border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#1E2535")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(rect, 24f, 24f, borderPaint)

        // Clip drawing to the route rect
        canvas.save()
        val clipPath = Path().apply { addRoundRect(rect, 24f, 24f, Path.Direction.CW) }
        canvas.clipPath(clipPath)

        // Outer glow
        val glowPaint = Paint().apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 18f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
            isAntiAlias = true
            alpha = 80
        }
        canvas.drawPath(path, glowPaint)

        // Inner glow
        val innerGlowPaint = Paint().apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
            isAntiAlias = true
            alpha = 140
        }
        canvas.drawPath(path, innerGlowPaint)

        // Crisp main line
        val linePaint = Paint().apply {
            color = accentColor
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(path, linePaint)

        // Start dot (green)
        if (points.isNotEmpty()) {
            val startScreen = latLonToScreen(points.first(), points, rect)
            val dotPaint = Paint().apply {
                color = Color.parseColor("#4CAF50")
                isAntiAlias = true
            }
            canvas.drawCircle(startScreen.x, startScreen.y, 10f, dotPaint)
        }

        // End dot (accent color, slightly larger)
        if (points.size > 1) {
            val endScreen = latLonToScreen(points.last(), points, rect)
            val dotPaint = Paint().apply {
                color = accentColor
                isAntiAlias = true
            }
            canvas.drawCircle(endScreen.x, endScreen.y, 12f, dotPaint)
        }

        canvas.restore()
    }

    private fun buildPath(points: List<LatLon>, rect: RectF): Path {
        val path = Path()
        val padding = 20f
        val drawRect = RectF(
            rect.left + padding,
            rect.top + padding,
            rect.right - padding,
            rect.bottom - padding
        )

        val minLat = points.minOf { it.lat }
        val maxLat = points.maxOf { it.lat }
        val minLon = points.minOf { it.lon }
        val maxLon = points.maxOf { it.lon }

        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon

        // Handle edge case: all points at same location
        if (latRange < 0.000001 && lonRange < 0.000001) return path

        // Maintain aspect ratio
        val drawWidth = drawRect.width()
        val drawHeight = drawRect.height()
        val scaleX = if (lonRange > 0) drawWidth / lonRange else 1.0
        val scaleY = if (latRange > 0) drawHeight / latRange else 1.0
        val scale = minOf(scaleX, scaleY)

        // Center the route
        val usedWidth = lonRange * scale
        val usedHeight = latRange * scale
        val offsetX = drawRect.left + (drawWidth - usedWidth) / 2.0
        val offsetY = drawRect.top + (drawHeight - usedHeight) / 2.0

        fun toX(lon: Double) = (offsetX + (lon - minLon) * scale).toFloat()
        fun toY(lat: Double) = (offsetY + (maxLat - lat) * scale).toFloat() // flip Y axis

        path.moveTo(toX(points[0].lon), toY(points[0].lat))
        for (i in 1 until points.size) {
            // Smooth with quadratic bezier for every 2 points
            if (i < points.size - 1) {
                val cx = (toX(points[i].lon) + toX(points[i + 1].lon)) / 2f
                val cy = (toY(points[i].lat) + toY(points[i + 1].lat)) / 2f
                path.quadTo(toX(points[i].lon), toY(points[i].lat), cx, cy)
            } else {
                path.lineTo(toX(points[i].lon), toY(points[i].lat))
            }
        }

        return path
    }

    private fun latLonToScreen(point: LatLon, allPoints: List<LatLon>, rect: RectF): PointF {
        val padding = 20f
        val drawRect = RectF(rect.left + padding, rect.top + padding, rect.right - padding, rect.bottom - padding)
        val minLat = allPoints.minOf { it.lat }
        val maxLat = allPoints.maxOf { it.lat }
        val minLon = allPoints.minOf { it.lon }
        val maxLon = allPoints.maxOf { it.lon }
        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon
        val scale = minOf(
            if (lonRange > 0) drawRect.width() / lonRange else 1.0,
            if (latRange > 0) drawRect.height() / latRange else 1.0
        )
        val offsetX = drawRect.left + (drawRect.width() - lonRange * scale) / 2.0
        val offsetY = drawRect.top + (drawRect.height() - latRange * scale) / 2.0
        return PointF(
            (offsetX + (point.lon - minLon) * scale).toFloat(),
            (offsetY + (maxLat - point.lat) * scale).toFloat()
        )
    }

    private fun accentColorFor(activityType: ActivityType): Int = when (activityType) {
        ActivityType.SWIMMING -> Color.parseColor("#2196F3")  // Blue
        ActivityType.RUNNING -> Color.parseColor("#4CAF50")   // Green
        ActivityType.CYCLING -> Color.parseColor("#FF9800")   // Orange
        else -> Color.parseColor("#9C27B0")                   // Purple
    }
}
