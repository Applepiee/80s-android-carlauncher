package com.retro.vfdlauncher.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/**
 * Light CRT look drawn over another app: teal tint, RGB phosphor stripes, scanlines, vignette,
 * rounded black bezel. Pure drawing, never touchable; the window itself is capped at alpha 0.8
 * so Android 12+ lets touches through to the app underneath.
 */
class CrtView(context: Context) : View(context) {

    private val tint = Paint().apply { color = Color.argb(13, 62, 242, 208) }

    private val rgb = Paint().apply {
        val b = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        b.setPixel(0, 0, Color.argb(13, 255, 40, 40))
        b.setPixel(1, 0, Color.argb(13, 40, 255, 90))
        b.setPixel(2, 0, Color.argb(13, 60, 90, 255))
        shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private val scan = Paint().apply {
        val b = Bitmap.createBitmap(1, 3, Bitmap.Config.ARGB_8888)
        b.setPixel(0, 0, Color.argb(71, 0, 0, 0)) // ~0.28
        shader = BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private val vignette = Paint()
    private val reflection = Paint()
    private val bezel = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
    }
    private val bezelPath = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = maxOf(w, h) * 0.62f
        vignette.shader = RadialGradient(
            w / 2f, h / 2f, r,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(140, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        reflection.shader = LinearGradient(
            0f, 0f, w * 0.1f, h * 0.42f,
            Color.argb(15, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP,
        )
        // Black frame with rounded inner corners, like the edge of a tube.
        val k = w / 1280f
        val inset = 6f * k
        val radius = 28f * k
        bezelPath.reset()
        bezelPath.fillType = Path.FillType.EVEN_ODD
        bezelPath.addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW)
        bezelPath.addRoundRect(inset, inset, w - inset, h - inset, radius, radius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, tint)
        canvas.drawRect(0f, 0f, w, h, rgb)
        canvas.drawRect(0f, 0f, w, h, scan)
        canvas.drawRect(0f, 0f, w, h, vignette)
        canvas.drawRect(0f, 0f, w, h, reflection)
        canvas.drawPath(bezelPath, bezel)
    }
}
