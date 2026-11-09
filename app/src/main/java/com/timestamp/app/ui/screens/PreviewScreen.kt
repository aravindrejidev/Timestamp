package com.timestamp.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.data.LyricLine
import com.timestamp.app.format.PreviewReport
import com.timestamp.app.format.TimeFmt
import com.timestamp.app.format.currentLineIndex
import com.timestamp.app.ui.components.GhostButton
import com.timestamp.app.ui.components.TsDialog
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.util.FileUtils
import com.timestamp.app.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ------------------------------------------------------------------ checks (shown in a dialog)

private data class CheckItem(val level: Int, val text: String) // 0 ok, 1 warning, 2 problem

private fun buildChecks(r: PreviewReport, projectLines: Int): List<CheckItem> {
    val out = ArrayList<CheckItem>()
    if (r.synced == 0) {
        out.add(CheckItem(2, "No timestamps found in this file."))
        return out
    }
    out.add(
        CheckItem(
            0,
            "${r.synced} lines with timestamps" +
                if (r.withWords > 0) ", ${r.withWords} with word timing" else ""
        )
    )
    val missing = projectLines - r.synced
    if (missing > 0) {
        out.add(CheckItem(1, "$missing project lines are not in this file (not synced yet)."))
    } else {
        out.add(CheckItem(0, "Every project line is in the file."))
    }
    out.add(
        if (r.outOfOrder == 0) CheckItem(0, "Timestamps are in order.")
        else CheckItem(1, "${r.outOfOrder} lines are out of order in the file.")
    )
    out.add(
        if (r.overlaps == 0) CheckItem(0, "No overlapping lines.")
        else CheckItem(1, "${r.overlaps} lines overlap the next line.")
    )
    if (r.beyondAudio > 0) out.add(CheckItem(2, "${r.beyondAudio} lines start after the audio ends."))
    if (r.longGaps > 0) out.add(CheckItem(1, "${r.longGaps} gaps longer than 15 s (instrumental?)."))
    if (r.unsynced > 0) out.add(CheckItem(1, "${r.unsynced} lines in the file have no timestamp."))
    return out
}

private fun levelIcon(level: Int): ImageVector = when (level) {
    0 -> Icons.Rounded.CheckCircle
    1 -> Icons.Rounded.Warning
    else -> Icons.Rounded.ErrorOutline
}

private fun levelColor(level: Int): Color = when (level) {
    0 -> TsColors.Neon
    1 -> TsColors.Amber
    else -> TsColors.Danger
}

// ------------------------------------------------------------------ lyric entries

private val Dim = Color.White.copy(alpha = 0.38f)

private sealed interface PreviewEntry {
    val key: String

    data class Lyric(val index: Int, val line: LyricLine) : PreviewEntry {
        override val key: String get() = "l${line.id}"
    }

    /** Intro / instrumental gap, shown as three dots that fill up. */
    data class Break(val start: Long, val end: Long) : PreviewEntry {
        override val key: String get() = "b$start"
    }
}

private fun buildEntries(lines: List<LyricLine>): List<PreviewEntry> {
    val out = ArrayList<PreviewEntry>()
    lines.forEachIndexed { i, l ->
        if (i == 0) {
            if (l.startMs >= 3000L) out.add(PreviewEntry.Break(0L, l.startMs))
        } else {
            val prev = lines[i - 1]
            if (prev.endMs > prev.startMs && l.startMs - prev.endMs >= 5000L) {
                out.add(PreviewEntry.Break(prev.endMs, l.startMs))
            }
        }
        out.add(PreviewEntry.Lyric(i, l))
    }
    return out
}

private fun lyricText(line: LyricLine, focused: Boolean, pos: Long, focusAmt: Float): AnnotatedString =
    buildAnnotatedString {
        if (line.words.isNotEmpty()) {
            line.words.forEachIndexed { i, w ->
                val f = if (focused) {
                    val dur = (w.endMs - w.startMs).coerceAtLeast(1L)
                    when {
                        pos >= w.endMs -> 1f
                        pos <= w.startMs -> 0f
                        else -> (pos - w.startMs).toFloat() / dur
                    }
                } else {
                    focusAmt
                }
                withStyle(SpanStyle(color = lerp(Dim, Color.White, f))) { append(w.text) }
                if (i < line.words.lastIndex) append(" ")
            }
        } else {
            withStyle(SpanStyle(color = lerp(Dim, Color.White, focusAmt))) { append(line.text) }
        }
    }

