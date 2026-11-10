package com.timestamp.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.data.SettingsRepository
import com.timestamp.app.format.ExportFormat
import com.timestamp.app.format.LyricsExporter
import com.timestamp.app.format.TtmlStyle
import com.timestamp.app.ui.components.GhostButton
import com.timestamp.app.ui.components.NeonButton
import com.timestamp.app.ui.components.Panel
import com.timestamp.app.ui.components.ScreenHeader
import com.timestamp.app.ui.components.SectionLabel
import com.timestamp.app.ui.components.TsChip
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.util.FileUtils
import com.timestamp.app.viewmodel.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ttmlColors = listOf("#FFFFFF", "#00E676", "#FFEB3B", "#40C4FF", "#FF80AB", "#FFAB40")

@Composable
fun ExportScreen(
    vm: EditorViewModel,
    settings: SettingsRepository,
    onBack: () -> Unit,
    onPreview: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val estimate by vm.estimateWords.collectAsStateWithLifecycle()
    val singerTags by vm.singerTags.collectAsStateWithLifecycle()
    val folder by settings.exportFolder.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var pendingText by remember { mutableStateOf("") }
    var lastSaved by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    DisposableEffect(Unit) { onDispose { vm.save() } }

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val ok = FileUtils.writeToUri(context, uri, pendingText)
            if (ok) {
                lastSaved = uri to FileUtils.displayName(context, uri)
                Toast.makeText(context, "File saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not save the file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val m = meta
    if (m == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Open a project first to export it.",
                color = TsColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    val format = ExportFormat.entries[tab]
    val style = TtmlStyle(m.ttmlFontSize, m.ttmlColor, m.ttmlPosition)
    val total = lines.size
    val synced = lines.count { it.startMs >= 0 }
    val preview = remember(format, m, lines, estimate, duration, singerTags) {
        LyricsExporter.render(
            format, m.title, m.artist,
            if (duration > 0) duration else m.durationMs,
            lines, style, estimate, singerTags
        )
    }
    val fileName = FileUtils.safeName(m.artist, m.title, format.ext)
    val canExport = synced > 0
    val hasDuet = lines.any { it.singer == 2 }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Export", onBack, subtitle = m.title)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEachIndexed { i, f ->
                TsChip(f.label, tab == i, { tab = i })
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            Text(
                "$synced of $total lines synced",
                color = TsColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (canExport) "Unsynced lines are left out of the file. Use Preview to check the result."
                else "Sync at least one line in the Studio to export.",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (format != ExportFormat.LRC) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Estimate missing word timings",
                            color = TsColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Lines synced only per line get word times spread evenly across the line.",
                            color = TsColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = estimate,
                        onCheckedChange = { vm.setEstimateWords(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = TsColors.Neon,
                            checkedBorderColor = TsColors.Neon,
                            uncheckedThumbColor = TsColors.TextSecondary,
                            uncheckedTrackColor = TsColors.Chip,
                            uncheckedBorderColor = TsColors.FieldBorder
                        )
                    )
                }
            }
        }

        if (hasDuet && format == ExportFormat.TTML) {
            Panel(Modifier.fillMaxWidth()) {
                Text("Two singers", color = TsColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lines of singer 2 are tagged ttm:agent=\"v2\" and aligned to the right.",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (hasDuet && format != ExportFormat.TTML) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Add singer tags",
                            color = TsColors.TextPrimary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Starts each line with v1: or v2:. Only some players understand this; TTML is the safest for duets.",
                            color = TsColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = singerTags,
                        onCheckedChange = { vm.setSingerTags(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = TsColors.Neon,
                            checkedBorderColor = TsColors.Neon,
                            uncheckedThumbColor = TsColors.TextSecondary,
                            uncheckedTrackColor = TsColors.Chip,
                            uncheckedBorderColor = TsColors.FieldBorder
                        )
                    )
                }
            }
        }

        if (format == ExportFormat.TTML) {
            Panel(Modifier.fillMaxWidth()) {
                SectionLabel("TTML style")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Font size: ${m.ttmlFontSize} px",
                    color = TsColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
                Slider(
                    value = m.ttmlFontSize.toFloat(),
                    onValueChange = { vm.setTtmlStyle(fontSize = it.toInt()) },
                    valueRange = 14f..48f,
                    colors = SliderDefaults.colors(
                        thumbColor = TsColors.Neon,
                        activeTrackColor = TsColors.Neon,
                        inactiveTrackColor = TsColors.Chip
                    )
                )
                Text("Text color", color = TsColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ttmlColors.forEach { hex ->
                        val selected = m.ttmlColor.equals(hex, ignoreCase = true)
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(hex)))
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) Color.White else TsColors.FieldBorder,
                                    CircleShape
                                )
                                .clickable { vm.setTtmlStyle(color = hex) }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Screen position", color = TsColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("top" to "Top", "center" to "Center", "bottom" to "Bottom").forEach { (key, label) ->
                        TsChip(label, m.ttmlPosition == key, { vm.setTtmlStyle(position = key) })
                    }
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            SectionLabel("File text  ·  $fileName")
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (canExport) preview else "Nothing to show yet.",
                    color = TsColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        NeonButton(
            "Preview and check",
            onClick = {
                vm.setPreview(preview, "$fileName (current export)")
                onPreview()
            },
            icon = Icons.Rounded.PlayArrow,
            enabled = canExport,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Plays your song with this file's lyrics, so you can see if the timing is right.",
            color = TsColors.TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GhostButton(
                "Save as…",
                onClick = {
                    pendingText = preview
                    savePicker.launch(fileName)
                },
                icon = Icons.Rounded.Save,
                enabled = canExport,
                modifier = Modifier.weight(1f)
            )
            GhostButton(
                "Share",
                onClick = {
                    try {
                        FileUtils.share(context, fileName, preview)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not share the file", Toast.LENGTH_SHORT).show()
                    }
                },
                icon = Icons.Rounded.Share,
                enabled = canExport,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GhostButton(
                "Copy text",
                onClick = {
                    clipboard.setText(AnnotatedString(preview))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Rounded.ContentCopy,
                enabled = canExport,
                modifier = Modifier.weight(1f)
            )
            if (folder != null) {
                GhostButton(
                    "Default folder",
                    onClick = {
                        val saved = FileUtils.saveToFolder(context, Uri.parse(folder), fileName, preview)
                        if (saved != null) {
                            lastSaved = saved to FileUtils.displayName(context, saved)
                            Toast.makeText(context, "Saved to default folder", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Could not write to the default folder", Toast.LENGTH_SHORT).show()
                        }
                    },
                    icon = Icons.Rounded.FolderOpen,
                    enabled = canExport,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        lastSaved?.let { (uri, name) ->
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = TsColors.Neon)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Saved", color = TsColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(name, color = TsColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    "Preview saved file",
                    onClick = {
                        scope.launch {
                            val text = withContext(Dispatchers.IO) { FileUtils.readText(context, uri) }
                            if (text == null) {
                                Toast.makeText(context, "Could not read the saved file", Toast.LENGTH_SHORT).show()
                            } else {
                                vm.setPreview(text, "$name (saved file)")
                                onPreview()
                            }
                        }
                    },
                    icon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
