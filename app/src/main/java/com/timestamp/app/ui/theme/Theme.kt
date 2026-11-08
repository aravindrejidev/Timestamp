package com.timestamp.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TsColors {
    val Neon = Color(0xFF00E676)
    val NeonSoft = Color(0xFF69F0AE)
    val BgTop = Color(0xFF121212)
    val BgBottom = Color(0xFF0D0D0D)
    val Card = Color(0xFF161618)
    val CardTop = Color(0xFF1E1E21)
    val Chip = Color(0xFF212125)
    val Border = Color(0x26FFFFFF)
    val FieldBorder = Color(0x40FFFFFF)
    val Amber = Color(0xFFFFB74D)
    val TextPrimary = Color(0xFFF2F2F2)
    val TextSecondary = Color(0xFF9A9AA0)
    val Danger = Color(0xFFFF5252)
}

val LocalGlow = compositionLocalOf { true }
val LocalBackgroundBrush = compositionLocalOf {
    Brush.verticalGradient(listOf(TsColors.BgTop, TsColors.BgBottom))
}

val TsTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp)
)

@Composable
fun TimestampTheme(amoled: Boolean, glow: Boolean, content: @Composable () -> Unit) {
    val brush = if (amoled) {
        Brush.verticalGradient(listOf(Color.Black, Color.Black))
    } else {
        Brush.verticalGradient(listOf(TsColors.BgTop, TsColors.BgBottom))
    }
    val scheme = darkColorScheme(
        primary = TsColors.Neon,
        onPrimary = Color.Black,
        secondary = TsColors.NeonSoft,
        onSecondary = Color.Black,
        background = if (amoled) Color.Black else TsColors.BgBottom,
        onBackground = TsColors.TextPrimary,
        surface = TsColors.Card,
        onSurface = TsColors.TextPrimary,
        surfaceVariant = TsColors.CardTop,
        onSurfaceVariant = TsColors.TextSecondary,
        outline = TsColors.Border,
        error = TsColors.Danger,
        surfaceContainer = TsColors.CardTop,
        surfaceContainerHigh = TsColors.CardTop,
        surfaceContainerHighest = TsColors.Chip
    )
    CompositionLocalProvider(LocalGlow provides glow, LocalBackgroundBrush provides brush) {
        MaterialTheme(colorScheme = scheme, typography = TsTypography, content = content)
    }
}
