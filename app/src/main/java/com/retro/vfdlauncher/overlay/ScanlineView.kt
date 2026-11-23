package com.retro.vfdlauncher.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The look over other apps (canvas v30, MapsOpen): a faint amber tint like the home screen's
 * glow, horizontal scanlines, and at night the black dim layer on top. No bezel, phosphor stripes
 * or rolling band any more.
 *
 * Never touchable. The window runs at [WINDOW_ALPHA] (Android 12+ passes touches only through
 * overlays at or below 0.8), so the paints are divided by it to land on the canvas values.
 */
class ScanlineView(context: Context) : View(context) {

    private val tint = Paint().apply { color = argb(TINT_ALPHA, 0xFF, 0xB3, 0x47) } // #ffb347 at 8 %
    private val scan = Paint()
    private val dim = Paint()
    private var scanlines = true

    /** [on] = tint + scanlines; [dimLevel] = wanted night darkening, 0..0.8. */
    fun configure(on: Boolean, dimLevel: Float) {
        scanlines = on
        dim.color = argb(dimLevel.coerceIn(0f, 0.8f), 0, 0, 0)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // 1 design px of black at 22 % every 3 design px, scaled like the home screen (1280x720 fit).
        val k = minOf(w / 1280f, h / 720f).coerceAtLeast(0.5f)
        val period = max(2, (3f * k).roundToInt())
        val line = max(1, k.roundToInt()).coerceAtMost(period - 1)
        val bmp = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
        for (y in 0 until line) bmp.setPixel(0, y, argb(SCAN_ALPHA, 0, 0, 0))
        scan.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (scanlines) {
            canvas.drawRect(0f, 0f, w, h, tint)
            canvas.drawRect(0f, 0f, w, h, scan)
        }
        canvas.drawRect(0f, 0f, w, h, dim)
    }

    companion object {
        const val WINDOW_ALPHA = 0.8f
        private const val TINT_ALPHA = 0.08f
        private const val SCAN_ALPHA = 0.22f

        /** Colour with [alpha] meant for the screen, compensated for the window's own alpha. */
        private fun argb(alpha: Float, r: Int, g: Int, b: Int) =
            Color.argb(((alpha / WINDOW_ALPHA).coerceIn(0f, 1f) * 255).roundToInt(), r, g, b)
    }
}
