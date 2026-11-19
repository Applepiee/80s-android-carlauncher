package com.retro.vfdlauncher.data.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Buttons whose app can be reassigned by long-press. RADIO and MUSIC are the two audio sources. */
enum class Slot(val key: String, val label: String) {
    MAPS("maps", "MAPS"),
    ANDROID_AUTO("aa", "ANDROID AUTO"),
    RADIO("radio", "RADIO"),
    MUSIC("music", "MUSIC"),
    CAMERA("camera", "CAMERA"),
    SETTINGS("settings", "SETTINGS"),
}

data class AppEntry(
    val component: ComponentName,
    val label: String,
    /** False for activities that are not in the normal app drawer (hidden system apps). */
    val inLauncher: Boolean,
    val isSystem: Boolean,
) {
    val id: String get() = component.flattenToString()
}

/**
 * Finds apps to launch. Head units often ship the Android Auto receiver, radio and camera as
 * system apps without a launcher icon, so besides the normal launcher list it also scans the
 * exported activities of every installed package.
 */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("buttons", Context.MODE_PRIVATE)

    private val cache = java.util.concurrent.ConcurrentHashMap<Boolean, List<AppEntry>>()

    /** Cached: scanning every package's activities takes a moment on a head unit. */
    suspend fun allEntries(includeHidden: Boolean, refresh: Boolean = false): List<AppEntry> {
        if (!refresh) cache[includeHidden]?.let { return it }
        return scan(includeHidden).also { cache[includeHidden] = it }
    }

    private suspend fun scan(includeHidden: Boolean): List<AppEntry> = withContext(Dispatchers.IO) {
        val launcher = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_ALL,
        ).map { ri -> entry(ri.activityInfo, inLauncher = true) }

        if (!includeHidden) return@withContext launcher.sortedBy { it.label.lowercase() }

        val seen = launcher.map { it.component }.toHashSet()
        val packages = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            }
        }.getOrDefault(emptyList())
        val hidden = packages
            .filter { it.packageName != context.packageName }
            .flatMap { pkg -> pkg.activities.orEmpty().filter { it.exported && it.enabled }.toList() }
            .map { entry(it, inLauncher = false) }
            .filter { it.component !in seen }

        (launcher + hidden).sortedWith(compareBy({ !it.inLauncher }, { it.label.lowercase() }))
    }

    private fun entry(ai: ActivityInfo, inLauncher: Boolean): AppEntry {
        val appLabel = ai.applicationInfo.loadLabel(pm).toString()
        val actLabel = ai.loadLabel(pm).toString()
        val label = when {
            inLauncher -> actLabel
            actLabel == appLabel -> "$appLabel  (${ai.name.substringAfterLast('.')})"
            else -> "$appLabel · $actLabel"
        }
        return AppEntry(
            component = ComponentName(ai.packageName, ai.name),
            label = label,
            inLauncher = inLauncher,
            isSystem = ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
        )
    }

    // ---- Button assignments ----

    fun assigned(slot: Slot): ComponentName? =
        prefs.getString(slot.key, null)?.let { ComponentName.unflattenFromString(it) }

    fun assign(slot: Slot, component: ComponentName?) {
        prefs.edit().apply {
            if (component == null) remove(slot.key) else putString(slot.key, component.flattenToString())
        }.apply()
    }

    /** Best guess for a slot when nothing is assigned, by package/label keywords. */
    suspend fun autoDetect(slot: Slot): AppEntry? {
        val words = when (slot) {
            Slot.ANDROID_AUTO -> AA_HINTS
            Slot.RADIO -> listOf("radio", "fmradio", ".fm", "tuner")
            Slot.MAPS -> listOf("com.google.android.apps.maps", "waze", "osmand", "maps", "navi")
            Slot.MUSIC -> listOf("com.google.android.apps.youtube.music", "yt music", "spotify", "music", "deezer", "tidal")
            Slot.CAMERA -> listOf("avm", "dvr", "reverse", "backcar", "rearview", "camera")
            Slot.SETTINGS -> return null // handled by the system Settings intent
        }
        val entries = allEntries(includeHidden = slot == Slot.ANDROID_AUTO || slot == Slot.CAMERA || slot == Slot.RADIO)
        fun score(e: AppEntry): Int {
            val hay = (e.component.packageName + " " + e.label).lowercase(Locale.ROOT)
            val idx = words.indexOfFirst { it in hay }
            if (idx < 0) return Int.MAX_VALUE
            return idx * 10 + (if (e.inLauncher) 0 else 1)
        }
        return entries.minByOrNull(::score)?.takeIf { score(it) != Int.MAX_VALUE }
    }

    /** Intent for a slot: the assigned component, or a sensible system default. */
    suspend fun intentFor(slot: Slot): Intent? {
        val component = assigned(slot) ?: autoDetect(slot)?.component
        val intent = when {
            component != null -> Intent(Intent.ACTION_MAIN).setComponent(component)
            slot == Slot.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
            slot == Slot.CAMERA -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            slot == Slot.MUSIC -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
            slot == Slot.MAPS -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)
            else -> null
        }
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    /** Package the slot resolves to, used to match media sessions to the RADIO / MUSIC source. */
    suspend fun packageFor(slot: Slot): String? = (assigned(slot) ?: autoDetect(slot)?.component)?.packageName

    /** App icon for the drawer, loaded off the main thread. */
    suspend fun icon(component: ComponentName, sizePx: Int): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        runCatching { pm.getActivityIcon(component).toBitmap(sizePx, sizePx) }.getOrNull()
    }

    fun launchIntent(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun labelFor(component: ComponentName?): String? = component?.let {
        runCatching { pm.getActivityInfo(it, 0).loadLabel(pm).toString() }.getOrNull()
    }

    companion object {
        /** Package/label fragments seen on head units for the Android Auto receiver, best first. */
        val AA_HINTS = listOf(
            "android auto", "androidauto", "gearhead", "aauto", "zlink", "autolink",
            "carlink", "carplay", "projection", "phonelink",
        )
    }
}
