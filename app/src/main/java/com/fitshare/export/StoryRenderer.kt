package com.fitshare.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.applyCanvas
import com.fitshare.data.LatLon
import com.fitshare.data.WorkoutData
import com.fitshare.data.primaryPaceFormatted
import com.fitshare.data.primaryPaceLabel
import com.fitshare.ui.components.StoryCard
import com.fitshare.ui.theme.FitShareTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StoryRenderer {

    /**
     * Renders the StoryCard to a 1080x1920 Bitmap using Android's Picture API.
     * This runs on the main thread to allow Compose drawing, then saves on IO.
     */
    suspend fun renderToBitmap(
        context: Context,
        workout: WorkoutData,
        backgroundUri: Uri?
    ): Bitmap = withContext(Dispatchers.Main) {
        val width = 1080
        val height = 1920

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Draw gradient background directly onto bitmap canvas
        val gradientStart = workout.activityType.gradientStart
        val gradientEnd = workout.activityType.gradientEnd

        val paint = android.graphics.Paint()
        val shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            gradientStart.toArgb(),
            gradientEnd.toArgb(),
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw content
        drawWorkoutContent(canvas, workout, width, height)

        bitmap
    }

    private fun drawWorkoutContent(
        canvas: android.graphics.Canvas,
        workout: WorkoutData,
        width: Int,
        height: Int
    ) {
        val accentArgb = workout.activityType.accentColor.toArgb()
        val whiteArgb = android.graphics.Color.WHITE
        val mutedArgb = android.graphics.Color.parseColor("#8A9BBF")

        val paddingH = (width * 0.08f)
        val paddingV = (height * 0.05f)

        // Header: "FitShare" + emoji
        val headerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = accentArgb
            textSize = height * 0.025f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText("FitShare", paddingH, paddingV + headerPaint.textSize, headerPaint)

        val emojiPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = height * 0.032f
        }
        canvas.drawText(
            workout.activityType.emoji,
            width - paddingH - emojiPaint.measureText(workout.activityType.emoji),
            paddingV + emojiPaint.textSize,
            emojiPaint
        )

        // Middle section Y position (~60% down)
        var y = height * 0.6f

        // Date
        if (workout.dateTimeMillis > 0) {
            val datePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.parseColor("#8A9BBF")
                textSize = height * 0.018f
            }
            val dateStr = SimpleDateFormat("d. MMMM yyyy", Locale.GERMAN)
                .format(Date(workout.dateTimeMillis))
            canvas.drawText(dateStr, paddingH, y, datePaint)
            y += datePaint.textSize * 1.5f
        }

        // Activity label
        val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = whiteArgb
            textSize = height * 0.038f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        canvas.drawText(workout.activityType.labelDe, paddingH, y, labelPaint)
        y += labelPaint.textSize * 1.6f

        // Primary stats row
        val statValuePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = whiteArgb
            textSize = height * 0.032f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val statLabelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedArgb
            textSize = height * 0.018f
        }

        val primaryStats = buildPrimaryStatsList(workout)
        if (primaryStats.isNotEmpty()) {
            val colWidth = (width - paddingH * 2) / primaryStats.size
            primaryStats.forEachIndexed { i, (value, label) ->
                val x = paddingH + i * colWidth
                canvas.drawText(value, x, y, statValuePaint)
                canvas.drawText(label, x, y + statValuePaint.textSize + 4, statLabelPaint)
            }
            y += statValuePaint.textSize + statLabelPaint.textSize + height * 0.04f
        }

        // Hero distance
        val (distValue, distUnit) = workout.formattedDistance()
        if (workout.distanceMeters > 0f) {
            val heroPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = whiteArgb
                textSize = height * 0.10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val heroUnitPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = accentArgb
                textSize = height * 0.045f
            }
            canvas.drawText(distValue, paddingH, y + heroPaint.textSize * 0.85f, heroPaint)
            val unitX = paddingH + heroPaint.measureText(distValue) + 16f
            canvas.drawText(distUnit, unitX, y + heroPaint.textSize * 0.85f, heroUnitPaint)

            y += heroPaint.textSize + height * 0.015f

            // Accent bar
            val accentBarPaint = android.graphics.Paint().apply { color = accentArgb }
            canvas.drawRect(paddingH, y, paddingH + 120f, y + 6f, accentBarPaint)
            y += height * 0.04f
        }

        // GPS route preview — rendered between accent bar and secondary stats
        if (!workout.gpsTrack.isNullOrEmpty()) {
            val routeWidth = width * 0.60f
            val routeHeight = height * 0.18f
            val routeLeft = (width - routeWidth) / 2f
            val routeTop = height * 0.57f  // anchored at ~57% of image height

            val routeRect = android.graphics.RectF(
                routeLeft, routeTop, routeLeft + routeWidth, routeTop + routeHeight
            )
            RouteRenderer.drawRoute(canvas, workout.gpsTrack, routeRect, workout.activityType)

            // Push y past the route area so secondary stats don't overlap
            val routeBottom = routeTop + routeHeight
            if (y < routeBottom + height * 0.03f) {
                y = routeBottom + height * 0.03f
            }
        }

        // Secondary stats
        val secondaryStats = buildSecondaryStatsList(workout)
        if (secondaryStats.isNotEmpty()) {
            val colWidth = (width - paddingH * 2) / secondaryStats.size
            secondaryStats.forEachIndexed { i, (value, label) ->
                val x = paddingH + i * colWidth
                canvas.drawText(value, x, y, statValuePaint)
                canvas.drawText(label, x, y + statValuePaint.textSize + 4, statLabelPaint)
            }
        }

        // Footer
        val footerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedArgb
            textSize = height * 0.016f
        }
        canvas.drawText(
            "FitShare • Gadgetbridge",
            paddingH,
            height - paddingV,
            footerPaint
        )
    }

    private fun buildPrimaryStatsList(workout: WorkoutData): List<Pair<String, String>> {
        val stats = mutableListOf<Pair<String, String>>()
        if (workout.activeSeconds > 0) stats.add(workout.formattedTime() to "Zeit")
        // Pace is the second-most important stat after distance — show it prominently
        workout.primaryPaceFormatted()?.let { stats.add(it to workout.primaryPaceLabel()) }
        when (workout.activityType) {
            com.fitshare.data.ActivityType.SWIMMING -> {
                workout.swimStyle?.let { stats.add(it.labelDe to "Stil") }
                if (workout.laps > 0) stats.add(workout.laps.toString() to "Bahnen")
            }
            com.fitshare.data.ActivityType.RUNNING -> {
                if (workout.steps > 0) stats.add(workout.steps.toString() to "Schritte")
            }
            else -> {}
        }
        return stats.take(3)
    }

    private fun buildSecondaryStatsList(workout: WorkoutData): List<Pair<String, String>> {
        val stats = mutableListOf<Pair<String, String>>()
        if (workout.calories > 0) stats.add(workout.calories.toString() to "kcal")
        if (workout.strokes > 0) stats.add(workout.strokes.toString() to "Züge")
        if (workout.swolfAvg > 0) stats.add(workout.swolfAvg.toString() to "SWOLF")
        if (workout.averageHR > 0) stats.add(workout.averageHR.toString() to "Ø Puls")
        return stats.take(3)
    }

    // Extension to get ARGB int from Compose Color
    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        val r = (red * 255).toInt()
        val g = (green * 255).toInt()
        val b = (blue * 255).toInt()
        val a = (alpha * 255).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }

    private fun WorkoutData.formattedTime(): String {
        val hours = activeSeconds / 3600
        val minutes = (activeSeconds % 3600) / 60
        val seconds = activeSeconds % 60
        return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    private fun WorkoutData.formattedDistance(): Pair<String, String> {
        return if (distanceMeters >= 1000f) {
            val km = distanceMeters / 1000f
            if (km == km.toLong().toFloat()) "%.0f".format(km) to "km"
            else "%.2f".format(km) to "km"
        } else {
            "%.0f".format(distanceMeters) to "m"
        }
    }
}
