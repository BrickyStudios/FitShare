package com.fitshare.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fitshare.data.UiState
import com.fitshare.ui.theme.BigButton
import com.fitshare.ui.theme.Bone
import com.fitshare.ui.theme.Hairline
import com.fitshare.ui.theme.Type
import com.fitshare.ui.theme.Volt
import com.fitshare.ui.theme.uiSpring
import kotlinx.coroutines.delay

private val PICK_TYPES = arrayOf(
    "application/json", "application/gpx+xml", "application/vnd.google-earth.kml+xml",
    "application/vnd.google-earth.kmz", "application/octet-stream", "application/xml", "text/xml", "*/*"
)

@Composable
fun ImportScreen(state: UiState, onOpen: (List<android.net.Uri>) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onOpen(it) }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().reveal(0), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FITSHARE", style = Type.label.copy(color = Bone))
            Text("OFFLINE · OHNE KONTO", style = Type.label)
        }
        Spacer(Modifier.height(72.dp))
        Text(
            buildAnnotatedString {
                append("DEIN\nTRAINING.\n")
                withStyle(SpanStyle(color = Volt)) { append("ALS STORY.") }
            },
            style = Type.hero,
            modifier = Modifier.reveal(1)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Gadgetbridge-Export und Route rein, fertige Instagram-Story raus. Alles bleibt auf dem Handy.",
            style = Type.body,
            modifier = Modifier.reveal(2)
        )
        Spacer(Modifier.height(36.dp))
        Step(3, "01", "Workout", "Gadgetbridge → Aktivität → Teilen. Die .json-Datei.")
        Step(4, "02", "Route", "Optional: GPX, KML oder KMZ, zum Beispiel aus OpenTracks.")
        Step(5, "03", "Stil", "Wischen, Sportart wählen, Foto drunter, teilen.")
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Spacer(Modifier.height(40.dp))

        state.error?.let {
            Text(
                it,
                style = Type.body.copy(color = Color(0xFFFF8A80)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x22FF3D5A))
                    .padding(14.dp)
            )
            Spacer(Modifier.height(16.dp))
        }
        BigButton("Dateien wählen", Modifier.reveal(6), busy = state.loading) { picker.launch(PICK_TYPES) }
        Spacer(Modifier.height(12.dp))
        Text(
            "JSON und Route gehen auch zusammen in einem Rutsch.",
            style = Type.body.copy(fontSize = Type.label.fontSize * 1.15f),
            modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally).reveal(6)
        )
    }
}

@Composable
private fun Step(index: Int, number: String, title: String, text: String) {
    Column(Modifier.reveal(index)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
        Row(Modifier.padding(vertical = 14.dp)) {
            Text(number, style = Type.title.copy(color = Volt), modifier = Modifier.width(48.dp))
            Column {
                Text(title.uppercase(), style = Type.label.copy(color = Bone))
                Spacer(Modifier.height(4.dp))
                Text(text, style = Type.body)
            }
        }
    }
}

/** Staggered entrance: fade + 24 dp rise, critically damped. */
@Composable
private fun Modifier.reveal(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        progress.animateTo(1f, uiSpring())
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}
