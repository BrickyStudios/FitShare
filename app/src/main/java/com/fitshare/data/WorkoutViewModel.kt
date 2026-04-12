package com.fitshare.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitshare.data.GpxParser
import com.fitshare.data.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WorkoutState {
    object Empty : WorkoutState()
    object Loading : WorkoutState()
    data class Success(val workout: WorkoutData) : WorkoutState()
    data class Error(val message: String) : WorkoutState()
}

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<WorkoutState>(WorkoutState.Empty)
    val state: StateFlow<WorkoutState> = _state.asStateFlow()

    private val _backgroundUri = MutableStateFlow<Uri?>(null)
    val backgroundUri: StateFlow<Uri?> = _backgroundUri.asStateFlow()

    private val _gpsTrack = MutableStateFlow<List<LatLon>?>(null)
    val gpsTrack: StateFlow<List<LatLon>?> = _gpsTrack.asStateFlow()

    fun loadGpxFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val points = GpxParser.parseFromUri(getApplication(), uri)
            _gpsTrack.value = if (points.isEmpty()) null else points
        }
    }

    fun clearGpsTrack() {
        _gpsTrack.value = null
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            _state.value = WorkoutState.Loading
            _gpsTrack.value = null
            try {
                val context = getApplication<Application>()
                val filename = uri.lastPathSegment ?: ""
                val jsonString = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: throw IllegalStateException("Datei konnte nicht gelesen werden")

                val result = WorkoutParser.parse(jsonString, filename)
                result.fold(
                    onSuccess = { _state.value = WorkoutState.Success(it) },
                    onFailure = { _state.value = WorkoutState.Error("Ungültige Datei: ${it.message}") }
                )
            } catch (e: Exception) {
                _state.value = WorkoutState.Error(e.message ?: "Unbekannter Fehler")
            }
        }
    }

    fun loadFromJson(jsonString: String, filename: String = "") {
        viewModelScope.launch {
            _state.value = WorkoutState.Loading
            val result = WorkoutParser.parse(jsonString, filename)
            result.fold(
                onSuccess = { _state.value = WorkoutState.Success(it) },
                onFailure = { _state.value = WorkoutState.Error("Ungültige JSON: ${it.message}") }
            )
        }
    }

    fun setBackgroundUri(uri: Uri?) {
        _backgroundUri.value = uri
    }

    fun reset() {
        _state.value = WorkoutState.Empty
        _backgroundUri.value = null
        _gpsTrack.value = null
    }
}
