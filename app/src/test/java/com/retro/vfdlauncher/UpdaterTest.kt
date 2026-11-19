package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {

    @Test
    fun versionCodeFromTag() {
        assertEquals(42, Updater.versionCodeOf("v0.1.42"))
        assertEquals(7, Updater.versionCodeOf("0.1.7"))
        assertNull(Updater.versionCodeOf("latest"))
    }

    @Test
    fun picksTheApkAsset() {
        val json = """{"tag_name":"v0.1.42","assets":[
            {"name":"notes.txt","browser_download_url":"https://example.invalid/notes.txt"},
            {"name":"vfd-launcher.apk","browser_download_url":"https://github.com/Applepiee/80s-carlauncher-releases/releases/download/v0.1.42/vfd-launcher.apk"}]}"""
        val r = Updater.parseRelease(json)!!
        assertEquals(42, r.versionCode)
        assertEquals("0.1.42", r.versionName)
        assertEquals("https://github.com/Applepiee/80s-carlauncher-releases/releases/download/v0.1.42/vfd-launcher.apk", r.apkUrl)
    }

    @Test
    fun releaseWithoutApkIsIgnored() = assertNull(Updater.parseRelease("""{"tag_name":"v0.1.3","assets":[]}"""))
}
