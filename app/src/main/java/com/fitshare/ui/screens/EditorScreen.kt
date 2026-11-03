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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fitshare.data.Look
import com.fitshare.data.Sport
import com.fitshare.data.UiState
import com.fitshare.data.Workout
import com.fitshare.data.WorkoutViewModel
import com.fitshare.data.formatDistance
import com.fitshare.data.formatInt
import com.fitshare.data.StatKey
import com.fitshare.data.availableStats
import com.fitshare.data.pickStats
import com.fitshare.data.tr
import com.fitshare.export.DIM_LEVELS
import com.fitshare.export.InkColor
import com.fitshare.export.PLACE_ORDER
import com.fitshare.export.SIZE_ORDER
import com.fitshare.export.placeLabel
import com.fitshare.export.sizeLabel
import com.fitshare.export.dimLabel
import com.fitshare.export.OverlayLayout
import com.fitshare.export.OverlayOptions
import com.fitshare.export.ShareHelper
import com.fitshare.export.StoryFormat
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

private enum class Section(val de: String, val en: String) {
    LOOKS("Looks", "Looks"), LAYOUT("Layout", "Layout"), STATS("Werte", "Stats"), COLOR("Farbe", "Colour"), PHOTO("Foto", "Photo"), EXTRAS("Extras", "Extras"), SPORT("Sport", "Sport");
    val label get() = tr(de, en)
}

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
    val overlay by vm.overlay.collectAsState()

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
    var format by remember { mutableStateOf(StoryFormat.entries.getOrElse(prefs.getInt("format", 0)) { StoryFormat.STORY }) }
    var busy by remember { mutableStateOf(false) }
    val looks by vm.looks.collectAsState()
    // with saved looks, a new workout starts on them: one tap and it looks like last time
    var section by remember { mutableStateOf(if (looks.presets.isNotEmpty() || looks.history.isNotEmpty()) Section.LOOKS else Section.LAYOUT) }
    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Look?>(null) }

    fun renderFull() = StoryRenderer.render(ctx, workout, style, background, overlay = overlay, format = format)

    fun save() = scope.launch {
        busy = true
        val ok = ShareHelper.saveToGallery(ctx, withContext(Dispatchers.Default) { renderFull() })
        busy = false
        if (ok && style == StoryStyle.OVERLAY) vm.rememberLook()
        Toast.makeText(
            ctx,
            if (ok) tr("Gespeichert in Bilder/FitShare", "Saved to Pictures/FitShare") else tr("Speichern hat nicht geklappt", "Saving failed"),
            Toast.LENGTH_SHORT
        ).show()
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else Toast.makeText(ctx, tr("Ohne Speicherrecht kein Speichern", "Saving needs the storage permission"), Toast.LENGTH_SHORT).show()
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { vm.setBackground(uri); section = Section.PHOTO } // photo options right away
    }
    val routePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.open(it) }

    if (naming) NameDialog(onDismiss = { naming = false }) { name ->
        vm.savePreset(name); naming = false
        Toast.makeText(ctx, tr("Look „$name“ gespeichert", "Saved look “$name”"), Toast.LENGTH_SHORT).show()
    }
    deleting?.let { look ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("„${look.name}“ löschen?", "Delete “${look.name}”?")) },
            confirmButton = { TextButton({ vm.deletePreset(look); deleting = null }) { Text(tr("Löschen", "Delete")) } },
            dismissButton = { TextButton({ deleting = null }) { Text(tr("Behalten", "Keep")) } }
        )
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction(tr("‹  Neu", "‹  New")) { vm.reset() }
        }

        // story pages: swipe between styles; neighbours peek in, scaled back
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 52.dp),
            pageSpacing = 10.dp,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)
        ) { page ->
            val pageStyle = StoryStyle.entries[page]
            val isOverlay = pageStyle == StoryStyle.OVERLAY
            val baked = isOverlay && overlay.withPhoto && background != null // photo is part of the PNG
            val preview by produceState<Bitmap?>(null, workout, pageStyle, background, overlay, format) {
                value = withContext(Dispatchers.Default) { StoryRenderer.render(ctx, workout, pageStyle, background, 0.5f, overlay, format) }
            }
            val aspect = if (isOverlay) 9f / 16f else StoryRenderer.W / format.height
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .aspectRatio(aspect) // fills width or height, whichever runs out first
                        .graphicsLayer {
                            val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            val s = 1f - 0.08f * off
                            scaleX = s; scaleY = s
                            alpha = 1f - 0.45f * off
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .then(if (isOverlay) Modifier.checkerboard(light = overlay.ink == InkColor.BLACK) else Modifier.background(Raised))
                        .semantics { contentDescription = tr("Vorschau Stil ", "Preview style ") + pageStyle.label },
                    contentAlignment = Alignment.Center
                ) {
                    // the overlay is previewed on your photo if you picked one; the PNG itself stays transparent
                    if (isOverlay && background != null && !baked) {
                        Image(background!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Crossfade(preview, label = "preview") { bmp ->
                        if (bmp != null) Image(
                            bmp.asImageBitmap(), null,
                            Modifier.fillMaxSize().padding(if (isOverlay && !baked) 18.dp else 0.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // style tabs mirror the pager
        TabRow(StoryStyle.entries.map { it.label }, pager.currentPage, accent) { i ->
            scope.launch { pager.animateScrollToPage(i, animationSpec = uiSpring()) }
        }

        Spacer(Modifier.height(6.dp))

        // overlay: sectioned controls like a photo editor; full stories: just the sport. "Photo" only once there is one.
        val sections = Section.entries.filter { it != Section.PHOTO || state.background != null }
        val activeSection = if (style != StoryStyle.OVERLAY) Section.SPORT else if (section in sections) section else Section.LAYOUT
        if (style == StoryStyle.OVERLAY) {
            TabRow(sections.map { it.label }, sections.indexOf(activeSection), accent, small = true) { section = sections[it] }
            Spacer(Modifier.height(6.dp))
        }
        AnimatedContent(activeSection, label = "controls") { sec ->
            ChipRow {
                when (sec) {
                    Section.LOOKS -> {
                        Chip(tr("+ Speichern", "+ Save"), false, accent) { naming = true }
                        looks.presets.forEach { look ->
                            Chip(look.name.orEmpty(), overlay == look.options, accent, onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); deleting = look
                            }) { vm.applyLook(look) }
                        }
                        looks.history.forEach { look ->
                            Chip("↺ " + lookLabel(look), overlay == look.options, accent) { vm.applyLook(look) }
                        }
                        if (looks.presets.isEmpty() && looks.history.isEmpty()) Hint(tr("Gespeicherte PNGs landen hier im Verlauf", "Every saved PNG shows up here"))
                        else if (looks.presets.isNotEmpty()) Hint(tr("Lange drücken löscht", "Long-press to delete"))
                    }
                    Section.LAYOUT -> OverlayLayout.entries.forEach { l ->
                        Chip(l.label, overlay.layout == l, accent) { vm.updateOverlay { it.copy(layout = l) } }
                    }
                    Section.STATS -> {
                        val max = overlay.layout.maxStats
                        val chosen = overlay.stats ?: workout.pickStats(null, max).map { it.key }
                        val shown = workout.pickStats(chosen, max).map { it.key }
                        // "Auto" = let the sport decide (and follow it when the sport changes)
                        Chip("Auto", overlay.stats == null, accent) { vm.updateOverlay { it.copy(stats = null) } }
                        workout.availableStats().forEach { st ->
                            val idx = shown.indexOf(st.key)
                            Chip(st.label, idx >= 0, accent, badge = if (idx >= 0) "${idx + 1}" else null) {
                                vm.updateOverlay { o -> o.copy(stats = toggleStat(shown, st.key, max)) }
                            }
                        }
                    }
                    Section.COLOR -> {
                        InkColor.entries.forEach { ink ->
                            val dot = when (ink) { InkColor.WHITE -> Color.White; InkColor.BLACK -> Color(0xFF14140F); InkColor.SPORT -> accent }
                            Chip(ink.label, overlay.ink == ink, accent, dot = dot) { vm.updateOverlay { it.copy(ink = ink) } }
                        }
                        // black ink needs a lighter, not a darker, background
                        Hint(if (overlay.ink == InkColor.BLACK) tr("AUFHELLEN", "LIGHTEN") else tr("ABDUNKELN", "DARKEN"))
                        DIM_LEVELS.indices.forEach { i ->
                            Chip(dimLabel(i), overlay.dim == i, accent) { vm.updateOverlay { it.copy(dim = i) } }
                        }
                    }
                    Section.EXTRAS -> {
                        val r = workout.route
                        if (r != null) Chip(tr("Route", "Route"), overlay.showRoute, accent) { vm.updateOverlay { it.copy(showRoute = !it.showRoute) } }
                        if (r != null && r.profile.size > 1) Chip(tr("Höhenprofil", "Elevation"), overlay.showProfile, accent) { vm.updateOverlay { it.copy(showProfile = !it.showProfile) } }
                        if (workout.stats.hrCurve.size > 1) Chip(tr("Pulskurve", "HR graph"), overlay.showHeart, accent) { vm.updateOverlay { it.copy(showHeart = !it.showHeart) } }
                        if ((r?.splitSeconds?.size ?: 0) >= 2) Chip(tr("Km-Splits", "Km splits"), overlay.showSplits, accent) { vm.updateOverlay { it.copy(showSplits = !it.showSplits) } }
                        if (workout.stats.hrZoneSeconds.sum() > 0) Chip(tr("Pulszonen", "HR zones"), overlay.showZones, accent) { vm.updateOverlay { it.copy(showZones = !it.showZones) } }
                        if (workout.startMillis != null) Chip(tr("Datum", "Date"), overlay.showDate, accent) { vm.updateOverlay { it.copy(showDate = !it.showDate) } }
                        Chip("Logo", overlay.showBrand, accent) { vm.updateOverlay { it.copy(showBrand = !it.showBrand) } }
                    }
                    Section.PHOTO -> {
                        // every option here only works with the photo baked in, so picking one switches that on
                        fun bake(f: (OverlayOptions) -> OverlayOptions) = vm.updateOverlay { f(it).copy(withPhoto = true) }
                        Chip(tr("Foto ins PNG", "Photo in PNG"), overlay.withPhoto, accent) { vm.updateOverlay { it.copy(withPhoto = !it.withPhoto) } }
                        Hint(tr("POSITION", "POSITION"))
                        PLACE_ORDER.forEach { i -> Chip(placeLabel(i), overlay.withPhoto && overlay.place == i, accent) { bake { it.copy(place = i) } } }
                        Hint(tr("GRÖSSE", "SIZE"))
                        SIZE_ORDER.forEach { i -> Chip(sizeLabel(i), overlay.withPhoto && overlay.size == i, accent) { bake { it.copy(size = i) } } }
                        Hint(if (overlay.ink == InkColor.BLACK) tr("AUFHELLEN", "LIGHTEN") else tr("ABDUNKELN", "DARKEN"))
                        DIM_LEVELS.indices.forEach { i -> Chip(dimLabel(i), overlay.withPhoto && overlay.dim == i, accent) { bake { it.copy(dim = i) } } }
                        // a gradient of nothing is invisible: turning it on with darken off picks "medium"
                        Chip(tr("Verlauf", "Gradient"), overlay.withPhoto && overlay.fade, accent) {
                            bake { it.copy(fade = !(it.withPhoto && it.fade), dim = if (it.dim == 0) 2 else it.dim) }
                        }
                        Chip(tr("Weich", "Blur"), overlay.withPhoto && overlay.soft, accent) { bake { it.copy(soft = !(it.withPhoto && it.soft)) } }
                    }
                    Section.SPORT -> {
                        // full stories: format first (story or feed post), then the sport
                        if (style != StoryStyle.OVERLAY) StoryFormat.entries.forEach { f ->
                            Chip(f.label, format == f, accent) { format = f; prefs.edit().putInt("format", f.ordinal).apply() }
                        }
                        Sport.entries.forEach { sport ->
                            Chip(sport.label, sport == workout.sport, Color(sport.accent), dot = Color(sport.accent)) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                vm.setSport(sport)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // tools
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(
                title = tr("Foto", "Photo"),
                detail = if (state.background != null) tr("Gesetzt", "Set") else tr("Hintergrund wählen", "Pick a background"),
                active = state.background != null,
                modifier = Modifier.weight(1f),
                onRemove = { vm.setBackground(null) },
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
            val route = state.route
            ToolTile(
                title = tr("Route", "Route"),
                detail = route?.let {
                    val d = formatDistance(it.distanceMeters)
                    "${d.value} ${d.unit.lowercase()} · ${formatInt(it.pointCount)} " + tr("Pkt.", "pts")
                } ?: "GPX · TCX · KML",
                active = route != null,
                modifier = Modifier.weight(1f),
                onRemove = { vm.clearRoute() },
                onClick = { routePicker.launch(arrayOf("*/*")) }
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(0.38f)
                    .pressable(enabled = !busy) {
                        scope.launch {
                            busy = true
                            try {
                                val bmp = withContext(Dispatchers.Default) { renderFull() }
                                ShareHelper.share(ctx, ShareHelper.cacheForSharing(ctx, bmp))
                                if (style == StoryStyle.OVERLAY) vm.rememberLook()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, tr("Teilen fehlgeschlagen: ", "Sharing failed: ") + e.message, Toast.LENGTH_LONG).show()
                            } finally {
                                busy = false
                            }
                        }
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Raised2)
                    .heightIn(min = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("Teilen", "Share"), style = Type.button.copy(color = Bone))
            }
            BigButton(tr("PNG speichern", "Save PNG"), Modifier.weight(0.62f), color = accent, busy = busy) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                )
                val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                if (needsPermission) storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else save()
            }
        }
        Text(
            if (style == StoryStyle.OVERLAY && overlay.withPhoto && background != null) tr("Fertige Story 1080 × 1920 mit deinem Foto", "Finished 1080 × 1920 story with your photo")
            else if (style == StoryStyle.OVERLAY) tr("Instagram: Story öffnen → Sticker → Foto → PNG wählen", "Instagram: open a story → Stickers → Photo → pick the PNG")
            else if (format == StoryFormat.FEED) tr("Fertiger Beitrag 1080 × 1350 für den Instagram-Feed", "Finished 1080 × 1350 post for the Instagram feed")
            else tr("Fertige Story 1080 × 1920, als Hintergrund in Instagram", "Finished 1080 × 1920 story, use it as the Instagram background"),
            style = Type.body.copy(fontSize = Type.label.fontSize * 1.1f),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
        )
    }
}

/** Tap order counts. Tapping a chosen stat removes it (never the last one); a new one past the limit pushes out the oldest. */
internal fun toggleStat(current: List<StatKey>, key: StatKey, max: Int): List<StatKey> = when {
    key in current -> if (current.size > 1) current - key else current
    else -> (current + key).takeLast(max)
}

@Composable
private fun TabRow(labels: List<String>, selectedIndex: Int, accent: Color, small: Boolean = false, onSelect: (Int) -> Unit) {
    // centred when it fits, scrolls sideways when seven section tabs don't
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth), horizontalArrangement = Arrangement.Center) {
            labels.forEachIndexed { i, text ->
                val selected = selectedIndex == i
                val color by animateColorAsState(if (selected) Bone else Muted.copy(alpha = 0.45f), uiSpring(), label = "tab")
                Column(
                    Modifier
                        .pressable { onSelect(i) }
                        .heightIn(min = if (small) 36.dp else 44.dp)
                        .padding(horizontal = if (small) 6.dp else 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text.uppercase(), style = Type.label.copy(color = color, fontSize = if (small) Type.label.fontSize * 0.95f else Type.label.fontSize), maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.size(if (small) 4.dp else 5.dp).clip(CircleShape).background(if (selected) accent else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

private fun lookLabel(look: Look): String {
    val day = java.time.Instant.ofEpochMilli(look.savedAt).atZone(java.time.ZoneId.systemDefault())
    return "${look.options.layout.label} · ${look.options.ink.label} · " + java.time.format.DateTimeFormatter.ofPattern(tr("d.M.", "M/d")).format(day)
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Type.body.copy(fontSize = Type.label.fontSize * 1.1f), maxLines = 1)
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Look speichern", "Save look")) },
        text = {
            OutlinedTextField(
                name, { name = it.take(24) },
                singleLine = true,
                placeholder = { Text(tr("z. B. Gravel", "e.g. Gravel")) }
            )
        },
        confirmButton = { TextButton({ onSave(name.trim()) }, enabled = name.isNotBlank()) { Text(tr("Speichern", "Save")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("Abbrechen", "Cancel")) } }
    )
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    selectedColor: Color,
    dot: Color? = null,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(if (selected) selectedColor else Raised2, uiSpring(), label = "chip")
    val fg = if (selected) OnAccent else Bone
    Row(
        Modifier
            .pressable(onLongClick = onLongClick, onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = text + if (selected) tr(", ausgewählt", ", selected") else "" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (badge != null) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(OnAccent), contentAlignment = Alignment.Center) {
                Text(badge, style = Type.label.copy(color = selectedColor, letterSpacing = Type.body.letterSpacing))
            }
            Spacer(Modifier.width(8.dp))
        } else if (dot != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot).drawBehind {
                drawCircle(Bone.copy(alpha = 0.45f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            })
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.button.copy(fontSize = Type.body.fontSize, color = fg))
    }
}

@Composable
private fun TextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.pressable(enabled, onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 10.dp),
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
                Modifier.pressable(onClick = onRemove).size(44.dp).semantics { contentDescription = tr("$title entfernen", "Remove $title") },
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
private fun Modifier.checkerboard(light: Boolean) = drawBehind {
    val tile = 12.dp.toPx()
    drawRect(if (light) Color(0xFFE9E6DE) else Color(0xFF2A2B27))
    var y = 0f; var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else tile
        while (x < size.width) { drawRect(if (light) Color(0xFFD9D5CB) else Color(0xFF353631), Offset(x, y), Size(tile, tile)); x += tile * 2 }
        y += tile; row++
    }
}
