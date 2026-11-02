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
        _state.value = UiState(stats = s.stats, route = s.route, sportOverride = s.sport, background = bg?.let(Uri::fromFile))
    }

    /** Opens any mix of workout JSON and route files (GPX/KML/KMZ). Later files replace earlier ones of the same kind. */
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
                            WorkoutParser.looksLikeJson(bytes) ->
                                stats = WorkoutParser.parse(bytes.decodeToString(), name).getOrThrow()
                            RouteParser.looksLikeRoute(bytes) ->
                                route = RouteParser.parse(bytes) ?: error("Keine GPS-Punkte gefunden.")
                            else -> error("Unbekanntes Format. Erwartet: JSON, GPX, KML oder KMZ.")
                        }
                    } catch (e: Exception) {
                        problems += "$name: ${e.message ?: "konnte nicht gelesen werden"}"
                    }
                }
            }
            _state.update {
                // A new JSON means a new workout: drop the old route, sport choice and photo.
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
            if (uri != null && copy == null) { _state.update { it.copy(error = "Foto konnte nicht geladen werden.") }; return@launch }
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
        }.getOrNull() ?: uri.lastPathSegment ?: "Datei"

    private fun readBytes(uri: Uri): ByteArray {
        val stream = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: error("konnte nicht geöffnet werden")
        return stream.use {
            val bytes = it.readNBytesCompat(MAX_BYTES + 1)
            require(bytes.size <= MAX_BYTES) { "Datei ist größer als 50 MB." }
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
