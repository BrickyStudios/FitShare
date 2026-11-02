package com.fitshare.data

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import com.google.gson.Gson
import com.fitshare.export.OverlayOptions
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class UiState(
    val stats: WorkoutStats? = null,
    val route: Route? = null,
    val sportOverride: Sport? = null,
    val background: Uri? = null,
    val loading: Boolean = false,
    val error: String? = null
) {
    val hasData: Boolean get() = stats != null || route != null
    val workout: Workout? get() = if (hasData) mergeWorkout(stats, route, sportOverride) else null
}

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(UiState())
    private val loadLock = Mutex()
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Android often kills the app while Instagram is open; the session survives in filesDir.
    private val files = application.filesDir
    private val sessionFile = File(files, "session.json")
    private val gson = Gson()

    private val prefs = application.getSharedPreferences("editor", android.content.Context.MODE_PRIVATE)
    private val _overlay = MutableStateFlow(loadOverlay())
    val overlay: StateFlow<OverlayOptions> = _overlay.asStateFlow()

    fun updateOverlay(change: (OverlayOptions) -> OverlayOptions) {
        _overlay.update(change)
        prefs.edit().putString("overlay", gson.toJson(_overlay.value)).apply()
    }

    private fun loadOverlay(): OverlayOptions =
        sanitize(runCatching { gson.fromJson(prefs.getString("overlay", null), OverlayOptions::class.java) }.getOrNull())
            ?: OverlayOptions()

    // Gson skips Kotlin defaults: anything missing or unknown from an older version resets to the default
    @Suppress("SENSELESS_COMPARISON")
    private fun sanitize(o: OverlayOptions?): OverlayOptions? =
        o?.takeIf { it.layout != null && it.ink != null }?.let { it.copy(stats = it.stats?.filterNotNull()) }

    private val _looks = MutableStateFlow(loadLooks())
    /** Own presets and the setups of the last exports, to put on the next workout in one tap. */
    val looks: StateFlow<Looks> = _looks.asStateFlow()

    @Suppress("SENSELESS_COMPARISON")
    private fun loadLooks(): Looks {
        val raw = runCatching { gson.fromJson(prefs.getString("looks", null), Looks::class.java) }.getOrNull() ?: return Looks()
        fun clean(list: List<Look>?) = list.orEmpty().filterNotNull().mapNotNull { l -> sanitize(l.options)?.let { l.copy(options = it) } }
        return Looks(clean(raw.presets).filter { !it.name.isNullOrBlank() }, clean(raw.history))
    }

    private fun updateLooks(change: (Looks) -> Looks) {
        _looks.update(change)
        prefs.edit().putString("looks", gson.toJson(_looks.value)).apply()
    }

    /** Called after every overlay save/share. */
    fun rememberLook() = updateLooks { it.copy(history = it.history.pushed(Look(options = _overlay.value, savedAt = System.currentTimeMillis()))) }
    fun savePreset(name: String) = updateLooks { it.copy(presets = it.presets.withPreset(Look(name.trim(), _overlay.value, System.currentTimeMillis()))) }
    fun deletePreset(look: Look) = updateLooks { it.copy(presets = it.presets - look) }
    fun applyLook(look: Look) = updateOverlay { look.options }

    private class Session(val stats: WorkoutStats?, val route: Route?, val sport: Sport?, val background: String?)

    init {
        restore() // synchronous: a few hundred KB at most, and it avoids flashing the start screen
        viewModelScope.launch {
            state.map { Session(it.stats, it.route, it.sportOverride, it.background?.path) }
                .distinctUntilChanged { a, b -> a.stats === b.stats && a.route === b.route && a.sport == b.sport && a.background == b.background }
                .drop(1)
                .collectLatest { session ->
                    delay(300)
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (session.stats == null && session.route == null) sessionFile.delete()
                            else sessionFile.writeText(gson.toJson(session))
                        }
                    }
                }
        }
    }

    private fun restore() {
        val s = runCatching { gson.fromJson(sessionFile.readText(), Session::class.java) }.getOrNull() ?: return
        val bg = s.background?.let(::File)?.takeIf { it.exists() }
        @Suppress("SENSELESS_COMPARISON") // sessions saved before the elevation profile existed
        val route = s.route?.let { if (it.profile == null) it.copy(profile = emptyList()) else it }
        _state.value = UiState(stats = s.stats, route = route, sportOverride = s.sport, background = bg?.let(Uri::fromFile))
    }

    /** Opens any mix of workout FIT/JSON and route files (GPX/KML/KMZ). Later files replace earlier ones of the same kind. */
    fun open(uris: List<Uri>) {
        if (uris.isEmpty()) return
        // one load at a time, in arrival order: a slow JSON must not overwrite a route that was shared after it
        viewModelScope.launch { loadLock.withLock {
            _state.update { it.copy(loading = true, error = null) }
            val problems = mutableListOf<String>()
            var stats: WorkoutStats? = null
            var route: Route? = null
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val name = displayName(uri)
                    try {
                        val bytes = readBytes(uri)
                        when {
                            FitParser.looksLikeFit(bytes) -> {
                                val fit = FitParser.parse(bytes)
                                stats = fit.stats
                                fit.route?.let { route = it }
                            }
                            WorkoutParser.looksLikeJson(bytes) ->
                                stats = WorkoutParser.parse(bytes.decodeToString(), name).getOrThrow()
                            RouteParser.looksLikeRoute(bytes) ->
                                route = RouteParser.parse(bytes) ?: error(tr("Keine GPS-Punkte gefunden.", "No GPS points found."))
                            else -> error(tr("Unbekanntes Format. Erwartet: FIT, JSON, GPX, KML oder KMZ.", "Unknown format. Expected FIT, JSON, GPX, KML or KMZ."))
                        }
                    } catch (e: Exception) {
                        problems += "$name: ${e.message ?: tr("konnte nicht gelesen werden", "could not be read")}"
                    }
                }
            }
            _state.update {
                // A new FIT/JSON means a new workout: drop the old route, sport choice and photo.
                // A route on its own (e.g. shared later from OpenTracks) is added to the current workout.
                val base = if (stats != null) UiState() else it
                base.copy(
                    stats = stats ?: it.stats,
                    route = route ?: base.route,
                    loading = false,
                    error = problems.takeIf { p -> p.isNotEmpty() }?.joinToString("\n")
                )
            }
        } }
    }

    fun setSport(sport: Sport) = _state.update { it.copy(sportOverride = sport) }
    /** Copies the photo into app storage so it still works after a restart (picker grants are temporary). */
    fun setBackground(uri: Uri?) {
        viewModelScope.launch {
            val copy = uri?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val target = File(files, "background-${System.currentTimeMillis()}")
                        getApplication<Application>().contentResolver.openInputStream(it)!!.use { input ->
                            target.outputStream().use { out -> input.copyTo(out) }
                        }
                        target
                    }.getOrNull()
                }
            }
            if (uri != null && copy == null) { _state.update { it.copy(error = tr("Foto konnte nicht geladen werden.", "Couldn't load the photo.")) }; return@launch }
            withContext(Dispatchers.IO) { deleteBackgrounds(except = copy) }
            _state.update { it.copy(background = copy?.let(Uri::fromFile)) }
        }
    }

    private fun deleteBackgrounds(except: File?) =
        files.listFiles { f -> f.name.startsWith("background-") && f != except }?.forEach { it.delete() }
    fun clearRoute() = _state.update { it.copy(route = null) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun reset() {
        _state.value = UiState()
        viewModelScope.launch(Dispatchers.IO) { deleteBackgrounds(except = null) }
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            getApplication<Application>().contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: tr("Datei", "File")

    private fun readBytes(uri: Uri): ByteArray {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error(tr("konnte nicht geöffnet werden", "could not be opened"))
        return stream.use {
            val bytes = it.readNBytesCompat(MAX_BYTES + 1)
            require(bytes.size <= MAX_BYTES) { tr("Datei ist größer als 50 MB.", "File is larger than 50 MB.") }
            bytes
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object { const val MAX_BYTES = 50 * 1024 * 1024 }
}
