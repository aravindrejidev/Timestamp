package com.timestamp.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.data.LyricLine
import com.timestamp.app.data.SyncMode
import com.timestamp.app.data.WordTiming
import com.timestamp.app.format.TimeFmt
import com.timestamp.app.format.currentLineIndex
import com.timestamp.app.format.splitWords
import com.timestamp.app.ui.components.TsIconButton
import com.timestamp.app.ui.components.Panel
import com.timestamp.app.ui.components.NeonButton
import com.timestamp.app.ui.components.SeekPill
import com.timestamp.app.ui.components.TsChip
import com.timestamp.app.ui.components.TsDialog
import com.timestamp.app.ui.components.WaveformView
import com.timestamp.app.ui.components.tsTextFieldColors
import com.timestamp.app.ui.theme.LocalGlow
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.viewmodel.EditorViewModel

private sealed interface StudioDialog {
    data class EditText(val index: Int) : StudioDialog
    data class Split(val index: Int) : StudioDialog
    data class Adjust(val index: Int) : StudioDialog
    data class Insert(val index: Int) : StudioDialog
    data object Offset : StudioDialog
}

private enum class LineAction(val label: String, val icon: ImageVector) {
    ADJUST("Adjust timestamp", Icons.Rounded.Tune),
    EDIT("Edit text", Icons.Rounded.Edit),
    SPLIT("Split line", Icons.Rounded.ContentCut),
    MERGE("Merge with next", Icons.Rounded.MergeType),
    INSERT("Insert line below", Icons.Rounded.Add),
    CLEAR("Clear timestamp", Icons.Rounded.RestartAlt),
    DELETE("Delete line", Icons.Rounded.Delete)
}

private fun nextWordText(line: LyricLine?): String {
    if (line == null) return "-"
    val ws = if (line.words.isEmpty()) splitWords(line.text).map { WordTiming(it) } else line.words
    return (ws.firstOrNull { it.startMs < 0 } ?: ws.firstOrNull())?.text ?: "-"
}

