package com.retro.vfdlauncher.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retro.vfdlauncher.data.apps.AppEntry
import com.retro.vfdlauncher.data.apps.AppRepository
import com.retro.vfdlauncher.data.apps.Slot
import com.retro.vfdlauncher.data.audio.VolumeRepository
import com.retro.vfdlauncher.data.media.MediaRepository
import com.retro.vfdlauncher.data.media.PresetRepository
import com.retro.vfdlauncher.data.weather.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PickerState(val slot: Slot, val showHidden: Boolean, val entries: List<AppEntry>?)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val volumeRepo = VolumeRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val mediaRepo = MediaRepository(app, viewModelScope, volumeRepo)
    private val presetRepo = PresetRepository(app)
    private val appRepo = AppRepository(app)

    val weather = weatherRepo.weather
    val volume = volumeRepo.volume
    val nowPlaying = mediaRepo.nowPlaying
    val hasMediaAccess = mediaRepo.hasAccess
    val presets = presetRepo.presets

    /** Subtitle per slot: the app it will open, so the user can see what was auto-detected. */
    private val _slotLabels = MutableStateFlow<Map<Slot, String?>>(emptyMap())
    val slotLabels: StateFlow<Map<Slot, String?>> = _slotLabels.asStateFlow()

    private val _picker = MutableStateFlow<PickerState?>(null)
    val picker: StateFlow<PickerState?> = _picker.asStateFlow()

    private val _inspector = MutableStateFlow<String?>(null)
    val inspector: StateFlow<String?> = _inspector.asStateFlow()

    private var weatherJob: Job? = null

    fun onResume() {
        volumeRepo.start()
        mediaRepo.start()
        if (_slotLabels.value.isEmpty()) refreshSlotLabels()
        if (weatherJob?.isActive != true) {
            weatherJob = viewModelScope.launch {
                while (isActive) {
                    weatherRepo.refresh()
                    delay(10 * 60_000L)
                }
            }
        }
    }

    fun onPause() {
        weatherJob?.cancel()
    }

    /** Called after the location permission is granted: fetch for the real location now. */
    fun onLocationGranted() {
        viewModelScope.launch { weatherRepo.refresh(maxAgeMs = 0) }
    }

    override fun onCleared() {
        volumeRepo.stop()
        mediaRepo.stop()
    }

    // ---- Media ----
    fun playPause() = mediaRepo.playPause()
    fun volumeUp() = volumeRepo.up()
    fun volumeDown() = volumeRepo.down()
    fun mediaAccessIntent(): Intent = mediaRepo.accessSettingsIntent()

    fun onPresetTap(index: Int) {
        val np = nowPlaying.value
        val stored = presets.value.getOrNull(index)
        when {
            stored != null -> mediaRepo.seekTo(stored)
            // The radio app publishes its presets as a queue: jump straight to that entry.
            np != null && index < np.queue.size -> mediaRepo.skipToQueueItem(np.queue[index].id)
            // Empty key on a tuner: store what is playing now.
            np?.tuning != null -> presetRepo.store(index, np.tuning)
            else -> if (index < 3) mediaRepo.previous() else mediaRepo.next()
        }
    }

    fun onPresetLongPress(index: Int) {
        val tuning = nowPlaying.value?.tuning
        if (tuning != null) presetRepo.store(index, tuning) else presetRepo.clear(index)
    }

    fun openInspector() {
        _inspector.value = mediaRepo.describeActiveSession()
    }

    fun closeInspector() {
        _inspector.value = null
    }

    // ---- Apps ----
    suspend fun intentFor(slot: Slot): Intent? = appRepo.intentFor(slot)

    fun openPicker(slot: Slot, showHidden: Boolean = slot != Slot.MUSIC) {
        _picker.value = PickerState(slot, showHidden, null)
        viewModelScope.launch {
            val entries = appRepo.allEntries(includeHidden = showHidden, refresh = true)
            if (_picker.value?.slot == slot) _picker.value = PickerState(slot, showHidden, entries)
        }
    }

    fun closePicker() {
        _picker.value = null
    }

    fun assign(slot: Slot, component: ComponentName?) {
        appRepo.assign(slot, component)
        _picker.value = null
        refreshSlotLabels()
    }

    private fun refreshSlotLabels() {
        viewModelScope.launch {
            _slotLabels.value = Slot.entries.associateWith { slot ->
                val assigned = appRepo.assigned(slot)
                if (assigned != null) appRepo.labelFor(assigned)
                else appRepo.autoDetect(slot)?.label
            }
        }
    }
}
