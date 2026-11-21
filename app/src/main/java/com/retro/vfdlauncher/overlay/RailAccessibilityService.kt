package com.retro.vfdlauncher.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Only job: let the floating rail's BACK button press "back" in the app in front (Maps,
 * YouTube Music, ...). Android lets no ordinary app do that; an accessibility service the user
 * switches on may (performGlobalAction). It reads nothing: no events, no window content.
 */
class RailAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: RailAccessibilityService? = null

        /** Press back in the app in front. False if the service is not switched on. */
        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false

        /** Whether the user has switched the service on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, RailAccessibilityService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
