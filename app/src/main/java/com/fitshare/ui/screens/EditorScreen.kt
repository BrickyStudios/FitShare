package com.fitshare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fitshare.data.Sport
import com.fitshare.data.UiState
import com.fitshare.data.Workout
import com.fitshare.data.WorkoutViewModel
import com.fitshare.data.formatDistance
import com.fitshare.data.formatInt
import com.fitshare.export.ShareHelper
import com.fitshare.export.StoryRenderer
import com.fitshare.export.StoryStyle
import com.fitshare.ui.theme.BigButton
import com.fitshare.ui.theme.Bone
import com.fitshare.ui.theme.Hairline
import com.fitshare.ui.theme.Muted
import com.fitshare.ui.theme.OnAccent
import com.fitshare.ui.theme.Raised
import com.fitshare.ui.theme.Raised2
import com.fitshare.ui.theme.Type
import com.fitshare.ui.theme.pressable
import com.fitshare.ui.theme.uiSpring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@Composable
fun EditorScreen(state: UiState, vm: WorkoutViewModel) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // Keep the last workout so the screen doesn't blank out while it animates away after "back".
    var last by remember { mutableStateOf<Workout?>(null) }
    state.workout?.let { last = it }
    val workout = last ?: return
    val accent = Color(workout.sport.accent)

    BackHandler { vm.reset() }
    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); vm.clearError() }
    }

    val background by produceState<Bitmap?>(null, state.background) {
        value = state.background?.let { withContext(Dispatchers.IO) { StoryRenderer.decodeBackground(ctx, it) } }
    }
    val prefs = remember { ctx.getSharedPreferences("editor", android.content.Context.MODE_PRIVATE) }
    val pager = rememberPagerState(initialPage = prefs.getInt("style", 0).coerceIn(0, StoryStyle.entries.lastIndex)) { StoryStyle.entries.size }
    LaunchedEffect(pager.settledPage) { prefs.edit().putInt("style", pager.settledPage).apply() }
    val style = StoryStyle.entries[pager.currentPage]
    var busy by remember { mutableStateOf(false) }

    fun renderFull() = StoryRenderer.render(ctx, workout, style, background)

    fun save() = scope.launch {
        busy = true
        val ok = ShareHelper.saveToGallery(ctx, withContext(Dispatchers.Default) { renderFull() })
        busy = false
        Toast.makeText(ctx, if (ok) "Gespeichert in Bilder/FitShare" else "Speichern hat nicht geklappt", Toast.LENGTH_SHORT).show()
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(ctx, "Ohne Speicherrecht kein Speichern", Toast.LENGTH_SHORT).show()
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setBackground(uri)
    }
    val routePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.open(it) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 8.dp)) {
        // top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextAction("‹  Neu") { vm.reset() }
            TextAction("Speichern", enabled = !busy) {
                val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
            }
        }

        // story pages: swipe between styles; neighbours peek in, scaled back
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 52.dp),
            pageSpacing = 10.dp,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)
        ) { page ->
            val pageStyle = StoryStyle.entries[page]
            val preview by produceState<Bitmap?>(null, workout, pageStyle, background) {
                value = withContext(Dispatchers.Default) { StoryRenderer.render(ctx, workout, pageStyle, background, 0.5f) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                        .graphicsLayer {
                            val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            val s = 1f - 0.08f * off
                            scaleX = s; scaleY = s
                            alpha = 1f - 0.45f * off
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .then(if (pageStyle == StoryStyle.STICKER) Modifier.checkerboard() else Modifier.background(Raised))
                        .semantics { contentDescription = "Vorschau Stil ${pageStyle.label}" }
                ) {
                    Crossfade(preview, label = "preview") { bmp ->
                        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize())
                    }
                }
            }
        }

        // style tabs mirror the pager
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            StoryStyle.entries.forEachIndexed { i, s ->
                val selected = pager.currentPage == i
                val color by animateColorAsState(if (selected) Bone else Muted.copy(alpha = 0.45f), uiSpring(), label = "tab")
                Column(
                    Modifier
                        .pressable { scope.launch { pager.animateScrollToPage(i, animationSpec = uiSpring()) } }
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(s.label.uppercase(), style = Type.label.copy(color = color))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) accent else Color.Transparent))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // sport chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Sport.entries.forEach { sport ->
                val selected = sport == workout.sport
                val bg by animateColorAsState(if (selected) Color(sport.accent) else Raised2, uiSpring(), label = "chip")
                Row(
                    Modifier
                        .pressable {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            vm.setSport(sport)
                        }
                        .clip(CircleShape)
                        .background(bg)
                        .heightIn(min = 44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!selected) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Color(sport.accent)))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(sport.label, style = Type.button.copy(fontSize = Type.body.fontSize, color = if (selected) OnAccent else Bone))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // tools
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(
                title = "Foto",
                detail = if (state.background != null) "Gesetzt" else "Hintergrund wählen",
                active = state.background != null,
                modifier = Modifier.weight(1f),
                onRemove = { vm.setBackground(null) },
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
            val route = state.route
            ToolTile(
                title = "Route",
                detail = route?.let {
                    val d = formatDistance(it.distanceMeters)
                    "${d.value} ${d.unit.lowercase()} · ${formatInt(it.pointCount)} Pkt."
                } ?: "GPX · KML · KMZ",
                active = route != null,
                modifier = Modifier.weight(1f),
                onRemove = { vm.clearRoute() },
                onClick = { routePicker.launch(arrayOf("*/*")) }
            )
        }

        Spacer(Modifier.height(12.dp))

        BigButton(
            "In Story teilen",
            Modifier.padding(horizontal = 20.dp),
            color = accent,
            busy = busy
        ) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            )
            scope.launch {
                busy = true
                try {
                    val bmp = withContext(Dispatchers.Default) { renderFull() }
                    ShareHelper.share(ctx, ShareHelper.cacheForSharing(ctx, bmp))
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Teilen fehlgeschlagen: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    busy = false
                }
            }
        }
    }
}

@Composable
private fun TextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.pressable(enabled, onClick).heightIn(min = 44.dp).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = Type.button.copy(color = if (enabled) Bone else Muted))
    }
}

@Composable
private fun ToolTile(
    title: String,
    detail: String,
    active: Boolean,
    modifier: Modifier,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Raised)
            .drawBehind { drawRect(Hairline, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
            .heightIn(min = 64.dp)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title.uppercase(), style = Type.label.copy(color = if (active) Bone else Muted))
            Spacer(Modifier.height(4.dp))
            Text(detail, style = Type.body.copy(fontSize = Type.label.fontSize * 1.2f), maxLines = 1)
        }
        if (active) {
            Box(
                Modifier.pressable(onClick = onRemove).size(44.dp).semantics { contentDescription = "$title entfernen" },
                contentAlignment = Alignment.Center
            ) {
                Text("✕", style = Type.button.copy(color = Muted))
            }
        } else {
            Text("+", style = Type.title.copy(color = Muted), modifier = Modifier.padding(end = 16.dp))
        }
    }
}

/** Grey checkerboard behind the transparent sticker preview, like any image editor. */
private fun Modifier.checkerboard() = drawBehind {
    val tile = 12.dp.toPx()
    drawRect(Color(0xFF2A2B27))
    var y = 0f; var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else tile
        while (x < size.width) { drawRect(Color(0xFF353631), Offset(x, y), Size(tile, tile)); x += tile * 2 }
        y += tile; row++
    }
}
