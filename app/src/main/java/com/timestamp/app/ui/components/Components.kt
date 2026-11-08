package com.timestamp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.timestamp.app.ui.Routes
import com.timestamp.app.ui.theme.LocalGlow
import com.timestamp.app.ui.theme.TsColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    var m = modifier
    if (glow && LocalGlow.current) {
        m = m.shadow(
            18.dp, shape,
            ambientColor = TsColors.Neon.copy(alpha = 0.5f),
            spotColor = TsColors.Neon.copy(alpha = 0.5f)
        )
    }
    m = m.clip(shape)
        .background(Brush.verticalGradient(listOf(TsColors.CardTop, TsColors.Card)))
        .border(
            1.dp,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.03f))),
            shape
        )
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    val glow = LocalGlow.current && enabled
    Row(
        modifier
            .then(
                if (glow) Modifier.shadow(14.dp, shape, ambientColor = TsColors.Neon, spotColor = TsColors.Neon)
                else Modifier
            )
            .clip(shape)
            .background(if (enabled) TsColors.Neon else TsColors.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (enabled) Color.Black else TsColors.TextSecondary
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(TsColors.Chip)
            .border(1.dp, TsColors.FieldBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (enabled) TsColors.TextPrimary else TsColors.TextSecondary
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = TsColors.TextPrimary,
    background: Color = TsColors.Chip,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, TsColors.Border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun TsChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Color.White else TsColors.Chip)
            .border(1.dp, if (selected) Color.White else TsColors.Border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                selected -> Color.Black
                enabled -> TsColors.TextSecondary
                else -> TsColors.TextSecondary.copy(alpha = 0.4f)
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = TsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
}

@Composable
fun tsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TsColors.TextPrimary,
    unfocusedTextColor = TsColors.TextPrimary,
    focusedBorderColor = TsColors.Neon,
    unfocusedBorderColor = TsColors.FieldBorder,
    cursorColor = TsColors.Neon,
    focusedLabelColor = TsColors.Neon,
    unfocusedLabelColor = TsColors.TextSecondary,
    focusedPlaceholderColor = TsColors.TextSecondary,
    unfocusedPlaceholderColor = TsColors.TextSecondary,
    focusedContainerColor = Color(0x0DFFFFFF),
    unfocusedContainerColor = Color(0x0DFFFFFF)
)

@Composable
fun TsDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String?,
    onConfirm: () -> Unit = {},
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TsColors.CardTop,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = TsColors.TextPrimary) },
        text = { Column(content = content) },
        confirmButton = {
            if (confirmText != null) {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(confirmText, color = if (confirmEnabled) TsColors.Neon else TsColors.TextSecondary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (confirmText != null) "Cancel" else "Done", color = TsColors.TextSecondary)
            }
        }
    )
}

private data class DockItem(val route: String, val icon: ImageVector, val label: String, val enabled: Boolean)

/** Floating round-button dock (like the reference UI). */
@Composable
fun FloatingDock(current: String?, studioEnabled: Boolean, onNavigate: (String) -> Unit) {
    val items = listOf(
        DockItem(Routes.HOME, Icons.Rounded.Home, "Home", true),
        DockItem(Routes.IMPORT, Icons.Rounded.LibraryAdd, "New project", true),
        DockItem(Routes.STUDIO, Icons.Rounded.GraphicEq, "Studio", studioEnabled),
        DockItem(Routes.EXPORT, Icons.Rounded.Download, "Export", studioEnabled),
        DockItem(Routes.SETTINGS, Icons.Rounded.Settings, "Settings", true)
    )
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(40.dp)
        Row(
            Modifier
                .clip(shape)
                .background(Color(0xE61A1A1C))
                .border(1.dp, TsColors.Border, shape)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                val selected = current == item.route
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else TsColors.Chip)
                        .clickable(enabled = item.enabled) { onNavigate(item.route) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon, item.label,
                        tint = when {
                            selected -> Color.Black
                            item.enabled -> TsColors.TextSecondary
                            else -> TsColors.TextSecondary.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
