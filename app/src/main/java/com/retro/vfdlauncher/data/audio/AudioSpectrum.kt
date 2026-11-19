package com.retro.vfdlauncher.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * Real spectrum of what the head unit is playing: android.media.audiofx.Visualizer on the
 * output mix (session 0), FFT folded into [BANDS] log-spaced bands with VFD-style fall-off.
 *
 * [levels] is null when there is nothing real to show: no RECORD_AUDIO permission, the
 * Visualizer is unavailable, or it has delivered only silence for 2 s (some head units route
 * the tuner past the mixer). The analyser then falls back to its animation.
 */
class AudioSpectrum(private val context: Context) {

    private val _levels = mutableStateOf<FloatArray?>(null)
    val levels: State<FloatArray?> = _levels

    private var visualizer: Visualizer? = null
    private val smoothed = FloatArray(BANDS)
    private var lastSignal = 0L

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Start capturing (call while the home screen is visible). Safe to call repeatedly. */
    fun start() {
        if (visualizer != null || !hasPermission()) return
        try {
            val v = Visualizer(0)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) = onFft(fft, samplingRate)
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            v.enabled = true
            visualizer = v
            lastSignal = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            // UnsupportedOperationException / RuntimeException on devices without output-mix capture.
            visualizer = null
            _levels.value = null
        }
    }

    fun stop() {
        visualizer?.let { v ->
            runCatching { v.enabled = false }
            runCatching { v.release() }
        }
        visualizer = null
        smoothed.fill(0f)
        _levels.value = null
    }

    private fun onFft(fft: ByteArray, samplingRateMilliHz: Int) {
        val bands = SpectrumMath.bands(fft, samplingRateMilliHz, BANDS)
        var loudest = 0f
        for (i in 0 until BANDS) {
            // Fast attack, slow release, like a VFD meter's peak.
            smoothed[i] = maxOf(bands[i], smoothed[i] * DECAY)
            loudest = maxOf(loudest, bands[i])
        }
        val now = SystemClock.elapsedRealtime()
        if (loudest > SILENCE) lastSignal = now
        _levels.value = if (now - lastSignal < SILENCE_MS) smoothed.copyOf() else null
    }

    companion object {
        const val BANDS = 14
        private const val DECAY = 0.82f
        private const val SILENCE = 0.04f
        private const val SILENCE_MS = 2_000L
    }
}

/** FFT bytes from Visualizer.getFft into 0..1 band levels. Pure, so it is unit-tested. */
object SpectrumMath {
    private const val LOW_HZ = 50f
    private const val HIGH_HZ = 16_000f
    private const val FLOOR_DB = -48f

    /**
     * [fft] is Visualizer's layout: [0] DC, [1] Nyquist, then (re, im) pairs for bins 1..n/2-1,
     * each a signed byte. Bands are log-spaced from 50 Hz to 16 kHz; each takes the loudest bin
     * (livelier than the mean) and maps -48..0 dBFS to 0..1.
     */
    fun bands(fft: ByteArray, samplingRateMilliHz: Int, count: Int): FloatArray {
        val out = FloatArray(count)
        val n = fft.size
        if (n < 4 || samplingRateMilliHz <= 0) return out
        val binHz = samplingRateMilliHz / 1000f / n
        val lastBin = n / 2 - 1
        val ratio = HIGH_HZ / LOW_HZ
        for (b in 0 until count) {
            val lo = LOW_HZ * ratio.pow(b / count.toFloat())
            val hi = LOW_HZ * ratio.pow((b + 1) / count.toFloat())
            var k0 = ceil(lo / binHz).toInt().coerceIn(1, lastBin)
            val k1 = floor(hi / binHz).toInt().coerceIn(1, lastBin)
            if (k1 < k0) k0 = k1 // narrow low bands: use the nearest bin
            var peak = 0f
            for (k in k0..k1) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                peak = maxOf(peak, hypot(re, im))
            }
            val db = 20f * log10(peak / 128f + 1e-5f)
            out[b] = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)
        }
        return out
    }
}
