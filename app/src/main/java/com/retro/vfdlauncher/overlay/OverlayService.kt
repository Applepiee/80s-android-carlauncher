package com.retro.vfdlauncher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.retro.vfdlauncher.MainActivity
import com.retro.vfdlauncher.R
import com.retro.vfdlauncher.data.audio.VolumeRepository
import com.retro.vfdlauncher.data.media.MediaRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * While another app is in front (opened from the launcher), shows the floating VFD control bar
 * and, for maps/Android Auto, the CRT effect. Needs "display over other apps".
 * Runs as a foreground service so the system doesn't stop it while the launcher is in the background.
 */
class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var wm: WindowManager
    private lateinit var volume: VolumeRepository
    private lateinit var media: MediaRepository
    private var bar: View? = null
    private var crt: View? = null
    private var barLp: WindowManager.LayoutParams? = null
    private val prefs by lazy { getSharedPreferences("rail", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        wm = getSystemService(WindowManager::class.java)
        volume = VolumeRepository(this).also { it.start() }
        media = MediaRepository(this, lifecycleScope, volume).also { it.start() }
        startInForeground()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        lifecycleScope.launch {
            combine(OverlayBus.launcherVisible, OverlayBus.crtWanted) { home, crtWanted -> home to crtWanted }
                .collect { (home, crtWanted) ->
                    if (!home) media.start() // picks up media access granted since the service started
                    render(showBar = !home, showCrt = !home && crtWanted)
                }
        }
    }

    /** Fold/unfold or rotation: rebuild the bar at the new scale and position. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val b = bar ?: return
        runCatching { wm.removeView(b) }
        bar = createBar().also { runCatching { wm.addView(it, barParams().also { lp -> barLp = lp }) } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        render(showBar = false, showCrt = false)
        media.stop()
        volume.stop()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    private fun render(showBar: Boolean, showCrt: Boolean) {
        if (!Settings.canDrawOverlays(this)) return
        // CRT first so the bar sits above it.
        if (showCrt && crt == null) crt = CrtView(this).also { wm.addView(it, crtParams()) }
        if (!showCrt) crt?.let { runCatching { wm.removeView(it) }; crt = null }
        if (showBar && bar == null) bar = createBar().also { wm.addView(it, barParams().also { lp -> barLp = lp }) }
        if (!showBar) bar?.let { runCatching { wm.removeView(it) }; bar = null }
        if (showBar && showCrt) bar?.let {
            // Re-add so it stays on top of the CRT window that was just added.
            runCatching { wm.removeView(it); wm.addView(it, barParams().also { lp -> barLp = lp }) }
        }
    }

    private fun createBar(): View = FullWidthHost(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        addView(
            ComposeView(this@OverlayService).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    FloatingBar(
                        scale = barScale(),
                        media = media,
                        volume = volume,
                        onHome = ::goHome,
                        onBack = ::goBack,
                        onDrag = ::dragBar,
                        onDragEnd = ::dropBar,
                    )
                }
            },
        )
    }

    /** Current window bounds; the display can change size (fold/unfold, rotation). */
    private fun screenBounds(): Rect =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.currentWindowMetrics.bounds
        else Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)

    /** Navigation bar on the left edge (landscape), so the rail is not drawn under it. The system already keeps the window out of a camera cutout. */
    private fun leftInset(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                .left
        } else 0

    /** Same fit rule as the home screen canvas: design px x min(width/1280, height/720). */
    private fun barScale(): Float {
        val b = screenBounds()
        return minOf(b.width() / 1280f, b.height() / 720f)
    }

    /** BACK on the rail: needs the accessibility service; the first time, open its settings. */
    private fun goBack() {
        if (RailAccessibilityService.back()) return
        android.widget.Toast.makeText(
            this,
            "Turn on \"Roadster Launcher BACK button\" to use BACK",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        runCatching { startActivity(RailAccessibilityService.settingsIntent()) }
    }

    // ---- Moving the rail: drag the grip; on release it snaps to the nearer side edge. ----

    private fun dragBar(dx: Float, dy: Float) {
        val lp = barLp ?: return
        val b = bar ?: return
        val right = lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.END
        lp.x = (lp.x + (if (right) -dx else dx)).toInt()
        val half = (screenBounds().height() - b.height) / 2
        lp.y = (lp.y + dy).toInt().coerceIn(-half, half)
        runCatching { wm.updateViewLayout(b, lp) }
    }

    private fun dropBar() {
        val lp = barLp ?: return
        val b = bar ?: return
        val width = screenBounds().width()
        val right = lp.gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.END
        val centre = if (right) width - lp.x - b.width / 2 else lp.x + b.width / 2
        val toRight = centre > width / 2
        lp.gravity = (if (toRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        lp.x = edgeMargin()
        runCatching { wm.updateViewLayout(b, lp) }
        prefs.edit()
            .putBoolean(KEY_RIGHT, toRight)
            .putFloat(KEY_Y, lp.y / screenBounds().height().toFloat())
            .apply()
    }

    private fun edgeMargin() = (16 * barScale()).toInt() + leftInset()

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    private fun crtParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Android 12+ only passes touches through other apps' overlays at or below 0.8 opacity.
        alpha = 0.8f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun barParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Vertical rail on a side edge (left by default: Maps' own buttons sit top, bottom and
        // right); the user can drag it elsewhere and that is remembered.
        val right = prefs.getBoolean(KEY_RIGHT, false)
        gravity = (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        x = edgeMargin()
        y = (prefs.getFloat(KEY_Y, 0f) * screenBounds().height()).toInt()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Floating controls", NotificationManager.IMPORTANCE_MIN),
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VFD controls active")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val KEY_RIGHT = "right"
        private const val KEY_Y = "y"

        /** Settings: put the rail back on the left, centred. */
        fun resetRailPosition(context: Context) {
            context.getSharedPreferences("rail", Context.MODE_PRIVATE).edit().clear().apply()
        }

        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 1

        /** Start once overlay permission is granted; harmless to call repeatedly. */
        fun ensureRunning(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching { context.startForegroundService(Intent(context, OverlayService::class.java)) }
        }
    }
}

/**
 * Host for the bar's ComposeView. A WRAP_CONTENT overlay window is first measured at the system's
 * dialog width (about 1100 px on the HONOR) and Compose never asks for more, so the bar was cut
 * off after the play button. Measuring the content against the full screen width fixes that.
 */
private class FullWidthHost(context: Context) : android.widget.FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val full = MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, MeasureSpec.AT_MOST)
        super.onMeasure(full, heightMeasureSpec)
    }
}