@Composable
fun StudioScreen(vm: EditorViewModel, onBack: () -> Unit, onExport: () -> Unit, onPreview: () -> Unit) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val cursor by vm.cursor.collectAsStateWithLifecycle()
    val mode by vm.syncMode.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val error by vm.playerError.collectAsStateWithLifecycle()
    val duet by vm.duetMode.collectAsStateWithLifecycle()
    val activeSinger by vm.activeSinger.collectAsStateWithLifecycle()
    val positionState = vm.positionMs.collectAsStateWithLifecycle()
    val playingIndex by remember(lines) { derivedStateOf { currentLineIndex(lines, positionState.value) } }

    var dialog by remember { mutableStateOf<StudioDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    BackHandler {
        vm.pause()
        vm.save()
        onBack()
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.pause()
            vm.save()
        }
    }
    LaunchedEffect(cursor, lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem((cursor - 2).coerceIn(0, lines.lastIndex))
        }
    }

    val m = meta
    if (!loaded || m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Open a project from Home to start syncing.",
                color = TsColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    val syncedCount = lines.count { it.startMs >= 0 }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TsIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack,
                onClick = { vm.pause(); vm.save(); onBack() },
                size = 40.dp, contentDescription = "Back"
            )
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    m.title,
                    color = TsColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$syncedCount/${lines.size} synced" + if (m.artist.isNotBlank()) "  ·  ${m.artist}" else "",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TsIconButton(
                Icons.AutoMirrored.Rounded.Undo, onClick = { vm.undo() },
                size = 40.dp, enabled = canUndo, contentDescription = "Undo"
            )
            TsIconButton(
                Icons.Rounded.Download, onClick = { vm.pause(); vm.save(); onExport() },
                size = 40.dp, tint = TsColors.Neon, contentDescription = "Export"
            )
            Box {
                TsIconButton(
                    Icons.Rounded.MoreVert, onClick = { menuOpen = true },
                    size = 40.dp, contentDescription = "More tools"
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuRow("Preview result", Icons.Rounded.PlayArrow) {
                        menuOpen = false
                        vm.pause()
                        vm.save()
                        if (vm.previewCurrent()) {
                            onPreview()
                        } else {
                            Toast.makeText(context, "Sync at least one line first", Toast.LENGTH_SHORT).show()
                        }
                    }
                    MenuRow("Offset all timestamps", Icons.Rounded.Timer) {
                        menuOpen = false
                        if (syncedCount > 0) {
                            dialog = StudioDialog.Offset
                        } else {
                            Toast.makeText(context, "Sync at least one line first", Toast.LENGTH_SHORT).show()
                        }
                    }
                    MenuRow("Add line at end", Icons.Rounded.Add) {
                        menuOpen = false
                        dialog = StudioDialog.Insert(lines.lastIndex)
                    }
                    MenuRow("Estimate word timings", Icons.Rounded.AutoFixHigh) {
                        menuOpen = false
                        val n = vm.estimateWordTimings()
                        Toast.makeText(
                            context,
                            if (n > 0) "Word timings estimated for $n lines" else "Nothing to estimate. Sync some lines first.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    MenuRow(if (duet) "Duet mode: on" else "Duet mode: off", Icons.Rounded.Group) {
                        menuOpen = false
                        vm.setDuetMode(!duet)
                        Toast.makeText(
                            context,
                            if (!duet) "Duet mode on. Tap the number on a line to switch its singer." else "Duet mode off",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    MenuRow("Clear all timestamps", Icons.Rounded.RestartAlt) {
                        menuOpen = false
                        vm.clearAllTimestamps()
                        Toast.makeText(context, "All timestamps cleared. Undo is available.", Toast.LENGTH_SHORT).show()
                    }
                    MenuRow("Save project", Icons.Rounded.Save) {
                        menuOpen = false
                        vm.saveNow()
                        Toast.makeText(context, "Project saved", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        PlayerPanel(vm)

        error?.let {
            Text(
                it,
                color = TsColors.Danger,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        // Lyrics list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (lines.isEmpty()) {
                item {
                    Text(
                        "No lyrics in this project. Use the menu to add a line.",
                        color = TsColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }
            itemsIndexed(lines, key = { _, l -> l.id }) { i, line ->
                LyricRow(
                    line = line,
                    isCursor = i == cursor,
                    isLast = i == lines.lastIndex,
                    duet = duet,
                    onToggleSinger = { vm.toggleSinger(i) },
                    isPlaying = i == playingIndex,
                    wordMode = mode == SyncMode.WORD,
                    onSelect = { vm.selectLine(i) },
                    onStamp = { vm.stampLine(i) },
                    onAction = { a ->
                        when (a) {
                            LineAction.ADJUST -> dialog = StudioDialog.Adjust(i)
                            LineAction.EDIT -> dialog = StudioDialog.EditText(i)
                            LineAction.SPLIT -> dialog = StudioDialog.Split(i)
                            LineAction.MERGE -> vm.mergeWithNext(i)
                            LineAction.INSERT -> dialog = StudioDialog.Insert(i)
                            LineAction.CLEAR -> vm.clearTimestamp(i)
                            LineAction.DELETE -> vm.deleteLine(i)
                        }
                    }
                )
            }
        }

        // Sync controls
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xCC0D0D0D))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TsChip("Line sync", mode == SyncMode.LINE, { vm.setMode(SyncMode.LINE) })
                TsChip("Word sync", mode == SyncMode.WORD, { vm.setMode(SyncMode.WORD) })
                Spacer(Modifier.weight(1f))
                Text(
                    "Line ${if (lines.isEmpty()) 0 else cursor + 1}/${lines.size}",
                    color = TsColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
            if (duet) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Singer", color = TsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                    TsChip("Auto", activeSinger == 0, { vm.setActiveSinger(0) }, compact = true, modifier = Modifier.width(60.dp))
                    TsChip("1", activeSinger == 1, { vm.setActiveSinger(1) }, compact = true, modifier = Modifier.width(48.dp))
                    TsChip("2", activeSinger == 2, { vm.setActiveSinger(2) }, compact = true, modifier = Modifier.width(48.dp))
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (activeSinger == 0) "TAP keeps each line's singer" else "TAP sets singer $activeSinger",
                        color = TsColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            TapButton(
                title = if (mode == SyncMode.LINE) "TAP TO SYNC LINE" else "TAP TO SYNC WORD",
                subtitle = if (mode == SyncMode.LINE) {
                    lines.getOrNull(cursor)?.text ?: "-"
                } else {
                    "Next word: ${nextWordText(lines.getOrNull(cursor))}"
                },
                onTap = {
                    if (lines.isEmpty()) {
                        Toast.makeText(context, "Add a lyric line first", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.tap()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            )
        }
    }

    StudioDialogs(dialog, { dialog = null }, vm, lines)
}

@Composable
private fun MenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = TsColors.TextPrimary) },
        leadingIcon = { Icon(icon, null, tint = TsColors.TextSecondary) },
        onClick = onClick
    )
}

@Composable
private fun PlayerPanel(vm: EditorViewModel) {
    val position by vm.positionMs.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val waveform by vm.waveform.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val markers = remember(lines) { lines.filter { it.startMs >= 0 }.map { it.startMs } }

    Panel(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(12.dp)) {
        WaveformView(
            waveform = waveform,
            positionMs = position,
            durationMs = duration,
            markers = markers,
            onSeek = { vm.seekTo(it) },
            modifier = Modifier.fillMaxWidth().height(96.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                TimeFmt.precise(position),
                color = TsColors.Neon,
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                "  / ${TimeFmt.lrc(duration)}",
                color = TsColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            Spacer(Modifier.weight(1f))
            TsChip("${if (speed == 1f) "1" else speed.toString()}x", false, { vm.cycleSpeed() }, compact = true)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeekPill("-5s") { vm.seekBy(-5000) }
            SeekPill("-0.1") { vm.seekBy(-100) }
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
            SeekPill("+0.1") { vm.seekBy(100) }
            SeekPill("+5s") { vm.seekBy(5000) }
        }
    }
}

@Composable
private fun TapButton(title: String, subtitle: String, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "tapScale")
    val tapCallback by rememberUpdatedState(onTap)
    val shape = RoundedCornerShape(20.dp)
    val glow = LocalGlow.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .scale(scale)
            .then(
                if (glow) Modifier.shadow(16.dp, shape, ambientColor = TsColors.Neon, spotColor = TsColors.Neon)
                else Modifier
            )
            .clip(shape)
            .background(TsColors.Neon)
            .pointerInput(Unit) {
                // Fires on touch-down for the lowest possible latency.
                detectTapGestures(onPress = {
                    pressed = true
                    tapCallback()
                    tryAwaitRelease()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
            Icon(Icons.Rounded.Bolt, null, tint = Color.Black, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    subtitle,
                    color = Color.Black.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildLineText(line: LyricLine, isCursor: Boolean, isPlaying: Boolean, wordMode: Boolean): AnnotatedString =
    buildAnnotatedString {
        if (isCursor && wordMode) {
            val words = if (line.words.isEmpty()) splitWords(line.text).map { WordTiming(it) } else line.words
            val next = words.indexOfFirst { it.startMs < 0 }
            words.forEachIndexed { i, w ->
                val style = when {
                    w.startMs >= 0 -> SpanStyle(color = TsColors.Neon)
                    i == next -> SpanStyle(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                    else -> SpanStyle(color = TsColors.TextSecondary)
                }
                withStyle(style) { append(w.text) }
                if (i < words.lastIndex) append(" ")
            }
        } else {
            val color = when {
                isPlaying -> TsColors.Neon
                line.startMs >= 0 -> TsColors.TextPrimary
                else -> TsColors.TextSecondary
            }
            withStyle(
                SpanStyle(color = color, fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal)
            ) { append(line.text) }
        }
    }

@Composable
private fun LyricRow(
    line: LyricLine,
    isCursor: Boolean,
    isLast: Boolean,
    duet: Boolean,
    onToggleSinger: () -> Unit,
    isPlaying: Boolean,
    wordMode: Boolean,
    onSelect: () -> Unit,
    onStamp: () -> Unit,
    onAction: (LineAction) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    var menu by remember { mutableStateOf(false) }
    val text = remember(line, isCursor, isPlaying, wordMode) { buildLineText(line, isCursor, isPlaying, wordMode) }
    val synced = line.startMs >= 0

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isCursor) TsColors.Neon.copy(alpha = 0.09f) else Color.Transparent)
            .border(1.dp, if (isCursor) TsColors.Neon.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tap the timestamp chip to lock the current playhead time into this line.
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (synced) TsColors.Neon.copy(alpha = 0.14f) else TsColors.Chip)
                .clickable(onClick = onStamp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                if (synced) TimeFmt.lrc(line.startMs) else "--:--.--",
                color = if (synced) TsColors.Neon else TsColors.TextSecondary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            lineHeight = 22.sp,
            textAlign = if (duet && line.singer == 2) TextAlign.End else TextAlign.Start
        )
        if (duet) {
            val singerColor = if (line.singer == 2) TsColors.Singer2 else TsColors.Neon
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(1.dp, singerColor, CircleShape)
                    .clickable(onClick = onToggleSinger),
                contentAlignment = Alignment.Center
            ) {
                Text("${line.singer}", color = singerColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.MoreVert, "Line tools", tint = TsColors.TextSecondary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                LineAction.entries.filter { a ->
                    when (a) {
                        LineAction.MERGE -> !isLast
                        LineAction.CLEAR -> synced
                        else -> true
                    }
                }.forEach { a ->
                    MenuRow(a.label, a.icon) {
                        menu = false
                        onAction(a)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioDialogs(
    dialog: StudioDialog?,
    onDismiss: () -> Unit,
    vm: EditorViewModel,
    lines: List<LyricLine>
) {
    when (dialog) {
        null -> Unit

        is StudioDialog.EditText -> {
            var text by remember(dialog.index) { mutableStateOf(lines.getOrNull(dialog.index)?.text ?: "") }
            TsDialog(
                title = "Edit line",
                onDismiss = onDismiss,
                confirmText = "Save",
                confirmEnabled = text.isNotBlank(),
                onConfirm = { vm.editText(dialog.index, text); onDismiss() }
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 2,
                    maxLines = 6,
                    colors = tsTextFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is StudioDialog.Split -> {
            var tfv by remember(dialog.index) {
                val t = lines.getOrNull(dialog.index)?.text ?: ""
                mutableStateOf(TextFieldValue(t, TextRange(t.length / 2)))
            }
            val s = tfv.selection.start
            val ok = s in 1 until tfv.text.length &&
                tfv.text.substring(0, s).isNotBlank() && tfv.text.substring(s).isNotBlank()
            TsDialog(
                title = "Split line",
                onDismiss = onDismiss,
                confirmText = "Split",
                confirmEnabled = ok,
                onConfirm = {
                    vm.splitLine(dialog.index, tfv.text.substring(0, s), tfv.text.substring(s))
                    onDismiss()
                }
            ) {
                Text(
                    "Place the cursor where the line should be split.",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tfv,
                    onValueChange = { tfv = it },
                    minLines = 2,
                    maxLines = 6,
                    colors = tsTextFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is StudioDialog.Insert -> {
            var text by remember(dialog.index) { mutableStateOf("") }
            TsDialog(
                title = "New line",
                onDismiss = onDismiss,
                confirmText = "Add",
                confirmEnabled = text.isNotBlank(),
                onConfirm = { vm.insertLineBelow(dialog.index, text); onDismiss() }
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Lyric text") },
                    minLines = 2,
                    maxLines = 6,
                    colors = tsTextFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is StudioDialog.Adjust -> {
            val l = lines.getOrNull(dialog.index)
            val synced = l != null && l.startMs >= 0
            TsDialog(title = "Adjust timestamp", onDismiss = onDismiss, confirmText = null) {
                Text(
                    if (synced) TimeFmt.precise(l!!.startMs) else "Not synced",
                    color = TsColors.Neon,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    l?.text ?: "",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(-100L, -10L, 10L, 100L).forEach { d ->
                        TsChip(
                            text = (if (d > 0) "+" else "") + d,
                            selected = false,
                            onClick = { vm.nudgeLine(dialog.index, d) },
                            modifier = Modifier.weight(1f),
                            compact = true,
                            enabled = synced
                        )
                    }
                }
                Text(
                    "milliseconds",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(14.dp))
                NeonButton(
                    "Set to playhead",
                    onClick = { vm.stampLine(dialog.index) },
                    icon = Icons.Rounded.Timer,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        StudioDialog.Offset -> {
            var delta by remember { mutableLongStateOf(0L) }
            TsDialog(
                title = "Offset all timestamps",
                onDismiss = onDismiss,
                confirmText = "Apply",
                confirmEnabled = delta != 0L,
                onConfirm = { vm.offsetAll(delta); onDismiss() }
            ) {
                Text(
                    "Shift every synced line and word by",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    (if (delta > 0) "+" else "") + "$delta ms",
                    color = TsColors.Neon,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                listOf(listOf(-1000L, -500L, -100L, -10L), listOf(10L, 100L, 500L, 1000L)).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        row.forEach { d ->
                            TsChip(
                                text = (if (d > 0) "+" else "") + d,
                                selected = false,
                                onClick = { delta += d },
                                modifier = Modifier.weight(1f),
                                compact = true
                            )
                        }
                    }
                }
                TextButton(onClick = { delta = 0L }) { Text("Reset", color = TsColors.TextSecondary) }
            }
        }
    }
}
