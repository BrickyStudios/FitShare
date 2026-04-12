package com.fitshare.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fitshare.data.WorkoutState
import com.fitshare.data.WorkoutViewModel
import com.fitshare.ui.components.StoryCard
import com.fitshare.ui.theme.OnSurfaceMuted
import com.fitshare.ui.theme.SurfaceVariantDark

@Composable
fun EditorScreen(
    viewModel: WorkoutViewModel,
    onShare: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val backgroundUri by viewModel.backgroundUri.collectAsState()
    val gpsTrack by viewModel.gpsTrack.collectAsState()
    val workout = (state as? WorkoutState.Success)?.workout ?: return
    val workoutWithGps = workout.copy(gpsTrack = gpsTrack)

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> viewModel.setBackgroundUri(uri) }

    val gpxPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadGpxFromUri(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Zurück", color = OnSurfaceMuted)
            }
            Text(
                "Story erstellen",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            TextButton(onClick = onShare) {
                Text("Teilen →", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Story preview (scaled down to fit screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // 9:16 aspect ratio preview
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val previewWidth = maxWidth * 0.85f
                val previewHeight = previewWidth * (16f / 9f)
                val constrainedHeight = minOf(previewHeight, maxHeight * 0.9f)
                val constrainedWidth = constrainedHeight * (9f / 16f)

                Box(
                    modifier = Modifier
                        .width(constrainedWidth)
                        .height(constrainedHeight)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    StoryCard(
                        workout = workoutWithGps,
                        backgroundUri = backgroundUri,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Background picker
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text(if (backgroundUri != null) "🖼️ Bild ändern" else "🖼️ Foto wählen")
            }

            if (backgroundUri != null) {
                OutlinedButton(
                    onClick = { viewModel.setBackgroundUri(null) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OnSurfaceMuted
                    )
                ) {
                    Text("✕ Entfernen")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // GPX controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (gpsTrack == null) {
                OutlinedButton(
                    onClick = { gpxPickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🗺️ GPX laden")
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.clearGpsTrack() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("✕ Route entfernen")
                }
            }
        }

        if (gpsTrack != null) {
            Text(
                text = "✓ GPS-Route geladen (${gpsTrack!!.size} Punkte)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = workout.activityType.accentColor
            )
        ) {
            Text(
                "📤  Story teilen",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
