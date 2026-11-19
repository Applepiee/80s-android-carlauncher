package com.retro.vfdlauncher.data.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A pinned YouTube Music playlist: the name shown on its key and the playlist id from its share link. */
data class Playlist(val name: String, val listId: String) {
    val url: String get() = "https://music.youtube.com/playlist?list=$listId"
}

/**
 * Six playlist keys for the PLAYLISTS panel, set by pasting YouTube Music share links
 * (music.youtube.com/playlist?list=… or youtube.com/playlist?list=…). Also remembers which one
 * was last started from the launcher, for the header and the PLAYING key.
 */
class PlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    private val _playlists = MutableStateFlow(List(COUNT) { load(it) })
    val playlists: StateFlow<List<Playlist?>> = _playlists.asStateFlow()

    private val _current = MutableStateFlow(prefs.getInt(KEY_CURRENT, -1))
    /** Index of the playlist last started from the launcher, or -1. */
    val current: StateFlow<Int> = _current.asStateFlow()

    fun set(index: Int, playlist: Playlist?) {
        prefs.edit().apply {
            if (playlist == null) {
                remove("name$index"); remove("list$index")
            } else {
                putString("name$index", playlist.name); putString("list$index", playlist.listId)
            }
        }.apply()
        _playlists.value = _playlists.value.toMutableList().also { it[index] = playlist }
        if (playlist == null && _current.value == index) markPlaying(-1)
    }

    fun markPlaying(index: Int) {
        prefs.edit().putInt(KEY_CURRENT, index).apply()
        _current.value = index
    }

    private fun load(index: Int): Playlist? {
        val id = prefs.getString("list$index", null) ?: return null
        return Playlist(prefs.getString("name$index", null).orEmpty(), id)
    }

    companion object {
        const val COUNT = 6
        private const val KEY_CURRENT = "current"
        const val YT_MUSIC = "com.google.android.apps.youtube.music"

        private val listParam = Regex("""[?&]list=([A-Za-z0-9_-]{10,})""")

        /** The playlist id from a pasted share link, or null if it isn't a playlist link. */
        fun parseListId(link: String): String? {
            val text = link.trim()
            if (!text.contains("youtube.com", ignoreCase = true) && !text.contains("youtu.be", ignoreCase = true)) return null
            return listParam.find(text)?.groupValues?.get(1)
        }

        /** Opens the playlist in YouTube Music (the app's own deep link). */
        fun openIntent(p: Playlist, inYtMusic: Boolean = true): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(p.url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { if (inYtMusic) setPackage(YT_MUSIC) }
    }
}
