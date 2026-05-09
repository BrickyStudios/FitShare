package com.fitshare.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fitshare.data.WorkoutData
import com.fitshare.data.formattedDistance
import com.fitshare.data.formattedTime
import com.fitshare.data.primaryPaceFormatted
import com.fitshare.data.primaryPaceLabel
import com.fitshare.ui.theme.OnSurfaceMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StoryCard(
    workout: WorkoutData,
    backgroundUri: Uri?,
    modifier: Modifier = Modifier
) {
    val accent = workout.activityType.accentColor
    val gradientStart = workout.activityType.gradientStart
    val gradientEnd = workout.activityType.gradientEnd

    Box(modifier = modifier) {
        // Background
        if (backgroundUri != null) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Dark overlay so text stays readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(gradientStart, gradientEnd)
                        )
                    )
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            // Header: App name + activity type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FitShare",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = workout.activityType.emoji,
                    fontSize = 24.sp
                )
            }

            Spacer(Modifier.weight(1f))

            // Date + Activity label
            if (workout.dateTimeMillis > 0) {
                Text(
                    text = formatDate(workout.dateTimeMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = workout.activityType.labelDe,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            // Primary stat row: Zeit, Stil/Pace, Bahnen
            val primaryStats = buildPrimaryStats(workout)
            if (primaryStats.isNotEmpty()) {
                StatGrid(stats = primaryStats, accentColor = accent)
                Spacer(Modifier.height(20.dp))
            }

            // Hero: Distance
            val (distValue, distUnit) = workout.formattedDistance()
            if (workout.distanceMeters > 0f) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = distValue,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 80.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = distUnit,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Normal,
                        color = accent,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                // Accent divider under hero
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(3.dp)
                        .background(accent)
                )
                Spacer(Modifier.height(20.dp))
            }

            // Secondary stats: kcal, Züge, SWOLF / HR
            val secondaryStats = buildSecondaryStats(workout)
            if (secondaryStats.isNotEmpty()) {
                StatGrid(stats = secondaryStats, accentColor = accent)
            }

            Spacer(Modifier.weight(1f))

            // Footer
            Text(
                text = "FitShare • Gadgetbridge",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun StatGrid(stats: List<Pair<String, String>>, accentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        stats.forEachIndexed { index, (value, label) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (index == 0) Alignment.Start else Alignment.CenterHorizontally
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceMuted
                )
            }
            if (index < stats.size - 1) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(accentColor.copy(alpha = 0.3f))
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

private fun buildPrimaryStats(workout: WorkoutData): List<Pair<String, String>> {
    val stats = mutableListOf<Pair<String, String>>()
    if (workout.activeSeconds > 0) stats.add(workout.formattedTime() to "Zeit")
    // Pace is the second-most important stat after distance — show it prominently
    workout.primaryPaceFormatted()?.let { stats.add(it to workout.primaryPaceLabel()) }
    when (workout.activityType) {
        com.fitshare.data.ActivityType.SWIMMING -> {
            if (workout.laps > 0) stats.add(workout.laps.toString() to "Bahnen")
        }
        com.fitshare.data.ActivityType.RUNNING -> {
            if (workout.steps > 0) stats.add(workout.steps.toString() to "Schritte")
        }
        else -> {}
    }
    return stats.take(3)
}

private fun buildSecondaryStats(workout: WorkoutData): List<Pair<String, String>> {
    val stats = mutableListOf<Pair<String, String>>()
    if (workout.calories > 0) stats.add(workout.calories.toString() to "kcal")
    if (workout.strokes > 0) stats.add(workout.strokes.toString() to "Züge")
    if (workout.swolfAvg > 0) stats.add(workout.swolfAvg.toString() to "SWOLF")
    if (workout.averageHR > 0) stats.add(workout.averageHR.toString() to "Ø Puls")
    if (workout.maxHR > 0) stats.add(workout.maxHR.toString() to "Max Puls")
    return stats.take(3)
}

private fun formatDate(millis: Long): String {
    val sdf = SimpleDateFormat("d. MMMM yyyy", Locale.GERMAN)
    return sdf.format(Date(millis))
}
