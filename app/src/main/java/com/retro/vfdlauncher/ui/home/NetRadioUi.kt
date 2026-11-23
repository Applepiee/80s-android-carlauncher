package com.retro.vfdlauncher.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.netradio.NetRadioState
import com.retro.vfdlauncher.data.netradio.NetStatus
import com.retro.vfdlauncher.data.netradio.RadioCountries
import com.retro.vfdlauncher.data.netradio.RadioGenres
import com.retro.vfdlauncher.data.netradio.RadioStation
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.VfdIcons
import com.retro.vfdlauncher.ui.theme.glow

private val NetRed = Color(0xFFFF6A4D)

/** STATIONS picker: what is typed/chosen and what Radio Browser returned. */
data class StationPicker(
    val query: String = "",
    val countryIndex: Int = RadioCountries.DEFAULT_INDEX,
    val genreIndex: Int = 0,
    /** null while searching. */
    val results: List<RadioStation>? = null,
    val error: Boolean = false,
)

/** Everything the RADIO tab shows when it is internet radio (canvas page "Internet radio"). */
data class NetRadioUi(
    val state: NetRadioState = NetRadioState(),
    val presets: List<RadioStation?> = List(6) { null },
    /** STATIONS picker, or null when closed. */
    val picker: StationPicker? = null,
)

/** Actions of the internet radio screen. */
class NetRadioActions(
    val onPreset: (Int) -> Unit = {},
    val onPresetSave: (Int) -> Unit = {},
    val onPlayKey: () -> Unit = {},
    val onPrev: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onStations: () -> Unit = {},
    val onPickerClose: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onCountry: () -> Unit = {},
    val onGenre: (Int) -> Unit = {},
    val onPickStation: (RadioStation) -> Unit = {},
)

@Composable
private fun pulseAlpha(on: Boolean): Float {
    if (!on) return 1f
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        1f, 0.35f,
        infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return a
}

// ---------------------------------------------------------------- big display