// ------------------------------------------------------------------ screen

@Composable
fun PreviewScreen(vm: EditorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val projectLines by vm.lines.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val cover by vm.cover.collectAsStateWithLifecycle()
    val coverTint by vm.coverTint.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val positionState = vm.positionMs.collectAsStateWithLifecycle()
    var checksOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { FileUtils.readText(context, uri) }
                if (text == null) {
                    Toast.makeText(context, "Could not read that file", Toast.LENGTH_SHORT).show()
                } else {
                    vm.setPreview(text, FileUtils.displayName(context, uri) + " (opened file)")
                }
            }
        }
    }
    val pickFile = {
        openPicker.launch(arrayOf("text/*", "application/octet-stream", "application/xml", "application/ttml+xml"))
    }

    DisposableEffect(Unit) { onDispose { vm.pause() } }

    val p = preview
    val tint = coverTint?.let { lerp(it, Color.Black, 0.45f) } ?: Color(0xFF2A3A33)
    val background = Brush.verticalGradient(listOf(tint, lerp(tint, Color.Black, 0.72f), Color.Black))

    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // drag-handle style close control
            Box(
                Modifier.fillMaxWidth().height(32.dp).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(40.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.45f))
                )
            }

            // header: cover, title, artist, buttons
            val checks = remember(p, projectLines.size) {
                if (p == null) emptyList() else buildChecks(p.report, projectLines.size)
            }
            val worst = checks.maxOfOrNull { it.level } ?: 0
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(cover)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        meta?.title ?: "Preview",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        meta?.artist?.ifBlank { null } ?: "Unknown artist",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (p != null) {
                    RoundButton(levelIcon(worst), levelColor(worst), "Checks") { checksOpen = true }
                    Spacer(Modifier.width(10.dp))
                }
                Box {
                    RoundButton(Icons.Rounded.MoreHoriz, Color.White, "More") { menuOpen = true }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Open saved file", color = TsColors.TextPrimary) },
                            leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, tint = TsColors.TextSecondary) },
                            onClick = {
                                menuOpen = false
                                pickFile()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Speed: ${if (speed == 1f) "1" else speed.toString()}x",
                                    color = TsColors.TextPrimary
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Speed, null, tint = TsColors.TextSecondary) },
                            onClick = {
                                menuOpen = false
                                vm.cycleSpeed()
                            }
                        )
                    }
                }
            }

            if (p == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nothing to preview yet.",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        GhostButton("Open a saved file", { pickFile() }, icon = Icons.Rounded.FolderOpen)
                    }
                }
            } else {
                val entries = remember(p) { buildEntries(p.lines) }
                LyricsList(
                    entries = entries,
                    lines = p.lines,
                    posState = positionState,
                    onSeek = { vm.seekTo(it) },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                PlayerControls(vm)
                Text(
                    "${p.format}  ·  ${p.label}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (checksOpen && p != null) {
        val checks = buildChecks(p.report, projectLines.size)
        TsDialog(title = "Checks", onDismiss = { checksOpen = false }, confirmText = null) {
            checks.forEach { c ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        levelIcon(c.level), null,
                        tint = levelColor(c.level),
                        modifier = Modifier.size(18.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(c.text, color = TsColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CoverArt(cover: ImageBitmap?) {
    val shape = RoundedCornerShape(8.dp)
    if (cover != null) {
        Image(
            bitmap = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(shape)
        )
    } else {
        Box(
            Modifier
                .size(56.dp)
                .clip(shape)
                .background(Brush.linearGradient(listOf(Color(0xFF1F3B2D), Color(0xFF0E1A14)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.MusicNote, null, tint = TsColors.Neon, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, tint: Color, description: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LyricsList(
    entries: List<PreviewEntry>,
    lines: List<LyricLine>,
    posState: State<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    // Which entry is "in focus": an active break (dots) or the line being sung.
    val focus by remember(entries) {
        derivedStateOf {
            val pos = posState.value
            val br = entries.indexOfFirst { it is PreviewEntry.Break && pos >= it.start && pos < it.end }
            if (br >= 0) {
                br
            } else {
                val li = currentLineIndex(lines, pos)
                if (li < 0) -1 else entries.indexOfFirst { it is PreviewEntry.Lyric && it.index == li }
            }
        }
    }
    LaunchedEffect(focus) {
        if (focus >= 0) listState.animateScrollToItem((focus - 1).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.06f to Color.Black,
                            0.94f to Color.Black,
                            1f to Color.Transparent
                        )
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        contentPadding = PaddingValues(top = 12.dp, bottom = 220.dp)
    ) {
        if (entries.isEmpty()) {
            item {
                Text(
                    "This file has no timestamped lines.",
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
        }
        itemsIndexed(entries, key = { _, e -> e.key }) { idx, e ->
            val focused = idx == focus
            when (e) {
                is PreviewEntry.Lyric -> LyricItem(
                    line = e.line,
                    focused = focused,
                    distance = if (focus >= 0) abs(idx - focus) else 2,
                    pos = if (focused) posState.value else 0L,
                    onClick = { onSeek(e.line.startMs) }
                )
                is PreviewEntry.Break -> {
                    val span = (e.end - e.start).coerceAtLeast(1L)
                    val progress = if (focused) ((posState.value - e.start).toFloat() / span) else 0f
                    BreakDots(active = focused, progress = progress.coerceIn(0f, 1f))
                }
            }
        }
    }
}

@Composable
private fun LyricItem(line: LyricLine, focused: Boolean, distance: Int, pos: Long, onClick: () -> Unit) {
    val focusAmt by animateFloatAsState(if (focused) 1f else 0f, tween(350), label = "lyricFocus")
    val blurTarget = if (focused) 0f else (distance * 1.5f).coerceIn(2f, 6f)
    val blurDp by animateDpAsState(blurTarget.dp, tween(350), label = "lyricBlur")
    Text(
        lyricText(line, focused, pos, focusAmt),
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        modifier = Modifier
            .fillMaxWidth()
            .blur(blurDp, BlurredEdgeTreatment.Unbounded)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun BreakDots(active: Boolean, progress: Float) {
    val height by animateDpAsState(if (active) 56.dp else 0.dp, tween(300), label = "dotsHeight")
    Row(
        Modifier.fillMaxWidth().height(height).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (height > 8.dp) {
            repeat(3) { i ->
                val lit = progress > (i + 0.5f) / 3f
                val alpha by animateFloatAsState(if (lit) 0.95f else 0.3f, tween(250), label = "dot$i")
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
private fun PlayerControls(vm: EditorViewModel) {
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val fraction = if (duration > 0) position.toFloat() / duration else 0f

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        ThinSeekBar(
            fraction = fraction,
            onSeek = { if (duration > 0) vm.seekTo((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth()) {
            Text(TimeFmt.length(position), color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "-" + TimeFmt.length((duration - position).coerceAtLeast(0L)),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BareIconButton(Icons.Rounded.FastRewind, 44, "Back 5 seconds") { vm.seekBy(-5000) }
            BareIconButton(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                64,
                if (playing) "Pause" else "Play"
            ) { vm.togglePlay() }
            BareIconButton(Icons.Rounded.FastForward, 44, "Forward 5 seconds") { vm.seekBy(5000) }
        }
    }
}

@Composable
private fun BareIconButton(icon: ImageVector, iconSize: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(80.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(iconSize.dp))
    }
}

/** Thin Apple-style progress bar: tap or drag to seek. */
@Composable
private fun ThinSeekBar(fraction: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier) {
    val seek by rememberUpdatedState(onSeek)
    Box(
        modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { off -> seek((off.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    seek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )
    }
}
