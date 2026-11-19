package com.retro.vfdlauncher.data.media

import android.service.notification.NotificationListenerService

/**
 * Empty on purpose. Android only lets an app read other apps' media sessions
 * (MediaSessionManager.getActiveSessions) if it has an enabled notification listener.
 */
class MediaListenerService : NotificationListenerService()
