package com.timestamp.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
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
import com.timestamp.app.viewmodel.PreviewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ------------------------------------------------------------------ checks (shown in a dialog)

private data class CheckItem(val level: Int, val text: String) // 0 ok, 1 warning, 2 problem

private fun buildChecks(p: PreviewData, projectLines: Int): List<CheckItem> {
    val r = p.report
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
    val right = p.lines.count { it.singer == 2 }
    if (right > 0) {
        out.add(
            if (p.singersFromProject) {
                CheckItem(
                    1,
                    "$right lines are shown on the right (singer 2) from your project. " +
                        "This file has no singer tags itself; TTML keeps them."
                )
            } else {
                CheckItem(0, "Two singers: $right lines for singer 2 appear on the right.")
            }
        )
    }
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

private val LyricFontSize = 32.sp

/** Character range of every word inside the text a word-synced line is drawn with (words joined by one space). */
private fun wordSpans(line: LyricLine): List<IntRange> {
    var offset = 0
    return line.words.map { w ->
        val r = offset until offset + w.text.length
        offset += w.text.length + 1
        r
    }
}

/**
 * Apple Music style word reveal. The line is already drawn dim underneath; this paints the
 * white "sung" part on top:
 *  - a word that is finished is fully white,
 *  - the word being sung is revealed from left to right by a moving gradient with a soft edge
 *    (the letters are not brightened all at once),
 *  - a word that has not started stays dim.
 * [strength] (0..1) fades the whole white layer in and out when the line gains or loses focus.
 */
private fun DrawScope.drawWordWipe(
    layout: TextLayoutResult,
    line: LyricLine,
    spans: List<IntRange>,
    posMs: Long,
    strength: Float,
    featherPx: Float
) {
    val textLength = layout.layoutInput.text.length
    val white = Color.White.copy(alpha = strength)
    val pad = 2.dp.toPx()
    val sung = Path()
    var anySung = false
    val partial = ArrayList<Pair<Rect, Float>>()

    for (i in line.words.indices) {
        val span = spans.getOrNull(i) ?: continue
        if (span.isEmpty() || span.last >= textLength) continue
        val w = line.words[i]
        val a = layout.getBoundingBox(span.first)
        val b = layout.getBoundingBox(span.last)
        val rect = if (b.right > a.left) {
            Rect(a.left - pad, minOf(a.top, b.top), b.right + pad, maxOf(a.bottom, b.bottom))
        } else {
            // a very long word that wraps onto the next row: use the full width of those rows
            Rect(0f, minOf(a.top, b.top), layout.size.width.toFloat(), maxOf(a.bottom, b.bottom))
        }
        val dur = (w.endMs - w.startMs).coerceAtLeast(1L)
        val f = ((posMs - w.startMs).toFloat() / dur).coerceIn(0f, 1f)
        if (f >= 1f) {
            sung.addRect(rect)
            anySung = true
        } else if (f > 0f) {
            partial.add(rect to f)
        }
    }

    if (anySung) {
        clipPath(sung) { drawText(layout, color = white) }
    }
    for ((rect, f) in partial) {
        // The soft edge travels from before the first letter to past the last letter.
        val edge = rect.left + f * (rect.width + featherPx)
        val brush = Brush.horizontalGradient(
            colorStops = arrayOf(0f to white, 1f to white.copy(alpha = 0f)),
            startX = edge - featherPx,
            endX = edge
        )
        clipRect(rect.left, rect.top, rect.right, rect.bottom) { drawText(layout, brush = brush) }
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
    val tintTarget = coverTint?.let { lerp(it, Color.Black, 0.45f) } ?: Color(0xFF2A3A33)
    val tint by animateColorAsState(tintTarget, tween(900), label = "previewTint")
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
                if (p == null) emptyList() else buildChecks(p, projectLines.size)
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
        val checks = buildChecks(p, projectLines.size)
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
    var viewportHeight by remember { mutableIntStateOf(0) }
    val bottomPadding = with(LocalDensity.current) { (viewportHeight * 0.78f).toDp() }

    // Every row can be pushed a little (translationY) while the list moves. Rows below the new
    // line start a moment later than the rows above it, so they follow one after another,
    // like the scrolling in Apple Music.
    val shifts = remember(entries) { HashMap<String, Animatable<Float, AnimationVector1D>>() }
    fun shiftOf(key: String): Animatable<Float, AnimationVector1D> = shifts.getOrPut(key) { Animatable(0f) }

    LaunchedEffect(focus) {
        val target = focus
        if (target < 0) return@LaunchedEffect
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == target }) {
            // far away (a seek): jump next to the line first, every row starts from rest
            listState.scrollToItem(target)
            withFrameNanos { }
            for (a in shifts.values.toList()) a.snapTo(0f)
        }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
        val viewport = info.viewportSize.height
        val delta = item.offset - viewport * 0.2f
        if (abs(delta) <= 2f) return@LaunchedEffect

        if (abs(delta) > viewport * 0.5f) {
            // a long move: everything glides together
            for (a in shifts.values.toList()) a.snapTo(0f)
            listState.animateScrollBy(delta, tween(650, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }

        val visible = info.visibleItemsInfo
        val from = ((visible.firstOrNull()?.index ?: target) - 3).coerceAtLeast(0)
        val to = ((visible.lastOrNull()?.index ?: target) + 6).coerceAtMost(entries.lastIndex)

        // 1) move the list at once, 2) push every row back by the same distance so nothing seems
        // to move yet, 3) let each row slide into place, later and later the further below it is.
        val moved = listState.scrollBy(delta)
        if (abs(moved) < 1f) return@LaunchedEffect
        for (i in from..to) {
            val a = shiftOf(entries[i].key)
            a.snapTo(a.value + moved)
        }
        coroutineScope {
            for (i in from..to) {
                val a = shiftOf(entries[i].key)
                val rank = (i - target).coerceIn(0, 7)
                launch {
                    if (rank > 0) delay(rank * 45L)
                    a.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 80f))
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .onSizeChanged { viewportHeight = it.height }
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
        contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding)
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
            val shift = shiftOf(e.key)
            when (e) {
                is PreviewEntry.Lyric -> LyricItem(
                    line = e.line,
                    focused = focused,
                    distance = if (focus >= 0) abs(idx - focus) else 2,
                    posState = posState,
                    shift = { shift.value },
                    onClick = { onSeek(e.line.startMs) }
                )
                is PreviewEntry.Break -> {
                    val span = (e.end - e.start).coerceAtLeast(1L)
                    val progress = if (focused) ((posState.value - e.start).toFloat() / span) else 0f
                    BreakDots(active = focused, progress = progress.coerceIn(0f, 1f), shift = { shift.value })
                }
            }
        }
    }
}

@Composable
private fun LyricItem(
    line: LyricLine,
    focused: Boolean,
    distance: Int,
    posState: State<Long>,
    shift: () -> Float,
    onClick: () -> Unit
) {
    val right = line.singer == 2
    val focusState = animateFloatAsState(
        if (focused) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "lyricFocus"
    )
    val blurTarget = if (focused) 0f else (distance * 1.5f).coerceIn(2f, 6f)
    val blurDp by animateDpAsState(blurTarget.dp, tween(450, easing = FastOutSlowInEasing), label = "lyricBlur")

    // Word timing in the file: each word is revealed by a soft left-to-right wipe, like Apple Music.
    // Only line timing: the whole line fades up to white, which is what Apple Music does for such lyrics.
    val wordSynced = line.words.isNotEmpty() && line.words.all { it.startMs >= 0 }
    val shownText = if (wordSynced) line.words.joinToString(" ") { it.text } else line.text
    val spans = remember(line) { if (wordSynced) wordSpans(line) else emptyList() }
    var layout by remember(shownText) { mutableStateOf<TextLayoutResult?>(null) }

    val wipe = if (wordSynced) {
        Modifier.drawWithContent {
            drawContent()
            val strength = focusState.value
            val tl = layout
            if (strength > 0.001f && tl != null) {
                drawWordWipe(tl, line, spans, posState.value, strength, 0.5f * LyricFontSize.toPx())
            }
        }
    } else {
        Modifier
    }

    Box(
        Modifier.fillMaxWidth().graphicsLayer { translationY = shift() },
        contentAlignment = if (right) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            shownText,
            color = if (wordSynced) Dim else lerp(Dim, Color.White, focusState.value),
            fontSize = LyricFontSize,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            textAlign = if (right) TextAlign.End else TextAlign.Start,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .graphicsLayer {
                    val sc = 0.94f + 0.06f * focusState.value
                    scaleX = sc
                    scaleY = sc
                    transformOrigin = TransformOrigin(if (right) 1f else 0f, 0.5f)
                }
                .blur(blurDp, BlurredEdgeTreatment.Unbounded)
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .then(wipe)
        )
    }
}

