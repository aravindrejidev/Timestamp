package com.timestamp.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.ui.components.GhostButton
import com.timestamp.app.ui.components.NeonButton
import com.timestamp.app.ui.components.SectionLabel
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.update.UpdateInfo
import com.timestamp.app.update.UpdateState
import com.timestamp.app.update.UpdateViewModel
import com.timestamp.app.update.infoOrNull
import java.util.Locale

/** Shows the update popup on top of the whole app. Place it once, inside the theme. */
@Composable
fun UpdateHost(vm: UpdateViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val visible by vm.dialogVisible.collectAsStateWithLifecycle()

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    if (!visible) return
    val info = state.infoOrNull ?: return
    val busy = state is UpdateState.Downloading

    Dialog(
        onDismissRequest = { if (!busy) vm.dismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
            usePlatformDefaultWidth = false
        )
    ) {
        UpdateCard(vm, state, info, Modifier.padding(horizontal = 20.dp))
    }
}

private fun mb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

/** Very small markdown reader for the release notice: headings, bullets, plain lines. */
private fun releaseNoteLines(notes: String): List<Pair<Boolean, String>> {
    val out = ArrayList<Pair<Boolean, String>>()
    for (raw in notes.lines()) {
        var line = raw.trim()
        if (line.isEmpty()) continue
        val heading = line.startsWith("#")
        line = line.trimStart('#').trim()
        if (line.startsWith("- ") || line.startsWith("* ")) line = "•  " + line.substring(2).trim()
        line = line.replace("**", "").replace("__", "").replace("`", "")
        if (line.isNotEmpty()) out.add(heading to line)
    }
    return out
}

@Composable
private fun UpdateCard(vm: UpdateViewModel, state: UpdateState, info: UpdateInfo, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val title = when (state) {
        is UpdateState.Downloading -> "Downloading update"
        is UpdateState.Ready -> "Ready to install"
        is UpdateState.NeedsPermission -> "One-time permission needed"
        is UpdateState.DownloadFailed -> "Update failed"
        else -> "Update available"
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TsColors.Card)
            .border(1.dp, TsColors.Border, shape)
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(TsColors.Neon),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = TsColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        vm.installedVersionName,
                        color = TsColors.TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                    Text("  →  ", color = TsColors.TextSecondary, fontSize = 13.sp)
                    Text(
                        info.versionName,
                        color = TsColors.Neon,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (info.sizeBytes > 0) {
                        Text(
                            "   ·   ${mb(info.sizeBytes)}",
                            color = TsColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        when (state) {
            is UpdateState.Downloading -> {
                val p by animateFloatAsState(state.progress, label = "updateProgress")
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(TsColors.Chip)) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(CircleShape).background(TsColors.Neon))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.total > 0) {
                        "${mb(state.downloaded)} of ${mb(state.total)}  ·  ${(state.progress * 100).toInt()}%"
                    } else {
                        mb(state.downloaded)
                    },
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(18.dp))
                GhostButton("Cancel", { vm.cancelDownload() }, modifier = Modifier.fillMaxWidth())
            }

            is UpdateState.Ready -> {
                Text(
                    "The installer should open now. If it did not, tap Install.",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(18.dp))
                NeonButton("Install", { vm.install() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton({ vm.dismiss() }, Modifier.fillMaxWidth()) {
                    Text("Close", color = TsColors.TextSecondary)
                }
            }

            is UpdateState.NeedsPermission -> {
                Text(
                    "Android needs your OK once to let Timestamp install updates. " +
                        "Turn on \"Allow from this source\", then come back. The installation continues by itself.",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(18.dp))
                NeonButton("Open settings", { vm.openPermissionSettings() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton({ vm.dismiss() }, Modifier.fillMaxWidth()) {
                    Text("Not now", color = TsColors.TextSecondary)
                }
            }

            is UpdateState.DownloadFailed -> {
                Text(state.message, color = TsColors.Danger, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(18.dp))
                NeonButton("Try again", { vm.startDownload() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                TextButton({ vm.dismiss() }, Modifier.fillMaxWidth()) {
                    Text("Close", color = TsColors.TextSecondary)
                }
            }

            else -> {
                SectionLabel("What's new")
                Spacer(Modifier.height(8.dp))
                val lines = remember(info.notes) { releaseNoteLines(info.notes) }
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            "Bug fixes and improvements.",
                            color = TsColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    lines.forEach { (heading, text) ->
                        Text(
                            text,
                            color = if (heading) TsColors.TextPrimary else TsColors.TextSecondary,
                            fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                NeonButton("Update now", { vm.startDownload() }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ vm.dismiss() }) { Text("Later", color = TsColors.TextSecondary) }
                    TextButton({ vm.skipVersion() }) { Text("Skip this version", color = TsColors.TextSecondary) }
                }
            }
        }
    }
}
