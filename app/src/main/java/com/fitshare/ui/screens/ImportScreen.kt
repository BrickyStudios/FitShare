package com.fitshare.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.fitshare.data.Source
import com.fitshare.data.UiState
import com.fitshare.data.WorkoutViewModel
import com.fitshare.data.tr
import com.fitshare.ui.theme.BigButton
import com.fitshare.ui.theme.Bone
import com.fitshare.ui.theme.Hairline
import com.fitshare.ui.theme.Muted
import com.fitshare.ui.theme.OnAccent
import com.fitshare.ui.theme.Raised2
import com.fitshare.ui.theme.Type
import com.fitshare.ui.theme.Volt
import com.fitshare.ui.theme.pressable
import com.fitshare.ui.theme.uiSpring
import kotlinx.coroutines.delay

// "*/*" last: some file managers hide FIT/TCX/KMZ behind made-up types
private val PICK_TYPES = arrayOf(
    "image/*", "application/vnd.ant.fit", "application/json", "application/gpx+xml", "application/vnd.google-earth.kml+xml",
    "application/vnd.google-earth.kmz", "application/octet-stream", "application/xml", "text/xml", "text/plain", "*/*"
)

private val Gutter = 24.dp

/** Which of the known fitness apps are installed, mapped to the package that opens them. */
private fun installedSources(ctx: Context): Map<Source, String> =
    Source.entries.mapNotNull { s ->
        s.packages.firstOrNull { ctx.packageManager.getLaunchIntentForPackage(it) != null }?.let { s to it }
    }.toMap()

@Composable
fun ImportScreen(state: UiState, vm: WorkoutViewModel) {
    val ctx = LocalContext.current
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.open(it) }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { uri -> vm.open(listOf(uri)) } }
    val installed = remember { installedSources(ctx) }
    var source by remember { mutableStateOf(Source.preselect(installed.keys, vm.lastSource())) }
    val openPackage = installed[source]

    fun pickScreenshot() = photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter).reveal(0), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("FITSHARE", style = Type.label.copy(color = Bone))
            Text(tr("OFFLINE · OHNE KONTO", "OFFLINE · NO ACCOUNT"), style = Type.label)
        }
        Spacer(Modifier.height(48.dp))
        Text(
            buildAnnotatedString {
                append(tr("DEIN\nTRAINING.\n", "YOUR\nWORKOUT.\n"))
                withStyle(SpanStyle(color = Volt)) { append(tr("ALS STORY.", "AS A STORY.")) }
            },
            style = Type.hero,
            modifier = Modifier.padding(horizontal = Gutter).reveal(1)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            tr(
                "Werte aus deiner Fitness-App oder einer Datei rein, fertiger Sticker für die Instagram-Story raus. Alles bleibt auf dem Handy.",
                "Numbers from your fitness app or a file go in, a sticker for your Instagram story comes out. Nothing leaves your phone."
            ),
            style = Type.body,
            modifier = Modifier.padding(horizontal = Gutter).reveal(2)
        )
        Spacer(Modifier.height(32.dp))

        Text(tr("DEINE APP", "YOUR APP"), style = Type.label, modifier = Modifier.padding(horizontal = Gutter).reveal(3))
        Spacer(Modifier.height(10.dp))
        val order = remember { Source.ordered(installed.keys) }
        val chips = rememberLazyListState()
        // the preselected chip may sit past the screen edge ("Other" is last)
        LaunchedEffect(Unit) { chips.scrollToItem(order.indexOf(source)) }
        LazyRow(
            Modifier.fillMaxWidth().reveal(3),
            state = chips,
            contentPadding = PaddingValues(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(order) { s ->
                SourceChip(s.title, selected = s == source, installed = s in installed) {
                    source = s
                    vm.rememberSource(s)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = source,
            transitionSpec = { fadeIn(uiSpring()) togetherWith fadeOut(uiSpring()) },
            modifier = Modifier.padding(horizontal = Gutter).reveal(4),
            label = "steps"
        ) { s ->
            Column {
                Step("01", tr("Werte", "Numbers"), s.numbers)
                s.map?.let { Step("02", tr("Karte · optional", "Map · optional"), it) }
                Step(if (s.map == null) "02" else "03", "Sticker", tr(
                    "Gestalten, als PNG speichern, in Instagram als Foto-Sticker einfügen.",
                    "Style it, save the PNG, add it as a photo sticker in Instagram."
                ))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
            }
        }
        Spacer(Modifier.height(28.dp))

        Column(Modifier.padding(horizontal = Gutter).reveal(5)) {
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
            if (openPackage != null) {
                BigButton(tr("${source.title} öffnen", "Open ${source.title}"), busy = state.loading) {
                    ctx.packageManager.getLaunchIntentForPackage(openPackage)?.let(ctx::startActivity)
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // without an app to open, the screenshot is the main way in
                val primary = openPackage == null
                BigButton(
                    tr("Screenshot wählen", "Pick screenshot"), Modifier.weight(1f),
                    color = if (primary) Volt else Raised2, contentColor = if (primary) OnAccent else Bone,
                    busy = primary && state.loading
                ) { pickScreenshot() }
                BigButton(tr("Datei wählen", "Pick file"), Modifier.weight(1f), color = Raised2, contentColor = Bone) { files.launch(PICK_TYPES) }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).pressable { vm.openDemo() },
                contentAlignment = Alignment.Center
            ) {
                Text(tr("Erst ein Beispiel ansehen →", "See an example first →"), style = Type.button.copy(color = Bone))
            }
            Text(
                tr(
                    "Werte und Route gehen auch zusammen in einem Rutsch. Menüs heißen je nach App-Version etwas anders.",
                    "You can pick the numbers and the route together. Menu names vary between app versions."
                ),
                style = Type.body.copy(fontSize = Type.label.fontSize * 1.15f, color = Muted),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SourceChip(text: String, selected: Boolean, installed: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Volt else Raised2, uiSpring(), label = "source")
    Row(
        Modifier
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = text + if (installed) tr(", installiert", ", installed") else ""
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (installed) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (selected) OnAccent else Volt))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.button.copy(fontSize = Type.body.fontSize, color = if (selected) OnAccent else Bone))
    }
}

@Composable
private fun Step(number: String, title: String, text: String) {
    Column {
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