@Composable
private fun BreakDots(active: Boolean, progress: Float, shift: () -> Float) {
    val height by animateDpAsState(if (active) 56.dp else 0.dp, tween(400, easing = FastOutSlowInEasing), label = "dotsHeight")
    val shown by animateFloatAsState(if (active) 1f else 0f, tween(350), label = "dotsShown")
    Box(
        Modifier.fillMaxWidth().graphicsLayer { translationY = shift() }.height(height).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (height > 6.dp) BreathingDots(progress, shown)
    }
}

/** Three dots that slowly breathe (grow and shrink) while they fill up towards the next line. */
@Composable
private fun BreathingDots(progress: Float, shown: Float) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    Row(
        Modifier.graphicsLayer {
            val sc = (0.86f + 0.26f * breath) * (0.6f + 0.4f * shown)
            scaleX = sc
            scaleY = sc
            alpha = shown
            transformOrigin = TransformOrigin(0f, 0.5f)
        },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val fill = (progress * 3f - i).coerceIn(0f, 1f)
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f + 0.65f * fill))
            )
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
            PlayPauseButton(playing) { vm.togglePlay() }
            BareIconButton(Icons.Rounded.FastForward, 44, "Forward 5 seconds") { vm.seekBy(5000) }
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(80.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = playing, animationSpec = tween(180), label = "playPause") { isPlaying ->
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
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
