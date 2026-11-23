package com.retro.vfdlauncher.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.retro.vfdlauncher.R
import com.retro.vfdlauncher.data.night.NightMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * While another app is in front (opened from the launcher), draws the scanline overlay over maps
 * and Android Auto, and the night-mode dim layer over every app. Needs "display over other apps".
 * Runs as a foreground service so the system doesn't stop it while the launcher is in the background.
 *
 * There are no on-screen controls over other apps any more: the head unit's own buttons do that.
 */
class OverlayService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var wm: WindowManager
    private var shade: ScanlineView? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        wm = getSystemService(WindowManager::class.java)
        startInForeground()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        lifecycleScope.launch {
            OverlayBus.loadCrtEnabled(this@OverlayService)
            combine(OverlayBus.launcherVisible, OverlayBus.crtWanted, OverlayBus.crtEnabled, NightMode.state) { home, wanted, on, night ->
                Triple(home, wanted && on, night.dim)
            }.collect { (home, scanlines, dim) ->
                render(scanlines = !home && scanlines, dim = if (home) 0f else dim)
            }
        }
        // Night mode also flips while the launcher is in the background (sunset during a drive).
        lifecycleScope.launch {
            while (true) {
                NightMode.update(this@OverlayService)
                kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L + 300L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        render(scanlines = false, dim = 0f)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    /** One window for the scanlines and the night dimming (two stacked see-through windows of one app block touches). */
    private fun render(scanlines: Boolean, dim: Float) {
        if (!Settings.canDrawOverlays(this)) return
        val show = scanlines || dim > 0f
        shade?.configure(scanlines, dim)
        if (show && shade == null) {
            shade = ScanlineView(this).also {
                it.configure(scanlines, dim)
                runCatching { wm.addView(it, shadeParams()) }
            }
        }
        if (!show) shade?.let { runCatching { wm.removeView(it) }; shade = null }
    }

    private fun shadeParams() = WindowManager.LayoutParams(
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
        alpha = ScanlineView.WINDOW_ALPHA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Scanlines and night dimming", NotificationManager.IMPORTANCE_MIN),
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Roadster Launcher overlay active")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 1

        /** Start once overlay permission is granted; harmless to call repeatedly. */
        fun ensureRunning(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            runCatching { context.startForegroundService(Intent(context, OverlayService::class.java)) }
        }
    }
}
