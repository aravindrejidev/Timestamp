package com.timestamp.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.data.ProjectEntity
import com.timestamp.app.ui.components.NeonButton
import com.timestamp.app.ui.components.Panel
import com.timestamp.app.ui.components.SectionLabel
import com.timestamp.app.ui.components.TsDialog
import com.timestamp.app.ui.components.TsIconButton
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.viewmodel.HomeViewModel
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onNew: () -> Unit,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onSettings: () -> Unit
) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<ProjectEntity?>(null) }
    val synced = projects.sumOf { it.syncedLines }
    val total = projects.sumOf { it.totalLines }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = navBottom + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(TsColors.Neon),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("Timestamp", color = TsColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TsIconButton(Icons.Rounded.Settings, onSettings, contentDescription = "Settings")
            }
            Spacer(Modifier.height(22.dp))
            Text("Your projects", color = TsColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (projects.isEmpty()) "Sync lyrics to music. Export LRC, TTML or ELARC."
                else "${projects.size} projects  ·  $synced of $total lines synced",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            NeonButton(
                "New project",
                onClick = onNew,
                icon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            if (projects.isNotEmpty()) SectionLabel("Recent")
          }
        }

        if (projects.isEmpty()) {
            item {
                Panel(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                    Icon(
                        Icons.Rounded.LibraryMusic, null,
                        tint = TsColors.Neon,
                        modifier = Modifier.size(32.dp).align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No projects yet",
                        color = TsColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tap New project, pick a song and paste its lyrics.",
                        color = TsColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            items(projects, key = { it.id }) { p ->
                ProjectRow(p, onClick = { onOpen(p.id) }, onDelete = { toDelete = p })
            }
        }
    }

    toDelete?.let { p ->
        TsDialog(
            title = "Delete project?",
            onDismiss = { toDelete = null },
            confirmText = "Delete",
            onConfirm = {
                onDelete(p.id)
                toDelete = null
            }
        ) {
            Text(
                "“${p.title}” and its timestamps will be removed from this device. The audio file itself is not touched.",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProjectRow(p: ProjectEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val frac = if (p.totalLines > 0) p.syncedLines.toFloat() / p.totalLines else 0f
    val done = p.totalLines > 0 && p.syncedLines >= p.totalLines
    val whenText = DateUtils.getRelativeTimeSpanString(
        p.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()
    Panel(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxSize(),
                    color = TsColors.Neon,
                    strokeWidth = 3.dp,
                    trackColor = TsColors.Chip
                )
                Text(
                    "${(frac * 100).roundToInt()}%",
                    color = TsColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    p.title,
                    color = TsColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    p.artist.ifBlank { "Unknown artist" },
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (done) "Fully synced" else "${p.syncedLines}/${p.totalLines} lines") + "  ·  $whenText",
                    color = if (done) TsColors.Neon else TsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(8.dp))
            TsIconButton(
                Icons.Rounded.Delete, onDelete,
                size = 36.dp, tint = TsColors.TextSecondary, contentDescription = "Delete project"
            )
        }
    }
}
