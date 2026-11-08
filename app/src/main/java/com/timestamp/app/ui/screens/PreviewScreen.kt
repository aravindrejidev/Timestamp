package com.timestamp.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.data.LyricLine
import com.timestamp.app.format.PreviewReport
import com.timestamp.app.format.TimeFmt
import com.timestamp.app.format.currentLineIndex
import com.timestamp.app.ui.components.GhostButton
import com.timestamp.app.ui.components.Panel
import com.timestamp.app.ui.components.ScreenHeader
import com.timestamp.app.ui.components.SeekPill
import com.timestamp.app.ui.components.TsChip
import com.timestamp.app.ui.components.TsIconButton
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.util.FileUtils
import com.timestamp.app.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

private fun previewText(line: LyricLine, current: Boolean, past: Boolean, pos: Long): AnnotatedString =
    buildAnnotatedString {
        if (current && line.words.isNotEmpty()) {
            line.words.forEachIndexed { i, w ->
                val color = when {
                    w.endMs in 0..pos -> TsColors.Neon
                    w.startMs in 0..pos -> Color.White
                    else -> TsColors.TextSecondary
                }
                val weight = if (w.startMs in 0..pos && w.endMs > pos) FontWeight.Bold else FontWeight.SemiBold
                withStyle(SpanStyle(color = color, fontWeight = weight)) { append(w.text) }
                if (i < line.words.lastIndex) append(" ")
            }
        } else {
            val color = when {
                current -> TsColors.Neon
                past -> TsColors.TextPrimary.copy(alpha = 0.5f)
                else -> TsColors.TextSecondary
            }
            withStyle(SpanStyle(color = color, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)) {
                append(line.text)
            }
        }
    }

@Composable
fun PreviewScreen(vm: EditorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val projectLines by vm.lines.collectAsStateWithLifecycle()
    val positionState = vm.positionMs.collectAsStateWithLifecycle()
    var checksOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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
    val lines = p?.lines ?: emptyList()
    val playingIndex by remember(lines) { derivedStateOf { currentLineIndex(lines, positionState.value) } }
    LaunchedEffect(playingIndex) {
        if (playingIndex >= 0) listState.animateScrollToItem((playingIndex - 2).coerceAtLeast(0))
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader(
            title = "Preview",
            onBack = onBack,
            subtitle = if (p != null) "${p.format}  ·  ${p.label}" else null,
            actions = {
                TsIconButton(Icons.Rounded.FolderOpen, { pickFile() }, contentDescription = "Open a saved file")
            }
        )

        if (p == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Nothing to preview yet.",
                        color = TsColors.TextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton("Open a saved file", { pickFile() }, icon = Icons.Rounded.FolderOpen)
                }
            }
        } else {
            val checks = remember(p, projectLines.size) { buildChecks(p.report, projectLines.size) }
            val worst = checks.maxOfOrNull { it.level } ?: 0
            Panel(Modifier.fillMaxWidth(), onClick = { checksOpen = !checksOpen }, contentPadding = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        levelIcon(worst), null,
                        tint = levelColor(worst),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when (worst) {
                            0 -> "All checks passed"
                            1 -> "${checks.count { it.level >= 1 }} things to look at"
                            else -> "Problems found"
                        },
                        color = TsColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (checksOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        null, tint = TsColors.TextSecondary
                    )
                }
                if (checksOpen) {
                    Spacer(Modifier.height(10.dp))
                    checks.forEach { c ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                levelIcon(c.level), null,
                                tint = levelColor(c.level),
                                modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(c.text, color = TsColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (lines.isEmpty()) {
                    item {
                        Text(
                            "This file has no timestamped lines.",
                            color = TsColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                }
                itemsIndexed(lines, key = { _, l -> l.id }) { i, line ->
                    val current = i == playingIndex
                    val pos = if (current) positionState.value else 0L
                    Text(
                        previewText(line, current, i < playingIndex, pos),
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { vm.seekTo(line.startMs) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            PreviewPlayerBar(vm)
        }
    }
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

@Composable
private fun PreviewPlayerBar(vm: EditorViewModel) {
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val max = if (duration > 0) duration.toFloat() else 1f

    Panel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Slider(
            value = position.toFloat().coerceIn(0f, max),
            onValueChange = { vm.seekTo(it.toLong()) },
            valueRange = 0f..max,
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = TsColors.Neon,
                activeTrackColor = TsColors.Neon,
                inactiveTrackColor = TsColors.Chip
            )
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                TimeFmt.lrc(position),
                color = TsColors.Neon,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                TimeFmt.lrc(duration),
                color = TsColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeekPill("-5s") { vm.seekBy(-5000) }
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(30.dp)
                )
            }
            SeekPill("+5s") { vm.seekBy(5000) }
            TsChip("${if (speed == 1f) "1" else speed.toString()}x", false, { vm.cycleSpeed() }, compact = true)
        }
    }
}
