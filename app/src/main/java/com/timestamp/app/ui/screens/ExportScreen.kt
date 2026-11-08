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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
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
import com.timestamp.app.ui.components.CircleIconButton
import com.timestamp.app.ui.components.GhostButton
import com.timestamp.app.ui.components.GlassCard
import com.timestamp.app.ui.components.NeonButton
import com.timestamp.app.ui.components.SectionLabel
import com.timestamp.app.ui.components.TsChip
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.util.FileUtils
import com.timestamp.app.viewmodel.EditorViewModel

private val ttmlColors = listOf("#FFFFFF", "#00E676", "#FFEB3B", "#40C4FF", "#FF80AB", "#FFAB40")

@Composable
fun ExportScreen(vm: EditorViewModel, settings: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val meta by vm.meta.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val duration by vm.durationMs.collectAsStateWithLifecycle()
    val estimate by vm.estimateWords.collectAsStateWithLifecycle()
    val folder by settings.exportFolder.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var pendingText by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { vm.save() } }

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val ok = FileUtils.writeToUri(context, uri, pendingText)
            Toast.makeText(context, if (ok) "File saved" else "Could not save the file", Toast.LENGTH_SHORT).show()
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
    val preview = remember(format, m, lines, estimate, duration) {
        LyricsExporter.render(
            format, m.title, m.artist,
            if (duration > 0) duration else m.durationMs,
            lines, style, estimate
        )
    }
    val fileName = FileUtils.safeName(m.artist, m.title, format.ext)
    val canExport = synced > 0

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack,
                size = 40.dp, contentDescription = "Back"
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Export hub", color = TsColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEachIndexed { i, f ->
                TsChip(f.label, tab == i, { tab = i })
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "$synced of $total lines synced",
                color = TsColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                if (canExport) "Unsynced lines are left out of the exported file."
                else "Sync at least one line in the Studio to export.",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (format != ExportFormat.LRC) {
            GlassCard(Modifier.fillMaxWidth()) {
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

        if (format == ExportFormat.TTML) {
            GlassCard(Modifier.fillMaxWidth()) {
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

        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Preview  ·  $fileName")
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    if (canExport) preview else "Nothing to preview yet.",
                    color = TsColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        NeonButton(
            "Share file",
            onClick = {
                try {
                    FileUtils.share(context, fileName, preview)
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not share the file", Toast.LENGTH_SHORT).show()
                }
            },
            icon = Icons.Rounded.Share,
            enabled = canExport,
            modifier = Modifier.fillMaxWidth()
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
                "Copy",
                onClick = {
                    clipboard.setText(AnnotatedString(preview))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                icon = Icons.Rounded.ContentCopy,
                enabled = canExport,
                modifier = Modifier.weight(1f)
            )
        }
        if (folder != null) {
            GhostButton(
                "Save to default folder",
                onClick = {
                    val ok = FileUtils.saveToFolder(context, Uri.parse(folder), fileName, preview)
                    Toast.makeText(
                        context,
                        if (ok) "Saved to default folder" else "Could not write to the default folder",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                icon = Icons.Rounded.FolderOpen,
                enabled = canExport,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}
