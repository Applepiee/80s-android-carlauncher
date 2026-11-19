package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.media.PlaylistRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistLinkTest {

    @Test
    fun musicShareLink() = assertEquals(
        "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
        PlaylistRepository.parseListId("https://music.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf&si=abc123"),
    )

    @Test
    fun youtubeLinkWithOtherParams() = assertEquals(
        "OLAK5uy_kXyz1234567890",
        PlaylistRepository.parseListId("  https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=OLAK5uy_kXyz1234567890  "),
    )

    @Test
    fun songLinkIsRejected() = assertNull(PlaylistRepository.parseListId("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test
    fun otherSiteIsRejected() = assertNull(PlaylistRepository.parseListId("https://example.com/?list=PLrAXtmErZgOeiKm4sgN"))
}
