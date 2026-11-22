package com.retro.vfdlauncher.data.phone

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A favourite key: name on the key and the number it calls. */
data class Favorite(val name: String, val number: String)

enum class CallKind { INCOMING, OUTGOING, MISSED }

data class RecentCall(val name: String, val number: String, val kind: CallKind, val whenLabel: String)

/**
 * Six favourite contacts (set in the launcher, like the playlists), the recent calls from
 * Android's call log when the phone app writes there, and placing a call.
 */
class PhoneRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("phone", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(List(COUNT) { load(it) })
    val favorites: StateFlow<List<Favorite?>> = _favorites.asStateFlow()

    fun setFavorite(index: Int, f: Favorite?) {
        prefs.edit().apply {
            if (f == null) { remove("name$index"); remove("number$index") } else { putString("name$index", f.name); putString("number$index", f.number) }
        }.apply()
        _favorites.value = _favorites.value.toMutableList().also { it[index] = f }
    }

    private fun load(i: Int): Favorite? {
        val number = prefs.getString("number$i", null) ?: return null
        return Favorite(prefs.getString("name$i", null).orEmpty(), number)
    }

    fun canReadCallLog(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    fun canCall(): Boolean =
        context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /** Latest [limit] calls from the call log; empty if not allowed or the phone app doesn't write there. */
    suspend fun recentCalls(limit: Int = 4): List<RecentCall> = withContext(Dispatchers.IO) {
        if (!canReadCallLog()) return@withContext emptyList()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val out = mutableListOf<RecentCall>()
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(0).orEmpty()
                    val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: number
                    val kind = when (c.getInt(2)) {
                        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallKind.MISSED
                        else -> CallKind.INCOMING
                    }
                    out += RecentCall(name.uppercase(Locale.getDefault()), number, kind, whenLabel(c.getLong(3)))
                }
                out
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** "09:41" today, "YESTERDAY", else the weekday ("FRI"). */
    private fun whenLabel(epochMs: Long): String {
        val zone = ZoneId.systemDefault()
        val t = Instant.ofEpochMilli(epochMs).atZone(zone)
        val today = LocalDate.now(zone)
        return when (t.toLocalDate()) {
            today -> t.format(DateTimeFormatter.ofPattern("HH:mm"))
            today.minusDays(1) -> "YESTERDAY"
            else -> t.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault()).take(3)
        }
    }

    /** Call [number] directly when allowed, else open the dialler with it filled in. */
    fun callIntent(number: String): Intent {
        val uri = Uri.fromParts("tel", number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }, null)
        val action = if (canCall()) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val COUNT = 6
    }
}
