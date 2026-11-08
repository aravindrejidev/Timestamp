package com.timestamp.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timestamp.app.format.TimeFmt
import com.timestamp.app.ui.components.GhostButton
import com.timestamp.app.ui.components.GlassCard
import com.timestamp.app.ui.components.NeonButton
import com.timestamp.app.ui.components.SectionLabel
import com.timestamp.app.ui.components.tsTextFieldColors
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.viewmodel.ImportViewModel

@Composable
fun ImportScreen(vm: ImportViewModel, onCreated: (Long) -> Unit) {
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onAudioPicked(uri)
    }
    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.onLyricsFilePicked(uri)
    }
    val clipboard = LocalClipboardManager.current
    val lineCount = remember(vm.lyrics) { vm.lyrics.lines().count { it.isNotBlank() } }
    val fieldShape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("New project", color = TsColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)

        // Audio
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Audio")
            Spacer(Modifier.height(10.dp))
            val uri = vm.audioUri
            if (uri == null) {
                Text(
                    "Choose an MP3, WAV or FLAC file.",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(TsColors.Chip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AudioFile, null, tint = TsColors.Neon)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            vm.audioName,
                            color = TsColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (vm.audioDurationMs > 0) {
                            Text(
                                "Duration ${TimeFmt.length(vm.audioDurationMs)}",
                                color = TsColors.TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GhostButton(
                if (uri == null) "Choose audio file" else "Change audio file",
                onClick = { audioPicker.launch(arrayOf("audio/*", "audio/flac", "audio/x-flac")) },
                icon = Icons.Rounded.UploadFile
            )
        }

        // Metadata
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Song details")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it },
                label = { Text("Song title") },
                singleLine = true,
                shape = fieldShape,
                colors = tsTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.artist,
                onValueChange = { vm.artist = it },
                label = { Text("Artist name") },
                singleLine = true,
                shape = fieldShape,
                colors = tsTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Lyrics
        GlassCard(Modifier.fillMaxWidth()) {
            SectionLabel("Lyrics")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.lyrics,
                onValueChange = { vm.lyrics = it },
                placeholder = { Text("Paste lyrics here, one line per row. Existing [mm:ss.xx] tags are imported.") },
                minLines = 8,
                maxLines = 14,
                shape = fieldShape,
                colors = tsTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$lineCount lines",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    "Paste",
                    onClick = { clipboard.getText()?.text?.let { vm.lyrics = it } },
                    icon = Icons.Rounded.ContentPaste
                )
                GhostButton(
                    "Text file",
                    onClick = {
                        textPicker.launch(arrayOf("text/*", "application/octet-stream", "application/x-subrip"))
                    },
                    icon = Icons.Rounded.UploadFile
                )
            }
        }

        NeonButton(
            if (vm.busy) "Creating…" else "Create project",
            onClick = { vm.createProject(onCreated) },
            enabled = vm.canCreate && !vm.busy,
            icon = Icons.Rounded.Bolt,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
}
