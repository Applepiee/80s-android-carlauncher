package com.retro.vfdlauncher.data.nav

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Next manoeuvre as Google Maps shows it in its turn-by-turn notification. */
data class NextTurn(
    val distance: String,
    val street: String,
    val eta: String?,
    /** Maps' own turn arrow (white on transparent), tinted amber by the UI. */
    val arrow: Bitmap?,
    val packageName: String,
)

/**
 * Filled by [com.retro.vfdlauncher.data.media.MediaListenerService] from navigation
 * notifications. A process-wide singleton because the listener service and the activity
 * are separate components.
 */
object NavigationState {

    val NAV_PACKAGES = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "net.osmand",
        "net.osmand.plus",
    )

    private val _nextTurn = MutableStateFlow<NextTurn?>(null)
    val nextTurn: StateFlow<NextTurn?> = _nextTurn.asStateFlow()

    private var sourceKey: String? = null

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        if (!isNavigation(sbn)) return
        parse(context, sbn)?.let {
            sourceKey = sbn.key
            _nextTurn.value = it
        }
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.key == sourceKey) {
            sourceKey = null
            _nextTurn.value = null
        }
    }

    fun clear() {
        sourceKey = null
        _nextTurn.value = null
    }

    private fun isNavigation(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName !in NAV_PACKAGES) return false
        val n = sbn.notification
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        return n.category == Notification.CATEGORY_NAVIGATION || (ongoing && n.category == null)
    }

    private val timeRegex = Regex("""\b([01]?\d|2[0-3])[:.][0-5]\d(\s?[AaPp][Mm])?\b""")

    private fun parse(context: Context, sbn: StatusBarNotification): NextTurn? {
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (title.isNullOrEmpty()) return null
        // Maps: title "300 m", text "Kerkstraat" (sometimes "towards Kerkstraat"), sub text "18 min · 12 km · 15:17 ETA".
        val eta = listOfNotNull(sub, big, ex.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString())
            .firstNotNullOfOrNull { timeRegex.find(it)?.value }
        val arrow = runCatching {
            val icon: Icon? = sbn.notification.getLargeIcon()
            icon?.loadDrawable(context)?.toBitmap(96, 96)
        }.getOrNull()
        return NextTurn(
            distance = title,
            street = text.orEmpty(),
            eta = eta,
            arrow = arrow,
            packageName = sbn.packageName,
        )
    }
}
