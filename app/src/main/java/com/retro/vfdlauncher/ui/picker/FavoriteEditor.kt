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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.phone.Favorite
import com.retro.vfdlauncher.data.phone.RecentCall
import com.retro.vfdlauncher.ui.home.VfdButton
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.glow

private const val NAME_MAX = 10

/**
 * Set up one favourite key on the PHONE panel (canvas "Favorite editor"): name + number side by
 * side with PASTE, a live preview of the key, and the last four calls as one-tap chips. Same base
 * as the playlist editor and kept in the top of the screen so the keyboard never covers it.
 */
@Composable
fun FavoriteEditor(
    index: Int,
    current: Favorite?,
    recents: List<RecentCall>,
    onSave: (Favorite) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(index) { mutableStateOf(current?.name.orEmpty().uppercase().take(NAME_MAX)) }
    var number by remember(index) { mutableStateOf(current?.number.orEmpty()) }
    var error by remember(index) { mutableStateOf<String?>(null) }
    var picked by remember(index) { mutableIntStateOf(-1) }
    val clipboard = LocalClipboardManager.current
    val chips = remember(recents) { recents.distinctBy { it.number }.take(4) }

    fun save() {
        if (number.count { it.isDigit() } < 6) {
            error = "NOT A PHONE NUMBER: IT NEEDS AT LEAST 6 DIGITS."
            return
        }
        onSave(Favorite(name.trim().ifEmpty { number.trim() }.take(NAME_MAX), number.trim()))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 24.dp)
                .width(1040.dp)
                .background(Vfd.Panel, RoundedCornerShape(10.dp))
                .border(2.dp, Vfd.Teal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) { /* swallow */ }
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    "FAVORITE KEY F${index + 1}",
                    Modifier.weight(1f),
                    style = glow(Vfd.Teal, 26.sp, 4.sp, blur = 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("PREVIEW", style = glow(Vfd.Teal.copy(alpha = 0.75f), 18.sp, 2.sp, blur = 0f))
                    Box(
                        Modifier
                            .size(48.dp)
                            .border(2.dp, Vfd.Amber, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            name.trim().take(1).ifEmpty { "+" },
                            style = glow(Vfd.Amber, 22.sp, blur = 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
                        )
                    }
                    BasicText(
                        name.trim().ifEmpty { "ADD" },
                        Modifier.widthIn(min = 110.dp),
                        style = glow(Vfd.Teal, 20.sp, 1.sp, blur = 4f),
                        maxLines = 1,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("NAME", name, { name = it.uppercase().take(NAME_MAX); error = null }, KeyboardType.Text, KeyboardCapitalization.Characters)
                }
                Box(Modifier.weight(1.3f)) {
                    Field("PHONE NUMBER", number, { number = it; error = null; picked = -1 }, KeyboardType.Phone, KeyboardCapitalization.None)
                }
                VfdButton(Modifier.size(128.dp, 64.dp), border = Vfd.Amber.copy(alpha = 0.7f), onClickLabel = "paste number", onClick = {
                    clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { number = it; error = null; picked = -1 }
                }) {
                    BasicText("PASTE", style = glow(Vfd.Amber, 20.sp, 3.sp, blur = 4f))
                }
            }
            BasicText(
                error ?: "TAP A RECENT CALL TO FILL BOTH, OR TYPE OR PASTE A NUMBER.",
                style = glow(if (error != null) Vfd.Amber else Vfd.Teal.copy(alpha = 0.75f), 18.sp, 2.sp, blur = 0f),
            )
            if (chips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText("FROM RECENT CALLS", style = glow(Vfd.Amber, 18.sp, 3.sp, blur = 0f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until 4) {
                            val r = chips.getOrNull(i)
                            Box(Modifier.weight(1f)) {
                                if (r != null) RecentChip(r, picked == i) {
                                    // A call without a contact name only fills the number.
                                    name = if (r.name.any { it.isLetter() }) r.name.uppercase().take(NAME_MAX) else ""
                                    number = r.number
                                    picked = i
                                    error = null
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VfdButton(Modifier.size(180.dp, 64.dp), border = Vfd.Teal, fill = Vfd.Teal, onClickLabel = "save", onClick = ::save) {
                    BasicText("SAVE", style = glow(Vfd.Panel, 20.sp, 3.sp, blur = 0f, family = Vfd.Orbitron, weight = FontWeight.Bold))
                }
                if (current != null) {
                    VfdButton(Modifier.size(200.dp, 64.dp), border = Vfd.Amber.copy(alpha = 0.7f), onClickLabel = "clear key", onClick = onClear) {
                        BasicText("CLEAR KEY", style = glow(Vfd.Amber, 20.sp, 3.sp, blur = 4f))
                    }
                }
                VfdButton(Modifier.size(180.dp, 64.dp), border = Vfd.Teal.copy(alpha = 0.55f), onClickLabel = "cancel", onClick = onDismiss) {
                    BasicText("CANCEL", style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f))
                }
            }
        }
    }
}

@Composable
private fun RecentChip(r: RecentCall, selected: Boolean, onClick: () -> Unit) {
    VfdButton(
        Modifier
            .fillMaxWidth()
            .height(60.dp),
        border = if (selected) Vfd.Teal else Vfd.Teal.copy(alpha = 0.45f),
        fill = if (selected) Vfd.Teal.copy(alpha = 0.18f) else Color.Transparent,
        onClickLabel = "use ${r.name}",
        onClick = onClick,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(r.name.uppercase(), style = glow(Vfd.Teal, 20.sp, 2.sp, blur = 0f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(r.number, style = glow(Vfd.Teal.copy(alpha = 0.75f), 18.sp, blur = 0f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
