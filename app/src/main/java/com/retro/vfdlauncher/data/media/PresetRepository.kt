package com.retro.vfdlauncher.data.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Six stored stations, like the preset keys on a hi-fi tuner. Long-press a preset to store. */
class PresetRepository(context: Context) {

    private val prefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
    private val _presets = MutableStateFlow(List(COUNT) { load(it) })
    val presets: StateFlow<List<Tuning?>> = _presets.asStateFlow()

    fun store(index: Int, tuning: Tuning) {
        prefs.edit().putString("p$index", "${tuning.band.name}:${tuning.value}").apply()
        _presets.value = _presets.value.toMutableList().also { it[index] = tuning }
    }

    fun clear(index: Int) {
        prefs.edit().remove("p$index").apply()
        _presets.value = _presets.value.toMutableList().also { it[index] = null }
    }

    private fun load(index: Int): Tuning? {
        val raw = prefs.getString("p$index", null) ?: return null
        val band = runCatching { Band.valueOf(raw.substringBefore(':')) }.getOrNull() ?: return null
        val value = raw.substringAfter(':').toFloatOrNull() ?: return null
        return Tuning(band, value)
    }

    companion object { const val COUNT = 6 }
}
