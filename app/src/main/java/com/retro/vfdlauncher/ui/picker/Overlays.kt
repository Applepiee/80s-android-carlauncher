package com.retro.vfdlauncher.ui.picker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.retro.vfdlauncher.data.apps.AppEntry
import com.retro.vfdlauncher.data.apps.Slot
import com.retro.vfdlauncher.ui.home.VfdButton
import com.retro.vfdlauncher.ui.picker.OverlayDefaults.Scrim
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.glow
import com.retro.vfdlauncher.ui.theme.vfdPanel

private object OverlayDefaults {
    val Scrim = Color.Black.copy(alpha = 0.82f)
}

/** Full-screen sheet over the home screen; tapping the dark edge closes it. Drawn inside the scaled canvas. */
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(1040.dp, 620.dp)
                .vfdPanel()
                .clickable(remember { MutableInteractionSource() }, indication = null) { /* swallow */ }
                .padding(24.dp),
        ) { content() }
    }
}

@Composable
private fun SheetButton(text: String, color: Color = Vfd.Teal, onClick: () -> Unit) {
    VfdButton(Modifier.height(56.dp), border = color.copy(alpha = 0.55f), onClick = onClick) {
        BasicText(text, Modifier.padding(horizontal = 20.dp), style = glow(color, 18.sp, 3.sp, blur = 4f))
    }
}

/**
 * App picker for a button. Lists launcher apps first, then (optionally) every exported activity
 * of every installed package, because head units hide their Android Auto and camera apps.
 */
@Composable
fun AppPicker(
    slot: Slot,
    showHidden: Boolean,
    entries: List<AppEntry>?,
    onPick: (AppEntry) -> Unit,
    onReset: () -> Unit,
    onToggleHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "ASSIGN ${slot.label}",
                    Modifier.weight(1f),
                    style = glow(Vfd.Teal, 26.sp, 4.sp, blur = 8f, family = Vfd.Orbitron),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetButton(if (showHidden) "HIDE SYSTEM" else "SHOW HIDDEN", Vfd.Amber, onToggleHidden)
                    SheetButton("AUTO", onClick = onReset)
                    SheetButton("CLOSE", onClick = onDismiss)
                }
            }
            if (entries == null) {
                BasicText("SCANNING PACKAGES…", style = glow(Vfd.Amber, 22.sp, 4.sp, blur = 6f))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(entries, key = { it.id }) { e ->
                        VfdButton(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            border = if (e.inLauncher) Vfd.Teal.copy(alpha = 0.3f) else Vfd.Amber.copy(alpha = 0.3f),
                            onClick = { onPick(e) },
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                            ) {
                                BasicText(
                                    e.label.uppercase(),
                                    style = glow(if (e.inLauncher) Vfd.Teal else Vfd.Amber, 20.sp, 2.sp, blur = 0f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                BasicText(
                                    e.component.flattenToShortString(),
                                    style = glow(Vfd.Teal.copy(alpha = 0.5f), 13.sp, 1.sp, blur = 0f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Raw dump of the media sessions, to learn what ATOTO's radio app exposes. */
@Composable
fun SessionInspector(text: String, onDismiss: () -> Unit) {
    Sheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "MEDIA SESSIONS",
                    Modifier.weight(1f),
                    style = glow(Vfd.Teal, 26.sp, 4.sp, blur = 8f, family = Vfd.Orbitron),
                )
                SheetButton("CLOSE", onClick = onDismiss)
            }
            BasicText(
                text,
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                style = glow(Vfd.Teal, 16.sp, blur = 0f),
            )
        }
    }
}
