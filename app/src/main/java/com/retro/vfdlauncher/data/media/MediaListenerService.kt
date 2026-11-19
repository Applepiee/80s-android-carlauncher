package com.retro.vfdlauncher.data.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.retro.vfdlauncher.data.nav.NavigationState

/**
 * Two jobs: Android only lets an app read other apps' media sessions if it has an enabled
 * notification listener, and Google Maps' turn-by-turn notification feeds the next-turn panel.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach { NavigationState.onPosted(this, it) }
    }

    override fun onListenerDisconnected() {
        NavigationState.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NavigationState.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NavigationState.onRemoved(sbn)
    }
}
