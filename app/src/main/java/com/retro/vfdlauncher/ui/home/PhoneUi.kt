package com.retro.vfdlauncher.ui.home

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.phone.CallInfo
import com.retro.vfdlauncher.data.phone.CallKind
import com.retro.vfdlauncher.data.phone.CallStage
import com.retro.vfdlauncher.data.phone.Favorite
import com.retro.vfdlauncher.data.phone.RecentCall
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.VfdIcons
import com.retro.vfdlauncher.ui.theme.glow
import com.retro.vfdlauncher.ui.theme.vfdPanel
import kotlinx.coroutines.delay

/** Missed calls and HANG UP, from the canvas. */
internal val CallRed = Color(0xFFFF6A4D)
private val Edge = Vfd.Teal.copy(alpha = 0.7f)

// ---------------------------------------------------------------- PHONE panel (canvas: Call-Phone)

/** RECENT CALLS in place of the media display: 4 rows, missed in red, outgoing in amber. Tap to call. */
@Composable
internal fun RecentCallsPanel(recents: List<RecentCall>?, note: String?, onDial: (String) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .vfdPanel()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("RECENT CALLS", style = glow(Vfd.Teal, 22.sp, 4.sp, blur = 6f))
            BasicText("TAP TO CALL", style = glow(Vfd.Amber, 18.sp, 2.sp, blur = 6f))
        }
        if (recents.isNullOrEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    note ?: "NO RECENT CALLS",
                    style = glow(Vfd.Teal.copy(alpha = 0.7f), 18.sp, 2.sp, blur = 0f).copy(textAlign = TextAlign.Center),
                )
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recents.take(4).forEach { r -> RecentRow(r, onDial, Modifier.weight(1f)) }
                repeat(4 - recents.size.coerceAtMost(4)) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RecentRow(r: RecentCall, onDial: (String) -> Unit, modifier: Modifier) {
    val color = when (r.kind) {
        CallKind.MISSED -> CallRed
        CallKind.OUTGOING -> Vfd.Amber
        CallKind.INCOMING -> Vfd.Teal
    }
    VfdButton(
        modifier.fillMaxWidth(),
        border = Vfd.Teal.copy(alpha = 0.35f),
        borderWidth = 1.dp,
        shape = RoundedCornerShape(8.dp),
        onClickLabel = "Call ${r.name}",
        onClick = { onDial(r.number) },
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GlowIcon(if (r.kind == CallKind.OUTGOING) VfdIcons.CallOut else VfdIcons.CallIn, color, 26.dp, glow = false)
            BasicText(
                r.name,
                Modifier.weight(1f),
                style = glow(if (r.kind == CallKind.MISSED) CallRed else Vfd.Teal, 28.sp, 2.sp, blur = 6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (r.number != r.name) BasicText(r.number, style = glow(Vfd.Teal, 18.sp, 1.sp, blur = 0f), maxLines = 1)
            BasicText(
                r.whenLabel,
                Modifier.width(120.dp),
                style = glow(color, 18.sp, 1.sp, blur = 0f).copy(textAlign = TextAlign.End),
                maxLines = 1,
            )
        }
    }
}

/** Six favourite keys with an amber initial ring; empty keys say ADD (hold any key to edit). */
@Composable
internal fun FavoritesRow(favorites: List<Favorite?>, onCall: (Int) -> Unit, onEdit: (Int) -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        favorites.take(6).forEachIndexed { i, f ->
            val ring = if (f != null) Vfd.Amber else Vfd.Teal.copy(alpha = 0.6f)
            VfdButton(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                border = if (f != null) Vfd.Teal.copy(alpha = 0.6f) else Vfd.Teal.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                onClickLabel = if (f != null) "Call favorite ${f.name}" else "Add a favorite contact",
                onLongClick = { onEdit(i) },
                onClick = { if (f != null) onCall(i) else onEdit(i) },
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .border(2.dp, ring, RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            f?.name?.take(1)?.uppercase() ?: "+",
                            style = glow(ring, 24.sp, blur = 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
                        )
                    }
                    BasicText(
                        f?.name?.uppercase()?.ifBlank { null } ?: if (f != null) f.number else "ADD",
                        style = glow(if (f != null) Vfd.Teal else Vfd.Teal.copy(alpha = 0.75f), 18.sp, 1.sp, blur = 0f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** BACK TO MUSIC / RADIO and KEYPAD & CONTACTS (opens the phone app). */
@Composable
internal fun PhoneBottomRow(mediaWord: String, onBack: () -> Unit, onKeypad: () -> Unit, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VfdButton(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            border = Edge,
            onClickLabel = mediaWord.lowercase(),
            onClick = onBack,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowIcon(VfdIcons.ChevronLeft, Vfd.Teal, 28.dp, glow = false)
                BasicText(mediaWord, style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f))
            }
        }
        VfdButton(
            Modifier
                .weight(1.4f)
                .fillMaxHeight(),
            border = Vfd.Amber,
            fill = Vfd.Amber.copy(alpha = 0.08f),
            onClickLabel = "Open the phone app",
            onClick = onKeypad,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlowIcon(VfdIcons.OpenApp, Vfd.Amber, 28.dp, glow = false)
                BasicText("KEYPAD & CONTACTS", style = glow(Vfd.Amber, 20.sp, 3.sp, blur = 4f))
            }
        }
    }
}

// ---------------------------------------------------------------- call bar (canvas: Call-Ringing / -Active / -Fallback)

/**
 * Replaces the small control row during a call. Ringing: HANG UP + ANSWER (the notification's own
 * buttons). In call / calling: PHONE + HANG UP. Ringing without buttons: OPEN PHONE.
 */
@Composable
internal fun CallBar(call: CallInfo, onAnswer: () -> Unit, onHangUp: () -> Unit, onOpenPhone: () -> Unit, modifier: Modifier) {
    val live = call.stage == CallStage.ACTIVE || call.stage == CallStage.CALLING
    val edge = if (live) Vfd.Teal else Vfd.Amber
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(call.connectedAt) {
        while (call.connectedAt != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val label = when (call.stage) {
        CallStage.ACTIVE -> "IN CALL · " + (call.connectedAt?.let { mmss((now - it) / 1000) } ?: "")
        CallStage.CALLING -> "CALLING…"
        CallStage.RINGING_NO_BUTTONS -> "INCOMING CALL · ANSWER IN PHONE APP"
        CallStage.RINGING -> "INCOMING CALL"
    }
    // Ringing: the handset pulses.
    val pulse = rememberInfiniteTransition(label = "ring")
        .animateFloat(1f, 0.35f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Row(
        modifier
            .halo(edge.copy(alpha = if (live) 0.14f else 0.22f), 10.dp, 8.dp)
            .background(Vfd.Panel, RoundedCornerShape(10.dp))
            .border(2.dp, edge, RoundedCornerShape(10.dp))
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.alpha(if (call.stage == CallStage.ACTIVE) 1f else pulse.value)) {
            GlowIcon(VfdIcons.Ringing, edge, 36.dp, glow = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText(label, style = glow(Vfd.Amber, 18.sp, 2.sp, blur = 6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                BasicText(call.name.uppercase(), style = glow(Vfd.Teal, 30.sp, 2.sp, blur = 10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                call.number?.let { BasicText(it, style = glow(Vfd.Teal, 18.sp, 1.sp, blur = 0f), maxLines = 1) }
            }
        }
        when (call.stage) {
            CallStage.RINGING -> {
                CallKey("HANG UP", VfdIcons.HangUp, 168.dp, CallRed, CallRed.copy(alpha = 0.16f), onHangUp)
                CallKey("ANSWER", VfdIcons.Phone, 188.dp, Vfd.Panel, Vfd.Teal, onAnswer, border = Vfd.Teal)
            }
            CallStage.ACTIVE, CallStage.CALLING -> {
                VfdButton(Modifier.size(140.dp, 68.dp), border = Edge, shape = RoundedCornerShape(12.dp), onClickLabel = "Open the phone app", onClick = onOpenPhone) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowIcon(VfdIcons.OpenApp, Vfd.Teal, 26.dp, glow = false)
                        BasicText("PHONE", style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f))
                    }
                }
                CallKey("HANG UP", VfdIcons.HangUp, 188.dp, CallRed, CallRed.copy(alpha = 0.16f), onHangUp)
            }
            CallStage.RINGING_NO_BUTTONS ->
                CallKey("OPEN PHONE", VfdIcons.OpenApp, 236.dp, Vfd.Amber, Vfd.Amber.copy(alpha = 0.12f), onOpenPhone, border = Vfd.Amber)
        }
    }
}

@Composable
private fun CallKey(
    word: String,
    icon: ImageVector,
    width: Dp,
    color: Color,
    fill: Color,
    onClick: () -> Unit,
    border: Color = color,
) {
    VfdButton(Modifier.size(width, 68.dp), border = border, fill = fill, shape = RoundedCornerShape(12.dp), onClickLabel = word.lowercase(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlowIcon(icon, color, 30.dp, glow = false)
            BasicText(word, style = glow(color, 20.sp, 2.sp, blur = 0f, family = Vfd.Orbitron, weight = FontWeight.Bold))
        }
    }
}

private fun mmss(s: Long): String = "%02d:%02d".format(s / 60, s % 60)
