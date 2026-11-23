package com.retro.vfdlauncher.data.netradio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder

/**
 * Radio Browser API client (https://api.radio-browser.info). Free and keyless; per its guidelines
 * we pick a server from the all.api.radio-browser.info DNS list, send a descriptive User-Agent and
 * report a click when a station is played.
 */
class RadioBrowser(private val userAgent: String) {

    @Volatile private var server: String? = null

    /** Search by name, country and genre tag; most-clicked first. */
    suspend fun search(name: String, countryCode: String?, tag: String?, limit: Int = 60): List<RadioStation> =
        withContext(Dispatchers.IO) {
            val q = buildList {
                if (name.isNotBlank()) add("name=" + enc(name.trim()))
                if (countryCode != null) add("countrycode=$countryCode")
                if (tag != null) add("tag=" + enc(tag))
                add("hidebroken=true")
                add("order=clickcount")
                add("reverse=true")
                add("limit=$limit")
            }.joinToString("&")
            RadioJson.parseStations(get("/json/stations/search?$q"))
        }

    /** Tell Radio Browser the station was played (their popularity ranking depends on it). */
    suspend fun click(uuid: String) {
        withContext(Dispatchers.IO) { runCatching { get("/json/url/" + enc(uuid)) } }
    }

    private fun get(path: String): String {
        var last: Exception? = null
        // Current server first; on failure re-resolve once and try the others.
        for (host in candidates()) {
            try {
                val conn = URL("https://$host$path").openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 12_000
                conn.setRequestProperty("User-Agent", userAgent)
                conn.setRequestProperty("Accept", "application/json")
                try {
                    if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    server = host
                    return body
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                last = e
                if (server == host) server = null
            }
        }
        throw last ?: java.io.IOException("No Radio Browser server")
    }

    /** The server that worked last, then (only if needed) the DNS list, then known mirrors. */
    private fun candidates(): Sequence<String> = sequence {
        server?.let { yield(it) }
        val resolved = runCatching {
            InetAddress.getAllByName("all.api.radio-browser.info")
                .mapNotNull { a -> a.canonicalHostName.takeIf { it.endsWith(".api.radio-browser.info") } }
                .distinct()
                .shuffled()
        }.getOrDefault(emptyList())
        yieldAll(resolved)
        yieldAll(listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info"))
    }.distinct().take(4)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
