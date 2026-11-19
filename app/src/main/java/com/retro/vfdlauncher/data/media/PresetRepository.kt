package com.retro.vfdlauncher.data.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Six stored stations, like the preset keys on a hi-fi tuner. Long-press a preset to store. */
data class Preset(val tuning: Tuning, val name: String?)

class PresetRepository(context: Context) {

    private val prefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)
    private val _presets = MutableStateFlow<List<Preset?>>(List(COUNT) { load(it) })
    val presets: StateFlow<List<Preset?>> = _presets.asStateFlow()

    fun store(index: Int, tuning: Tuning, name: String?) {
        prefs.edit()
            .putString("p$index", "${tuning.band.name}:${tuning.value}")
            .putString("n$index", name)
            .apply()
        _presets.value = _presets.value.toMutableList().also { it[index] = Preset(tuning, name) }
    }

    fun clear(index: Int) {
        prefs.edit().remove("p$index").remove("n$index").apply()
        _presets.value = _presets.value.toMutableList().also { it[index] = null }
    }

    private fun load(index: Int): Preset? {
        val raw = prefs.getString("p$index", null) ?: return null
        val band = runCatching { Band.valueOf(raw.substringBefore(':')) }.getOrNull() ?: return null
        val value = raw.substringAfter(':').toFloatOrNull() ?: return null
        return Preset(Tuning(band, value), prefs.getString("n$index", null))
    }

    companion object { const val COUNT = 6 }
}
