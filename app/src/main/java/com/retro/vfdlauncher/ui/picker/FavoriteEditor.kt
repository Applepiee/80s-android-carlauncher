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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.phone.Favorite
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.glow

/**
 * Set up one favourite key on the PHONE panel: a name and a number. Same look as the playlist
 * editor, at the top of the screen so the keyboard doesn't cover it.
 */
@Composable
fun FavoriteEditor(
    index: Int,
    current: Favorite?,
    onSave: (Favorite) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(index) { mutableStateOf(current?.name.orEmpty()) }
    var number by remember(index) { mutableStateOf(current?.number.orEmpty()) }
    var error by remember(index) { mutableStateOf<String?>(null) }

    fun save() {
        val digits = number.count { it.isDigit() }
        if (digits < 3) {
            error = "ENTER A PHONE NUMBER"
            return
        }
        onSave(Favorite(name.trim().ifEmpty { number.trim() }, number.trim()))
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
            BasicText("FAVORITE ${index + 1}", style = glow(Vfd.Teal, 26.sp, 4.sp, blur = 8f, family = Vfd.Orbitron))
            Field("NAME", name, { name = it }, KeyboardType.Text, KeyboardCapitalization.Characters)
            Field("PHONE NUMBER", number, { number = it; error = null }, KeyboardType.Phone, KeyboardCapitalization.None)
            BasicText(
                error ?: "TAP THE KEY TO CALL, HOLD IT TO CHANGE IT.",
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
