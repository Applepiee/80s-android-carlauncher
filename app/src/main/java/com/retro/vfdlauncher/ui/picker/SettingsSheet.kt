package com.retro.vfdlauncher.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.night.NightMode
import com.retro.vfdlauncher.data.night.NightState
import com.retro.vfdlauncher.ui.home.GlowIcon
import com.retro.vfdlauncher.ui.home.VfdButton
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.VfdIcons
import com.retro.vfdlauncher.ui.theme.glow
import java.time.format.DateTimeFormatter

/** What the launcher needs from Android, as last checked (refreshed whenever the launcher resumes). */
data class LauncherStatus(
    val isDefaultHome: Boolean = false,
    val mediaAccess: Boolean = false,
    val overlay: Boolean = false,
    val location: Boolean = false,
    val version: String = "",
    /** "Modify system settings" granted: night mode also lowers the real backlight. */
    val backlight: Boolean = false,
    /** Settings > DISPLAY > SCANLINES OVER APPS. */
    val crt: Boolean = true,
    /** Black layer strength at night, 0..0.8. */
    val nightDim: Float = NightMode.DEFAULT_DIM,
)

/** Actions of the launcher settings page. */
class SettingsActions(
    val onMakeDefault: () -> Unit,
    val onMediaAccess: () -> Unit,
    val onOverlay: () -> Unit,
    val onLocation: () -> Unit,
    val onCrt: () -> Unit,
    val onNightDim: (delta: Float) -> Unit,
    val onDayNow: () -> Unit,
    val onNightNow: () -> Unit,
    val onDismiss: () -> Unit,
)

private val PanelShape = RoundedCornerShape(10.dp)
private val RowLine = Vfd.Teal.copy(alpha = 0.15f)

/**
 * LAUNCHER SETTINGS (canvas page "Launcher settings"): a full screen with PERMISSIONS + DISPLAY
 * on the left and NIGHT MODE on the right.
 */
@Composable
fun LauncherSettings(status: LauncherStatus, a: SettingsActions, night: NightState = NightState()) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF0B1110), Color(0xFF050807), Color(0xFF030504))))
            .clickable(remember { MutableInteractionSource() }, indication = null) { /* swallow */ },
    ) {
        Row(
            Modifier
                .offset(40.dp, 24.dp)
                .size(1200.dp, 68.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "LAUNCHER SETTINGS",
                Modifier.weight(1f),
                style = glow(Vfd.Teal, 30.sp, 3.sp, blur = 10f, family = Vfd.Orbitron, weight = FontWeight.Bold),
            )
            VfdButton(
                Modifier.size(180.dp, 64.dp),
                border = Vfd.Amber,
                fill = Vfd.Amber.copy(alpha = 0.1f),
                onClickLabel = "back to home",
                onClick = a.onDismiss,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlowIcon(VfdIcons.ChevronLeft, Vfd.Amber, 26.dp, glow = false)
                    BasicText("HOME", style = glow(Vfd.Amber, 20.sp, 2.sp, blur = 4f, family = Vfd.Orbitron, weight = FontWeight.Bold))
                }
            }
        }

        // Left: permissions + display
        SettingsPanel(Modifier.offset(40.dp, 108.dp)) {
            SectionLabel("PERMISSIONS")
            PermRow("DEFAULT LAUNCHER", status.isDefaultHome, a.onMakeDefault)
            PermRow("NOTIFICATION ACCESS", status.mediaAccess, a.onMediaAccess)
            PermRow("DISPLAY OVER APPS", status.overlay, a.onOverlay)
            PermRow("LOCATION", status.location, a.onLocation)
            SectionLabel("DISPLAY", Modifier.padding(top = 14.dp))
            LampRow(lampOn = status.crt, lamp = Vfd.Teal, line = false) {
                BasicText("SCANLINES OVER APPS", Modifier.weight(1f), style = rowText())
                VfdButton(
                    Modifier.size(130.dp, 52.dp),
                    border = Vfd.Teal,
                    fill = if (status.crt) Vfd.Teal else Color.Transparent,
                    onClickLabel = if (status.crt) "scanlines off" else "scanlines on",
                    onClick = a.onCrt,
                ) {
                    BasicText(if (status.crt) "ON" else "OFF", style = glow(if (status.crt) Vfd.Panel else Vfd.Teal, 20.sp, 3.sp, blur = 0f))
                }
            }
        }

        // Right: night mode
        SettingsPanel(Modifier.offset(660.dp, 108.dp)) {
            SectionLabel("NIGHT MODE")
            val lampColor = if (night.isNight) Vfd.Amber else Vfd.Teal
            LampRow(lampOn = true, lamp = lampColor) {
                BasicText(if (night.isNight) "NIGHT IS ON" else "DAY IS ON", Modifier.weight(1f), style = rowText())
                BasicText(if (night.manual) "MANUAL" else "AUTO", style = glow(Vfd.Amber, 20.sp, 2.sp, blur = 0f))
            }
            LampRow(lampOn = false, lamp = Vfd.Teal) {
                BasicText("NEXT SWITCH", Modifier.weight(1f), style = rowText())
                val next = night.nextSwitch?.let {
                    it.format(DateTimeFormatter.ofPattern("HH:mm")) + if (NightMode.nextIsSunrise()) " SUNRISE" else " SUNSET"
                } ?: if (night.isNight) "07:00" else "19:00"
                BasicText(next, style = glow(Vfd.Amber, 24.sp, 2.sp, blur = 6f))
            }
            LampRow(lampOn = false, lamp = Vfd.Teal, height = 76, gap = 14) {
                BasicText("NIGHT DIM", Modifier.weight(1f), style = rowText())
                DimKey(VfdIcons.Minus, "less night dim") { a.onNightDim(-0.1f) }
                BasicText(
                    "${Math.round(status.nightDim * 100)}%",
                    Modifier.width(78.dp),
                    style = glow(Vfd.Amber, 30.sp, blur = 6f).copy(textAlign = TextAlign.Center),
                )
                DimKey(VfdIcons.Plus, "more night dim") { a.onNightDim(0.1f) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val dayForced = night.manual && !night.isNight
                val nightForced = night.manual && night.isNight
                ForceKey("DAY NOW", VfdIcons.DaySun, Vfd.Teal, dayForced, a.onDayNow, Modifier.weight(1f))
                ForceKey("NIGHT NOW", VfdIcons.Moon, Vfd.Amber, nightForced, a.onNightNow, Modifier.weight(1f))
            }
            BasicText(
                "AUTO SWITCHES AT SUNSET AND SUNRISE, OR 19:00 AND 07:00 WITHOUT WEATHER DATA. " +
                    "THE DIM ALSO DARKENS MAPS, MUSIC AND ANDROID AUTO, UP TO 80%.",
                style = glow(Vfd.Teal.copy(alpha = 0.75f), 18.sp, 1.sp, blur = 0f).copy(lineHeight = 25.sp),
            )
        }
    }
}

