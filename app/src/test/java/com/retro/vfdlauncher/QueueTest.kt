package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.media.NowPlaying
import com.retro.vfdlauncher.data.media.QueueEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueTest {

    private fun np(pkg: String, title: String?, activeId: Long, size: Int = 10) = NowPlaying(
        packageName = pkg, appLabel = "App", title = "Song", artist = null, album = null, isPlaying = true,
        tuning = null, isRadio = false, positionMs = 0, positionUpdatedAt = 0, speed = 1f, durationMs = 1,
        queue = (1..size).map { QueueEntry(it.toLong(), "Song $it", null) },
        activeQueueId = activeId, actions = 0, customActions = emptyList(), queueTitle = title,
    )

    @Test
    fun youTubeMusicWindowHasNoTrackCountButNamesNext() {
        val n = np("com.google.android.apps.youtube.music", "Up next", activeId = 1)
        assertNull(n.queuePosition)
        assertEquals("Song 2", n.nextTitle)
    }

    @Test
    fun completeQueueKeepsTrackCount() {
        assertEquals("3/12", np("com.example.player", "Road trip", activeId = 3, size = 12).queuePosition)
    }

    @Test
    fun lastSongHasNoNext() = assertNull(np("com.example.player", "Mix", activeId = 10).nextTitle)
}