/** Main display: label + info, the station name in the big VFD text, the song line and spectrum / buffer. */
@Composable
internal fun NetRadioDisplay(ui: NetRadioUi) {
    val st = ui.state
    val s = st.station
    val presetIdx = s?.let { cur -> ui.presets.indexOfFirst { it?.uuid == cur.uuid } } ?: -1
    val offline = st.status == NetStatus.OFFLINE
    val buffering = st.status == NetStatus.BUFFERING
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                if (presetIdx >= 0) "NET RADIO · PRESET ${presetIdx + 1}" else "NET RADIO",
                style = glow(Vfd.Teal, 22.sp, 4.sp, blur = 6f),
            )
            BasicText(s?.infoShort.orEmpty(), style = glow(Vfd.Amber, 22.sp, 4.sp, blur = 6f), maxLines = 1)
        }
        val name = if (offline) "NO SIGNAL" else s?.displayName ?: "NET RADIO"
        // Ten characters fill the panel at 120 sp; longer names shrink to fit.
        val size = if (name.length <= 10) 120f else (1200f / name.length).coerceAtLeast(52f)
        val color = if (offline) NetRed else Vfd.Teal
        val big = glow(color, size.sp, 2.sp, blur = 22f).copy(lineHeight = 0.9.em)
        Box {
            BasicText("8".repeat(maxOf(10, name.length)), style = big.copy(color = color.copy(alpha = 0.06f), shadow = null), maxLines = 1)
            BasicText(
                name,
                Modifier.alpha(if (buffering) 0.55f * pulseAlpha(true) else pulseAlpha(offline)),
                style = big,
                maxLines = 1,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val line = when (st.status) {
                NetStatus.PLAYING -> st.songLine ?: "LIVE"
                NetStatus.BUFFERING -> "CONNECTING TO STREAM…"
                NetStatus.OFFLINE -> "${s?.displayName ?: "STREAM"} · NO DATA · RETRY IN ${st.retryIn} S"
                NetStatus.STOPPED -> "STOPPED"
                NetStatus.IDLE -> if (s == null) "TAP STATIONS TO FIND ONE" else "STOPPED"
            }
            BasicText(
                line,
                Modifier.weight(1f),
                style = glow(if (offline) NetRed else Vfd.Amber, 34.sp, 4.sp, blur = 10f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (st.status) {
                NetStatus.BUFFERING -> BufferMeter(st.bufferPct, Modifier.width(300.dp))
                NetStatus.OFFLINE -> Unit
                else -> SpectrumAnalyzer(st.status == NetStatus.PLAYING, Modifier.size(300.dp, 48.dp))
            }
        }
    }
}

@Composable
private fun BufferMeter(pct: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            "BUFFER $pct%",
            Modifier.fillMaxWidth(),
            style = glow(Vfd.Amber, 18.sp, 3.sp, blur = 0f).copy(textAlign = TextAlign.End),
        )
        val lit = (pct + 2) / 5
        Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(20) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (i < lit) Vfd.Amber else Vfd.Amber.copy(alpha = 0.12f), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- NET chip (top bar)

/** Data-connection chip: four bars + NET 128K / BUFFERING / NO NET (or the music word). */
@Composable
internal fun NetChip(ui: NetRadioUi, radioTab: Boolean, musicWord: String, modifier: Modifier = Modifier) {
    val status = ui.state.status
    val accent = Vfd.Teal
    val (word, color) = when {
        !radioTab -> musicWord to accent
        status == NetStatus.BUFFERING -> "BUFFERING" to Vfd.Amber
        status == NetStatus.OFFLINE -> "NO NET" to NetRed
        else -> (ui.state.station?.bitrate?.takeIf { it > 0 }?.let { "NET ${it}K" } ?: "NET") to accent
    }
    val dimAmber = Vfd.Amber.copy(alpha = 0.25f)
    val dimRed = NetRed.copy(alpha = 0.25f)
    val bars = when {
        !radioTab || status == NetStatus.PLAYING || status == NetStatus.STOPPED || status == NetStatus.IDLE -> List(4) { accent }
        status == NetStatus.BUFFERING -> listOf(Vfd.Amber, Vfd.Amber, dimAmber, dimAmber)
        else -> List(4) { dimRed }
    }
    val offline = radioTab && status == NetStatus.OFFLINE
    Row(
        modifier
            .height(56.dp)
            .padding(horizontal = 12.dp)
            .alpha(pulseAlpha(radioTab && status == NetStatus.BUFFERING)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(Modifier.size(26.dp, 22.dp)) {
            val u = size.width / 26f
            val heights = listOf(6f, 10f, 15f, 20f)
            heights.forEachIndexed { i, h ->
                drawRoundRect(
                    bars[i],
                    topLeft = Offset((1f + 7f * i) * u, (21f - h) * u),
                    size = Size(4f * u, h * u),
                    cornerRadius = CornerRadius(u, u),
                )
            }
            if (offline) {
                drawLine(NetRed, Offset(14f * u, 2f * u), Offset(22f * u, 10f * u), 2.4f * u, StrokeCap.Round)
                drawLine(NetRed, Offset(22f * u, 2f * u), Offset(14f * u, 10f * u), 2.4f * u, StrokeCap.Round)
            }
        }
        BasicText(word, style = glow(color, 20.sp, 3.sp, blur = 8f), maxLines = 1)
    }
}

// ---------------------------------------------------------------- presets + controls

@Composable
internal fun NetPresetGrid(ui: NetRadioUi, a: NetRadioActions, modifier: Modifier) {
    val cur = ui.state.station?.uuid
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.presets.forEachIndexed { i, st ->
            val on = st != null && st.uuid == cur
            VfdButton(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (on) Modifier.halo(Vfd.Teal.copy(alpha = 0.45f), 14.dp, 0.dp) else Modifier),
                border = if (on) Vfd.Teal else Vfd.Teal.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                fill = if (on) Vfd.Teal.copy(alpha = 0.18f) else Color.Transparent,
                onLongClick = { a.onPresetSave(i) },
                onClickLabel = "Preset ${i + 1}, ${st?.displayName ?: "empty"}. Hold to save the current station here",
                onClick = { a.onPreset(i) },
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicText("P${i + 1}", style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f))
                    BasicText(
                        st?.displayName ?: "— — —",
                        style = glow(Vfd.Teal, 22.sp, 1.sp, blur = if (on) 10f else 4f)
                            .copy(textAlign = TextAlign.Center, lineHeight = 1.05.em),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(
                        st?.genreLabel?.ifEmpty { " " } ?: "HOLD = SAVE",
                        style = glow(Vfd.Amber, 18.sp, 2.sp, blur = 4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** PREV / STOP (RETRY, PLAY) / STATIONS / NEXT. */
@Composable
internal fun NetRadioControls(ui: NetRadioUi, a: NetRadioActions, modifier: Modifier) {
    val status = ui.state.status
    val live = status != NetStatus.OFFLINE
    val playing = status == NetStatus.PLAYING || status == NetStatus.BUFFERING
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CtrlKey(Modifier.weight(1f), "PREV", VfdIcons.Previous, iconFirst = true, label = "Previous station", onClick = a.onPrev)
        CtrlKey(
            Modifier.weight(1.2f),
            when { !live -> "RETRY"; playing -> "STOP"; else -> "PLAY" },
            when { !live -> VfdIcons.Retry; playing -> VfdIcons.Stop; else -> VfdIcons.Play },
            iconFirst = true,
            color = Vfd.Amber, border = Vfd.Amber,
            fill = if (!live) Vfd.Amber.copy(alpha = 0.22f) else Color.Transparent,
            label = when { !live -> "Retry"; playing -> "Stop"; else -> "Play" },
            onClick = a.onPlayKey,
        )
        CtrlKey(
            Modifier.weight(1.2f), "STATIONS", VfdIcons.Search, iconFirst = true,
            border = Vfd.Teal, fill = Vfd.Teal.copy(alpha = 0.08f),
            label = "Find a station", onClick = a.onStations,
        )
        CtrlKey(Modifier.weight(1f), "NEXT", VfdIcons.Next, iconFirst = false, label = "Next station", onClick = a.onNext)
    }
}

@Composable
private fun CtrlKey(
    modifier: Modifier,
    word: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconFirst: Boolean,
    color: Color = Vfd.Teal,
    border: Color = Vfd.Teal.copy(alpha = 0.7f),
    fill: Color = Color.Transparent,
    label: String,
    onClick: () -> Unit,
) {
    VfdButton(
        modifier.fillMaxHeight(),
        border = border,
        fill = fill,
        pressedFill = color.copy(alpha = 0.2f),
        onClickLabel = label,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (iconFirst) GlowIcon(icon, color, 28.dp, glow = false)
            BasicText(word, style = glow(color, 18.sp, 2.sp, blur = 4f))
            if (!iconFirst) GlowIcon(icon, color, 28.dp, glow = false)
        }
    }
}

// ---------------------------------------------------------------- STATIONS picker

/** FIND A STATION: covers the left column (780 × 580). */
@Composable
internal fun StationPickerPanel(p: StationPicker, current: RadioStation?, a: NetRadioActions, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .background(Vfd.Panel, shape)
            .border(2.dp, Vfd.Teal.copy(alpha = 0.5f), shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) { /* swallow */ }
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "FIND A STATION",
                Modifier.weight(1f),
                style = glow(Vfd.Teal, 26.sp, 3.sp, blur = 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
            )
            val n = p.results?.size
            BasicText(
                when {
                    p.error -> "NO CONNECTION"
                    n == null -> "SEARCHING…"
                    else -> "$n ${if (n == 1) "STATION" else "STATIONS"}${if (n > 4) " · SWIPE FOR MORE" else ""}"
                },
                Modifier.padding(end = 16.dp),
                style = glow(Vfd.Amber, 18.sp, 2.sp, blur = 0f),
            )
            VfdButton(Modifier.size(72.dp, 64.dp), border = Vfd.Teal.copy(alpha = 0.7f), onClickLabel = "Close station search", onClick = a.onPickerClose) {
                GlowIcon(VfdIcons.Close, Vfd.Teal, 28.dp, glow = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .border(2.dp, Vfd.Teal.copy(alpha = 0.55f), shape),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.padding(start = 16.dp)) { GlowIcon(VfdIcons.Search, Vfd.Teal.copy(alpha = 0.7f), 24.dp, glow = false) }
                if (p.query.isEmpty()) {
                    BasicText("STATION NAME", Modifier.padding(start = 52.dp), style = glow(Vfd.Teal.copy(alpha = 0.4f), 24.sp, 2.sp, blur = 0f))
                }
                BasicTextField(
                    value = p.query,
                    onValueChange = { a.onQuery(it.uppercase()) },
                    singleLine = true,
                    textStyle = glow(Vfd.Teal, 24.sp, 2.sp, blur = 0f),
                    cursorBrush = SolidColor(Vfd.Teal),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp, end = 16.dp),
                )
            }
            VfdButton(
                Modifier.size(236.dp, 64.dp),
                border = Vfd.Amber.copy(alpha = 0.7f),
                fill = Vfd.Amber.copy(alpha = 0.06f),
                onClickLabel = "Change country",
                onClick = a.onCountry,
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText("COUNTRY", Modifier.alpha(0.8f), style = glow(Vfd.Amber, 18.sp, 2.sp, blur = 0f))
                        BasicText(RadioCountries.list[p.countryIndex].name, style = glow(Vfd.Amber, 22.sp, 2.sp, blur = 4f), maxLines = 1)
                    }
                    GlowIcon(VfdIcons.ChevronRight, Vfd.Amber, 22.dp, glow = false)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioGenres.forEachIndexed { i, g ->
                val on = i == p.genreIndex
                VfdButton(
                    Modifier
                        .weight(1f)
                        .height(56.dp),
                    border = if (on) Vfd.Teal else Vfd.Teal.copy(alpha = 0.45f),
                    fill = if (on) Vfd.Teal else Color.Transparent,
                    onClickLabel = "Genre ${g.label}",
                    onClick = { a.onGenre(i) },
                ) {
                    BasicText(g.label, style = glow(if (on) Vfd.Panel else Vfd.Teal, 18.sp, 2.sp, blur = 0f))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val results = p.results
            when {
                p.error -> Note("NO DATA CONNECTION · TRY AGAIN")
                results == null -> Note("SEARCHING…")
                results.isEmpty() -> Note("NO STATIONS FOUND · TRY ANOTHER GENRE")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.uuid }) { r -> ResultRow(r, r.uuid == current?.uuid) { a.onPickStation(r) } }
                }
            }
        }
        BasicText("TAP TO PLAY · THEN HOLD A PRESET KEY TO SAVE IT THERE", style = glow(Vfd.Teal.copy(alpha = 0.75f), 18.sp, 2.sp, blur = 0f))
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(text, style = glow(Vfd.Amber, 22.sp, 3.sp, blur = 4f))
    }
}

@Composable
private fun ResultRow(r: RadioStation, onAir: Boolean, onClick: () -> Unit) {
    VfdButton(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
        border = if (onAir) Vfd.Teal else Vfd.Teal.copy(alpha = 0.45f),
        fill = if (onAir) Vfd.Teal.copy(alpha = 0.14f) else Color.Transparent,
        onClickLabel = "Play ${r.displayName}",
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BasicText(
                r.displayName,
                Modifier.width(180.dp),
                style = glow(Vfd.Teal, 26.sp, 1.sp, blur = 4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                r.infoLong,
                Modifier.weight(1f),
                style = glow(Vfd.Amber, 18.sp, 2.sp, blur = 0f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onAir) BasicText("ON AIR", style = glow(Vfd.Teal, 18.sp, 2.sp, blur = 4f))
            GlowIcon(VfdIcons.Play, Vfd.Teal, 26.dp, glow = false)
        }
    }
}
