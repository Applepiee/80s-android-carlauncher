package com.retro.vfdlauncher.data.media

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.retro.vfdlauncher.data.nav.NavigationState
import com.retro.vfdlauncher.data.phone.PhoneCalls

/**
 * Two jobs: Android only lets an app read other apps' media sessions if it has an enabled
 * notification listener, and Google Maps' turn-by-turn notification feeds the next-turn panel.
 */
class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        runCatching { activeNotifications }.getOrNull()?.forEach {
            NavigationState.onPosted(this, it)
            PhoneCalls.onPosted(it)
        }
    }

    override fun onListenerDisconnected() {
        NavigationState.clear()
        PhoneCalls.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NavigationState.onPosted(this, sbn)
        PhoneCalls.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NavigationState.onRemoved(sbn)
        PhoneCalls.onRemoved(sbn)
    }
}
