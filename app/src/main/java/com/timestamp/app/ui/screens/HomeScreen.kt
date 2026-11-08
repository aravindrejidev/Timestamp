package com.timestamp.app.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timestamp.app.data.ProjectEntity
import com.timestamp.app.ui.components.CircleIconButton
import com.timestamp.app.ui.components.GlassCard
import com.timestamp.app.ui.components.SectionLabel
import com.timestamp.app.ui.components.TsDialog
import com.timestamp.app.ui.theme.TsColors
import com.timestamp.app.viewmodel.HomeViewModel

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

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bolt, null, tint = TsColors.Neon, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "TIMESTAMP.",
                    color = TsColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                CircleIconButton(Icons.Rounded.Settings, onSettings, contentDescription = "Settings")
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Sync lyrics\nto the beat",
                color = TsColors.TextPrimary,
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "LRC  ·  TTML  ·  ELARC",
                color = TsColors.TextSecondary,
                style = MaterialTheme.typography.labelLarge
            )
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), onClick = onNew, glow = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("New project", color = TsColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Import audio and lyrics to start syncing",
                            color = TsColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(TsColors.Neon),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, "New project", tint = Color.Black, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${projects.size}", "Projects", Icons.Rounded.MusicNote, Modifier.weight(1f))
                StatCard("$synced/$total", "Lines synced", Icons.Rounded.Check, Modifier.weight(1f))
            }
        }

        item { SectionLabel("Recent projects", Modifier.padding(top = 8.dp)) }

        if (projects.isEmpty()) {
            item {
                Text(
                    "No projects yet. Tap “New project” to import a song.",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                )
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
private fun StatCard(value: String, label: String, icon: ImageVector, modifier: Modifier) {
    GlassCard(modifier) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(TsColors.Chip).align(Alignment.End),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TsColors.Neon, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(value, color = TsColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Light)
        Text(label.uppercase(), color = TsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProjectRow(p: ProjectEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val frac = if (p.totalLines > 0) p.syncedLines.toFloat() / p.totalLines else 0f
    val whenText = DateUtils.getRelativeTimeSpanString(
        p.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(TsColors.Chip),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, null, tint = TsColors.Neon)
            }
            Spacer(Modifier.width(12.dp))
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
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = TsColors.Neon,
                    trackColor = TsColors.Chip
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${p.syncedLines}/${p.totalLines} lines  ·  $whenText",
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                Icons.Rounded.Delete, onDelete,
                size = 36.dp, tint = TsColors.TextSecondary, contentDescription = "Delete project"
            )
        }
    }
}
