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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.media.Playlist
import com.retro.vfdlauncher.data.media.PlaylistRepository
import com.retro.vfdlauncher.ui.home.VfdButton
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.glow

/**
 * Set up one PLAYLISTS key: a name and a YouTube Music share link (Share > Copy link in the
 * app, then PASTE here). Sits at the top of the screen so the keyboard doesn't cover it.
 */
@Composable
fun PlaylistEditor(
    index: Int,
    current: Playlist?,
    onSave: (Playlist) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(index) { mutableStateOf(current?.name.orEmpty()) }
    var link by remember(index) { mutableStateOf(current?.url.orEmpty()) }
    var error by remember(index) { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    fun save() {
        val id = PlaylistRepository.parseListId(link)
        if (id == null) {
            error = "NOT A PLAYLIST LINK: IT NEEDS …/playlist?list=…"
            return
        }
        onSave(Playlist(name.trim().ifEmpty { "PLAYLIST ${index + 1}" }, id))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicText(
                "PLAYLIST KEY P${index + 1}",
                style = glow(Vfd.Teal, 26.sp, 4.sp, blur = 8f, family = Vfd.Orbitron),
            )
            Field("NAME", name, { name = it }, KeyboardType.Text, KeyboardCapitalization.Characters)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("YOUTUBE MUSIC LINK", link, { link = it; error = null }, KeyboardType.Uri, KeyboardCapitalization.None)
                }
                EditorButton("PASTE", Vfd.Amber) {
                    clipboard.getText()?.text?.let { link = it.trim(); error = null }
                }
            }
            BasicText(
                error ?: "IN YOUTUBE MUSIC: OPEN THE PLAYLIST › SHARE › COPY LINK, THEN PASTE.",
                style = glow(if (error != null) Vfd.Amber else Vfd.Teal.copy(alpha = 0.7f), 18.sp, 2.sp, blur = 0f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorButton("SAVE", Vfd.Teal, ::save)
                if (current != null) EditorButton("CLEAR KEY", Vfd.Amber, onClear)
                EditorButton("CANCEL", Vfd.Teal, onDismiss)
            }
        }
    }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    type: KeyboardType,
    caps: KeyboardCapitalization,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(label, style = glow(Vfd.Amber, 18.sp, 3.sp, blur = 0f))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = glow(Vfd.Teal, 24.sp, 1.sp, blur = 0f),
            cursorBrush = SolidColor(Vfd.Teal),
            keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = type, imeAction = ImeAction.Done),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(2.dp, Vfd.Teal.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
internal fun EditorButton(text: String, color: Color, onClick: () -> Unit) {
    VfdButton(Modifier.height(64.dp), border = color.copy(alpha = 0.7f), onClick = onClick) {
        BasicText(text, Modifier.padding(horizontal = 24.dp), style = glow(color, 20.sp, 3.sp, blur = 4f))
    }
}
