package com.timestamp.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timestamp.app.ui.theme.LocalGlow
import com.timestamp.app.ui.theme.TsColors

/** Flat dark panel with a hairline border. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    var m = modifier
        .clip(shape)
        .background(TsColors.Card)
        .border(1.dp, TsColors.Border, shape)
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
    val shape = RoundedCornerShape(14.dp)
    val glow = LocalGlow.current && enabled
    Row(
        modifier
            .then(
                if (glow) Modifier.shadow(10.dp, shape, ambientColor = TsColors.Neon, spotColor = TsColors.Neon)
                else Modifier
            )
            .clip(shape)
            .background(if (enabled) TsColors.Neon else TsColors.Chip)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
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
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .clip(shape)
            .background(TsColors.Chip)
            .border(1.dp, TsColors.FieldBorder, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val fg = if (enabled) TsColors.TextPrimary else TsColors.TextSecondary.copy(alpha = 0.5f)
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/** Rounded-square icon button. */
@Composable
fun TsIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = TsColors.TextPrimary,
    background: Color = TsColors.Chip,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(background)
            .border(1.dp, TsColors.Border, shape)
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
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) TsColors.Neon.copy(alpha = 0.16f) else TsColors.Chip)
            .border(1.dp, if (selected) TsColors.Neon.copy(alpha = 0.7f) else TsColors.Border, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                selected -> TsColors.Neon
                enabled -> TsColors.TextSecondary
                else -> TsColors.TextSecondary.copy(alpha = 0.4f)
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/** Small mono pill used for seek buttons (-5s, +0.1 ...). */
@Composable
fun SeekPill(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .width(50.dp)
            .height(38.dp)
            .clip(shape)
            .background(TsColors.Chip)
            .border(1.dp, TsColors.Border, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TsColors.TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, color = TsColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
}

@Composable
fun StepHeader(number: Int, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(TsColors.Neon),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = TsColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            TsIconButton(Icons.AutoMirrored.Rounded.ArrowBack, onBack, contentDescription = "Back")
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TsColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = TsColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
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
        shape = RoundedCornerShape(22.dp),
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
