package com.retro.vfdlauncher.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
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
import com.retro.vfdlauncher.data.update.UpdateState
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
                                .height(76.dp),
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
                                    style = glow(Vfd.Teal.copy(alpha = 0.5f), 18.sp, 1.sp, blur = 0f),
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

/**
 * APPS button: a FAVORITES row (amber) on top, then every launcher app, as VFD tiles.
 * Tap opens; long-press pins an app to FAVORITES or unpins it.
 */
@Composable
fun AppDrawer(
    entries: List<AppEntry>,
    favorites: List<android.content.ComponentName>,
    onToggleFavorite: (AppEntry) -> Unit,
    update: UpdateState = UpdateState.Idle,
    currentVersion: String = "",
    onUpdate: () -> Unit = {},
    loadIcon: suspend (android.content.ComponentName) -> android.graphics.Bitmap?,
    onOpen: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Sheet(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "APPS",
                    Modifier.weight(1f),
                    style = glow(Vfd.Teal, 26.sp, 4.sp, blur = 8f, family = Vfd.Orbitron),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (label, hot) = updateLabel(update, currentVersion)
                    SheetButton(label, if (hot) Vfd.Amber else Vfd.Teal, onUpdate)
                    SheetButton("CLOSE", onClick = onDismiss)
                }
            }
            if (entries.isEmpty()) {
                BasicText("SCANNING…", style = glow(Vfd.Amber, 22.sp, 4.sp, blur = 6f))
            } else {
                val byComponent = entries.associateBy { it.component }
                val pinned = favorites.mapNotNull { byComponent[it] }
                val pinnedSet = pinned.map { it.component }.toSet()
                val rest = entries.filter { it.component !in pinnedSet }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "h-fav", span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel("FAVORITES", Vfd.Amber)
                    }
                    if (pinned.isEmpty()) {
                        item(key = "fav-hint", span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .border(2.dp, Vfd.Amber.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    "LONG-PRESS AN APP BELOW TO PIN IT HERE",
                                    style = glow(Vfd.Amber.copy(alpha = 0.8f), 18.sp, 3.sp, blur = 0f),
                                )
                            }
                        }
                    } else {
                        gridItems(pinned, key = { "fav-" + it.id }) { e ->
                            AppTile(e, Vfd.Amber, loadIcon, { onOpen(e) }, { onToggleFavorite(e) })
                        }
                    }
                    item(key = "h-all", span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel("ALL APPS", Vfd.Teal)
                    }
                    gridItems(rest, key = { it.id }) { e ->
                        AppTile(e, Vfd.Teal, loadIcon, { onOpen(e) }, { onToggleFavorite(e) })
                    }
                }
            }
        }
    }
}

/** Text of the drawer's UPDATE key, and whether it should stand out (amber). */
internal fun updateLabel(st: UpdateState, current: String): Pair<String, Boolean> = when (st) {
    UpdateState.Idle -> "UPDATE · v$current" to false
    UpdateState.Checking -> "CHECKING…" to false
    is UpdateState.UpToDate -> "UP TO DATE · v${st.versionName}" to false
    is UpdateState.Available -> "INSTALL v${st.release.versionName}" to true
    is UpdateState.Downloading -> "DOWNLOADING ${st.percent}%" to true
    UpdateState.Installing -> "INSTALLING…" to true
    UpdateState.NeedsPermission -> "ALLOW INSTALLS, THEN TAP" to true
    is UpdateState.Failed -> (st.reason.ifEmpty { "UPDATE · v$current" }) to st.reason.isNotEmpty()
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    BasicText(text, Modifier.padding(top = 4.dp), style = glow(color, 18.sp, 4.sp, blur = 6f))
}

@Composable
private fun AppTile(
    e: AppEntry,
    color: Color,
    loadIcon: suspend (android.content.ComponentName) -> android.graphics.Bitmap?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val icon by produceState<android.graphics.Bitmap?>(null, e.component) {
        value = loadIcon(e.component)
    }
    val pinned = color == Vfd.Amber
    VfdButton(
        Modifier
            .fillMaxWidth()
            .height(120.dp),
        border = if (pinned) Vfd.Amber else Vfd.Teal.copy(alpha = 0.4f),
        fill = if (pinned) Vfd.Amber.copy(alpha = 0.08f) else Color.Transparent,
        onClickLabel = "Open ${e.label}",
        onLongClick = onLongPress,
        onClick = onOpen,
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(48.dp)) {
                icon?.let {
                    Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(48.dp))
                }
            }
            BasicText(
                e.label.uppercase(),
                style = glow(color, 18.sp, 1.sp, blur = 0f).copy(textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
