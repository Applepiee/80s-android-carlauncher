package com.retro.vfdlauncher.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * CRT tube look drawn over another app (Maps, Android Auto): heavy scanlines, RGB phosphor
 * stripes, a curved-glass bezel with colour fringing at the edges, deep vignette, glass glare and
 * a slow rolling refresh band.
 *
 * An overlay cannot bend the pixels of the app underneath (that would need screen capture), so
 * the curvature is suggested by the bulging bezel opening, the fringes and the vignette.
 * Never touchable; the window is capped at alpha 0.8 so Android 12+ passes touches through.
 */
class CrtView(context: Context) : FrameLayout(context) {

    private val tube = TubeLayer(context)
    private val band = RollBand(context)
    private val dimLayer = View(context)
    private var roll: ObjectAnimator? = null
    private var crtOn = true

    init {
        addView(tube, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(band, LayoutParams(LayoutParams.MATCH_PARENT, 0))
        addView(dimLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /**
     * This window is also the night-mode shade over other apps (Android blocks touches through
     * two stacked see-through windows of one app, so CRT and dimming share one window).
     * [dim] is the wanted darkening, 0..0.8; the window itself is at alpha 0.8.
     */
    fun configure(crt: Boolean, dim: Float) {
        crtOn = crt
        tube.visibility = if (crt) View.VISIBLE else View.GONE
        band.visibility = if (crt) View.VISIBLE else View.GONE
        if (crt && isAttachedToWindow) roll?.start() else roll?.cancel()
        val a = (dim / 0.8f).coerceIn(0f, 1f)
        dimLayer.setBackgroundColor(android.graphics.Color.argb((a * 255).toInt(), 0, 0, 0))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val bandH = (h * 0.22f).roundToInt()
        post { band.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, bandH) }
        roll?.cancel()
        // The refresh band crawls down the tube every 8 s; only its translation animates (cheap).
        roll = ObjectAnimator.ofFloat(band, View.TRANSLATION_Y, -bandH.toFloat(), h.toFloat()).apply {
            duration = 8_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            if (isAttachedToWindow && crtOn) start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (crtOn) roll?.start()
    }

    override fun onDetachedFromWindow() {
        roll?.cancel()
        super.onDetachedFromWindow()
    }

    /** Faint bright band, like the visible refresh roll of a filmed CRT. */
    private class RollBand(context: Context) : View(context) {
        private val paint = Paint()
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            paint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.TRANSPARENT, Color.argb(16, 180, 255, 235), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP,
            )
        }
        override fun onDraw(canvas: Canvas) = canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /** Everything static, drawn once per size. */
    private class TubeLayer(context: Context) : View(context) {

        private val tint = Paint().apply { color = Color.argb(18, 62, 242, 208) }
        private val rgb = Paint()
        private val scan = Paint()
        private val vignette = Paint()
        private val glare = Paint()
        private val bezel = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        private val fringeRed = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val fringeBlue = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val innerShade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val opening = Path()
        private val bezelPath = Path()
        private var k = 1f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            // Scale from the 1280x720 design; at least 1 px per line so it reads on any screen.
            k = minOf(w / 1280f, h / 720f).coerceAtLeast(0.5f)

            // Scanlines: a dark line half the period tall, every ~4 design px.
            val period = max(3, (4f * k).roundToInt())
            val dark = max(1, period / 2)
            val s = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888)
            for (y in 0 until dark) s.setPixel(0, y, Color.argb(120, 0, 0, 0))
            scan.shader = BitmapShader(s, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

            // RGB phosphor triads.
            val col = max(1, k.roundToInt())
            val t = Bitmap.createBitmap(3 * col, 1, Bitmap.Config.ARGB_8888)
            for (x in 0 until 3 * col) {
                t.setPixel(
                    x, 0,
                    when (x / col) {
                        0 -> Color.argb(22, 255, 40, 40)
                        1 -> Color.argb(22, 40, 255, 90)
                        else -> Color.argb(22, 60, 90, 255)
                    },
                )
            }
            rgb.shader = BitmapShader(t, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

            // Elliptical vignette, darker than the home screen canvas: the tube's edges fall off.
            vignette.shader = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(110, 0, 0, 0), Color.argb(215, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 0.85f, 1f),
                Shader.TileMode.CLAMP,
            ).apply {
                setLocalMatrix(Matrix().apply { setScale(w * 0.62f, h * 0.68f); postTranslate(w / 2f, h / 2f) })
            }

            // Curved glass glare, top left.
            glare.shader = RadialGradient(
                w * 0.28f, h * 0.12f, max(w, h) * 0.45f,
                Color.argb(22, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP,
            )

            // Bulging (pincushion) opening: corners rounded, every edge bowed outward.
            val inset = 10f * k
            val r = 90f * k
            val bow = 22f * k
            val l = inset
            val tp = inset
            val rt = w - inset
            val bt = h - inset
            opening.reset()
            opening.moveTo(l + r, tp + bow)
            opening.quadTo(w / 2f, tp - bow, rt - r, tp + bow)
            opening.quadTo(rt, tp, rt - bow, tp + r)
            opening.quadTo(rt + bow, h / 2f, rt - bow, bt - r)
            opening.quadTo(rt, bt, rt - r, bt - bow)
            opening.quadTo(w / 2f, bt + bow, l + r, bt - bow)
            opening.quadTo(l, bt, l + bow, bt - r)
            opening.quadTo(l - bow, h / 2f, l + bow, tp + r)
            opening.quadTo(l, tp, l + r, tp + bow)
            opening.close()

            bezelPath.reset()
            bezelPath.fillType = Path.FillType.EVEN_ODD
            bezelPath.addRect(-1f, -1f, w + 1f, h + 1f, Path.Direction.CW)
            bezelPath.addPath(opening)

            // Chromatic fringes and an inner shadow along the glass edge.
            fringeRed.strokeWidth = 4f * k
            fringeRed.color = Color.argb(70, 255, 50, 50)
            fringeBlue.strokeWidth = 4f * k
            fringeBlue.color = Color.argb(70, 60, 110, 255)
            innerShade.strokeWidth = 36f * k
            innerShade.color = Color.argb(120, 0, 0, 0)
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            canvas.drawRect(0f, 0f, w, h, tint)
            canvas.drawRect(0f, 0f, w, h, rgb)
            canvas.drawRect(0f, 0f, w, h, scan)
            canvas.drawRect(0f, 0f, w, h, vignette)
            canvas.drawRect(0f, 0f, w, h, glare)

            canvas.drawPath(opening, innerShade)
            val shift = 3f * k
            canvas.save(); canvas.translate(shift, 0f); canvas.drawPath(opening, fringeRed); canvas.restore()
            canvas.save(); canvas.translate(-shift, 0f); canvas.drawPath(opening, fringeBlue); canvas.restore()
            canvas.drawPath(bezelPath, bezel)
        }
    }
}
