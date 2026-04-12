package com.fitshare.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitshare.data.WorkoutState
import com.fitshare.data.WorkoutViewModel
import com.fitshare.ui.theme.OnSurfaceMuted
import com.fitshare.ui.theme.SurfaceVariantDark

@Composable
fun ImportScreen(
    viewModel: WorkoutViewModel,
    onWorkoutLoaded: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.loadFromUri(it) }
    }

    LaunchedEffect(state) {
        if (state is WorkoutState.Success) onWorkoutLoaded()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🏋️",
            fontSize = 72.sp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "FitShare",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Gadgetbridge Workouts als\nInstagram Story teilen",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        when (state) {
            is WorkoutState.Loading -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Lade Workout...", color = OnSurfaceMuted)
            }
            is WorkoutState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1A1A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ ${(state as WorkoutState.Error).message}",
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(24.dp))
                ImportButton { filePicker.launch(arrayOf("application/json", "*/*")) }
            }
            else -> {
                ImportButton { filePicker.launch(arrayOf("application/json", "*/*")) }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Gadgetbridge → Aktivität → Export → JSON",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            "📂  JSON Datei öffnen",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}
