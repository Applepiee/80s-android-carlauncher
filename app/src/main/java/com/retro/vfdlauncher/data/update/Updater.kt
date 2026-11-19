package com.retro.vfdlauncher.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import com.retro.vfdlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A published build: "v0.1.42" with its APK. */
data class Release(val versionCode: Int, val versionName: String, val apkUrl: String)

/** Where the UPDATE button is. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val versionName: String) : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val percent: Int) : UpdateState
    data object Installing : UpdateState
    /** Android needs "install unknown apps" allowed for the launcher first. */
    data object NeedsPermission : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Over-the-air updates over the head unit's own SIM: GitHub Actions publishes every signed build
 * to the public repo [RELEASES_REPO] (APKs only, no source). This checks the latest release,
 * downloads the APK and hands it to Android's installer; the user confirms with one tap
 * (Android always asks for sideloaded apps without root).
 */
class Updater(private val context: Context) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Quiet check at most every [minIntervalMs]; the UPDATE button forces one. */
    suspend fun check(force: Boolean = false, minIntervalMs: Long = 6 * 3_600_000L) {
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST_CHECK, 0L) < minIntervalMs) return
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
        prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        _state.value = result.fold(
            onSuccess = { r ->
                when {
                    r == null -> UpdateState.UpToDate(currentVersion)
                    r.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Available(r)
                    else -> UpdateState.UpToDate(currentVersion)
                }
            },
            onFailure = { UpdateState.Failed(if (force) "NO CONNECTION" else "") },
        )
    }

    /** The release waiting for "install unknown apps" to be allowed. */
    var pending: Release? = null
        private set

    /** Download and install [release]. */
    suspend fun install(release: Release) {
        pending = release
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission
            return
        }
        _state.value = UpdateState.Downloading(release, 0)
        val apk = withContext(Dispatchers.IO) {
            runCatching { download(release) { pct -> _state.value = UpdateState.Downloading(release, pct) } }
        }.getOrElse {
            _state.value = UpdateState.Failed("DOWNLOAD FAILED")
            return
        }
        _state.value = UpdateState.Installing
        withContext(Dispatchers.IO) {
            runCatching { startInstall(apk) }.onFailure { _state.value = UpdateState.Failed("INSTALL FAILED") }
        }
    }

    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun reset() {
        _state.value = UpdateState.Idle
    }

    internal fun onInstallResult(status: Int, message: String?) {
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.UpToDate(currentVersion)
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateState.Idle // user tapped Cancel
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed("SIGNED WITH ANOTHER KEY: UNINSTALL ONCE")
            else -> UpdateState.Failed(message?.uppercase()?.take(40) ?: "INSTALL FAILED")
        }
    }

    private fun fetchLatest(): Release? {
        val conn = URL("https://api.github.com/repos/$RELEASES_REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode == 404) return null // nothing published yet
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun download(r: Release, progress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "vfd-launcher-${r.versionCode}.apk")
        var url = URL(r.apkUrl)
        repeat(5) { // GitHub redirects release downloads to its CDN
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    url = URL(url, conn.getHeaderField("Location"))
                    return@repeat
                }
                check(code == 200) { "HTTP $code" }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            val pct = if (total > 0) (done * 100 / total).toInt() else 0
                            if (pct != last) { last = pct; progress(pct) }
                        }
                    }
                }
                return out
            } finally {
                conn.disconnect()
            }
        }
        error("too many redirects")
    }

    private fun startInstall(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("vfd-launcher.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    companion object {
        /** Public repo that holds only the published APKs. */
        const val RELEASES_REPO = "Applepiee/80s-carlauncher-releases"
        private const val KEY_LAST_CHECK = "lastCheck"

        @Volatile
        var instance: Updater? = null

        /** "v0.1.42" or "0.1.42" -> 42; the last number is the build number. */
        fun versionCodeOf(tag: String): Int? = tag.trim().removePrefix("v").split('.').lastOrNull()?.toIntOrNull()

        /** GitHub's "latest release" JSON -> the APK asset of that release. */
        fun parseRelease(json: String): Release? {
            val o = JSONObject(json)
            val tag = o.optString("tag_name")
            val code = versionCodeOf(tag) ?: return null
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    return Release(code, tag.removePrefix("v"), a.getString("browser_download_url"))
                }
            }
            return null
        }
    }
}

/** Receives the installer's answer: show Android's confirmation screen, or the final result. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        Updater.instance?.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
