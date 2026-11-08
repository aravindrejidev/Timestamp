package com.timestamp.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.data.SettingsRepository
import com.timestamp.app.ui.components.GhostButton
import com.timestamp.app.ui.components.Panel
import com.timestamp.app.ui.components.ScreenHeader
import com.timestamp.app.ui.components.SectionLabel
import com.timestamp.app.ui.theme.TsColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val amoled by settings.amoled.collectAsStateWithLifecycle()
    val glow by settings.glow.collectAsStateWithLifecycle()
    val tapComp by settings.tapCompMs.collectAsStateWithLifecycle()
    val folder by settings.exportFolder.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            settings.setExportFolder(uri.toString())
        }
    }

    val folderName = remember(folder) {
        folder?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }
    }
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader("Settings", onBack)

        Panel(Modifier.fillMaxWidth()) {
            SectionLabel("Appearance")
            Spacer(Modifier.height(8.dp))
            SettingSwitch("AMOLED black", "Pure black background instead of matte dark grey", amoled, settings::setAmoled)
            SettingSwitch("Neon glow", "Glowing accents on buttons and cards", glow, settings::setGlow)
            Text(
                "Timestamp is a dark-theme app; there is no light mode.",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }

        Panel(Modifier.fillMaxWidth()) {
            SectionLabel("Sync")
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap latency compensation: $tapComp ms",
                color = TsColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Subtracted from every TAP so timestamps land where you meant to press.",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = tapComp.toFloat(),
                onValueChange = { settings.setTapCompMs((it / 10f).roundToInt() * 10) },
                valueRange = 0f..400f,
                colors = SliderDefaults.colors(
                    thumbColor = TsColors.Neon,
                    activeTrackColor = TsColors.Neon,
                    inactiveTrackColor = TsColors.Chip
                )
            )
        }

        Panel(Modifier.fillMaxWidth()) {
            SectionLabel("Storage")
            Spacer(Modifier.height(8.dp))
            Text("Default export folder", color = TsColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(
                folderName ?: if (folder != null) "Selected folder" else "Not set. Exports use Share or Save as.",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Choose folder", onClick = { picker.launch(null) }, icon = Icons.Rounded.FolderOpen)
                if (folder != null) {
                    GhostButton("Clear", onClick = { settings.setExportFolder(null) })
                }
            }
        }

        Panel(Modifier.fillMaxWidth()) {
            SectionLabel("About")
            Spacer(Modifier.height(8.dp))
            Text("Timestamp $version", color = TsColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(
                "Lyrics and subtitle sync studio. Formats: LRC, TTML, ELARC (Enhanced LRC).",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Built with Kotlin, Jetpack Compose, AndroidX Media3 and Room.",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TsColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = TsColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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