@Composable
private fun SettingsPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .size(580.dp, 580.dp)
            .background(Vfd.Panel, PanelShape)
            .border(1.dp, Vfd.Teal.copy(alpha = 0.22f), PanelShape)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = glow(Vfd.Amber, 20.sp, 4.sp, blur = 0f))
}

private fun rowText() = glow(Vfd.Teal, 24.sp, 2.sp, blur = 4f)

/** One 68-high row with the round status lamp on the left and a line underneath. */
@Composable
private fun LampRow(
    lampOn: Boolean,
    lamp: Color,
    line: Boolean = true,
    height: Int = 68,
    gap: Int = 18,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .drawBehind {
                if (line) drawLine(RowLine, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx())
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .then(if (lampOn) Modifier.shadow(6.dp, CircleShape, ambientColor = lamp, spotColor = lamp) else Modifier)
                .background(if (lampOn) lamp else Color.Transparent, CircleShape)
                .border(2.dp, if (lampOn) lamp else Vfd.Teal.copy(alpha = 0.35f), CircleShape),
        )
        content()
    }
}

/** Permission row: lamp lit + "ON" when granted, otherwise an amber GRANT key. */
@Composable
private fun PermRow(label: String, on: Boolean, onGrant: () -> Unit) {
    LampRow(lampOn = on, lamp = Vfd.Teal) {
        BasicText(label, Modifier.weight(1f), style = rowText(), maxLines = 1)
        if (on) {
            BasicText("ON", style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f))
        } else {
            VfdButton(
                Modifier.size(130.dp, 52.dp),
                border = Vfd.Amber,
                fill = Vfd.Amber.copy(alpha = 0.1f),
                onClickLabel = "grant ${label.lowercase()}",
                onClick = onGrant,
            ) {
                BasicText("GRANT", style = glow(Vfd.Amber, 20.sp, 3.sp, blur = 4f))
            }
        }
    }
}

@Composable
private fun DimKey(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    VfdButton(Modifier.size(64.dp, 60.dp), border = Vfd.Teal.copy(alpha = 0.7f), onClickLabel = label, onClick = onClick) {
        GlowIcon(icon, Vfd.Teal, 24.dp, glow = false)
    }
}

/** DAY NOW / NIGHT NOW: outlined, filled while that one is forced. */
@Composable
private fun ForceKey(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    forced: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val fg = if (forced) Vfd.Panel else color
    VfdButton(
        modifier.height(72.dp),
        border = color,
        fill = if (forced) color else Color.Transparent,
        onClickLabel = text.lowercase(),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowIcon(icon, fg, 26.dp, glow = false)
            BasicText(text, style = glow(fg, 20.sp, 2.sp, blur = 0f, family = Vfd.Orbitron, weight = FontWeight.Bold))
        }
    }
}
