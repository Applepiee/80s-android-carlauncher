package com.retro.vfdlauncher.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.ui.home.VfdButton
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.glow

/** What the launcher needs from Android, as last checked (refreshed whenever the launcher resumes). */
data class LauncherStatus(
    val isDefaultHome: Boolean = false,
    val mediaAccess: Boolean = false,
    val overlay: Boolean = false,
    val backButton: Boolean = false,
    val version: String = "",
    /** "Modify system settings" granted: night mode can lower the real backlight. */
    val backlight: Boolean = false,
    /** Black layer strength at night, 0..0.8. */
    val nightDim: Float = 0.6f,
)

/** Actions of the launcher settings sheet. */
class SettingsActions(
    val onMakeDefault: () -> Unit,
    val onMediaAccess: () -> Unit,
    val onOverlay: () -> Unit,
    val onBackButton: () -> Unit,
    val onResetRail: () -> Unit,
    val onBacklight: () -> Unit = {},
    val onNightDim: (delta: Float) -> Unit = {},
    val onNightToggle: () -> Unit = {},
    val onUpdate: () -> Unit,
    val onAndroidSettings: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Launcher settings (the SETTINGS key): make Roadster the default home app, the permissions it
 * needs, the floating rail, updates, and a way through to Android's own settings.
 */
@Composable
fun LauncherSettings(
    status: LauncherStatus,
    updateLabel: String,
    a: SettingsActions,
    night: com.retro.vfdlauncher.data.night.NightState = com.retro.vfdlauncher.data.night.NightState(),
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = a.onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .size(1040.dp, 640.dp)
                .background(Vfd.Panel, RoundedCornerShape(10.dp))
                .border(2.dp, Vfd.Teal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) { /* swallow */ }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "LAUNCHER SETTINGS",
                    Modifier.weight(1f),
                    style = glow(Vfd.Teal, 26.sp, 4.sp, blur = 8f, family = Vfd.Orbitron),
                )
                SettingButton("ANDROID SETTINGS", Vfd.Teal, a.onAndroidSettings)
                Box(Modifier.width(10.dp))
                SettingButton("CLOSE", Vfd.Teal, a.onDismiss)
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingRow(
                    "HOME APP",
                    if (status.isDefaultHome) "ROADSTER IS YOUR DEFAULT LAUNCHER" else "ANOTHER LAUNCHER IS THE DEFAULT",
                    ok = status.isDefaultHome,
                    button = if (status.isDefaultHome) null else "MAKE DEFAULT LAUNCHER",
                    onClick = a.onMakeDefault,
                )
                SettingRow(
                    "MEDIA ACCESS",
                    if (status.mediaAccess) "NOW PLAYING, CONTROLS AND MAPS TURNS WORK" else "NEEDED FOR NOW PLAYING AND MAPS TURNS",
                    ok = status.mediaAccess,
                    button = if (status.mediaAccess) null else "ALLOW",
                    onClick = a.onMediaAccess,
                )
                SettingRow(
                    "FLOATING CONTROLS",
                    if (status.overlay) "SHOWN OVER MAPS AND OTHER APPS" else "NEEDS \"DISPLAY OVER OTHER APPS\"",
                    ok = status.overlay,
                    button = if (status.overlay) null else "ALLOW",
                    onClick = a.onOverlay,
                )
                SettingRow(
                    "BACK BUTTON",
                    if (status.backButton) "BACK ON THE CONTROLS WORKS IN EVERY APP" else "TURN ON \"ROADSTER LAUNCHER BACK BUTTON\"",
                    ok = status.backButton,
                    button = if (status.backButton) null else "TURN ON",
                    onClick = a.onBackButton,
                )
                NightDimRow(status, night, a)
                SettingRow(
                    "NIGHT BACKLIGHT",
                    if (status.backlight) "SCREEN GOES TO ITS LOWEST BRIGHTNESS AT NIGHT" else "ALLOW \"MODIFY SYSTEM SETTINGS\" TO DIM THE BACKLIGHT",
                    ok = status.backlight,
                    button = if (status.backlight) null else "ALLOW",
                    onClick = a.onBacklight,
                )
                SettingRow(
                    "CONTROLS POSITION",
                    "DRAG THE GRIP ON TOP OF THE CONTROLS TO MOVE THEM",
                    ok = null,
                    button = "RESET",
                    onClick = a.onResetRail,
                )
                SettingRow(
                    "VERSION ${status.version}",
                    "UPDATES ARRIVE OVER WI-FI OR THE SIM",
                    ok = null,
                    button = updateLabel,
                    onClick = a.onUpdate,
                )
            }
        }
    }
}

/** NIGHT DIM: strength of the black layer (0-80 %), plus switching night mode on/off now. */
@Composable
private fun NightDimRow(status: LauncherStatus, night: com.retro.vfdlauncher.data.night.NightState, a: SettingsActions) {
    val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
    val detail = buildString {
        append(if (night.isNight) "NIGHT NOW" else "DAY NOW")
        if (night.manual) append(" (MANUAL)")
        night.nextSwitch?.let { append(" · AUTO SWITCH ${it.format(clock)}") }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .border(1.dp, Vfd.Teal.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("NIGHT DIM ${Math.round(status.nightDim * 100)}%", style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f), maxLines = 1)
            BasicText(detail, style = glow(Vfd.Teal.copy(alpha = 0.75f), 18.sp, 1.sp, blur = 0f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SettingButton("−", Vfd.Teal) { a.onNightDim(-0.1f) }
        SettingButton("+", Vfd.Teal) { a.onNightDim(0.1f) }
        SettingButton(if (night.isNight) "DAY NOW" else "NIGHT NOW", Vfd.Amber, a.onNightToggle)
    }
}

@Composable
private fun SettingRow(title: String, detail: String, ok: Boolean?, button: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .border(1.dp, Vfd.Teal.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Status lamp: teal = done, amber = needs you, none = just a setting.
        Box(
            Modifier
                .size(14.dp)
                .background(
                    when (ok) {
                        true -> Vfd.Teal
                        false -> Vfd.Amber
                        null -> Color.Transparent
                    },
                    RoundedCornerShape(7.dp),
                ),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(title, style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f), maxLines = 1)
            BasicText(
                detail,
                style = glow(if (ok == false) Vfd.Amber else Vfd.Teal.copy(alpha = 0.75f), 18.sp, 1.sp, blur = 0f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (button != null) SettingButton(button, if (ok == false) Vfd.Amber else Vfd.Teal, onClick)
    }
}

@Composable
private fun SettingButton(text: String, color: Color, onClick: () -> Unit) {
    VfdButton(Modifier.height(56.dp), border = color.copy(alpha = 0.7f), onClickLabel = text.lowercase(), onClick = onClick) {
        BasicText(text, Modifier.padding(horizontal = 20.dp), style = glow(color, 18.sp, 2.sp, blur = 4f), maxLines = 1)
    }
}
