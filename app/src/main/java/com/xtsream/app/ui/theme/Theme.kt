package com.xtsream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Refined neutral dark charcoal-grey palette inspired by YouTube Dark, Metrolist, and CloudStream —
 * softer than pitch OLED black, zero generic blue-slate cast or glassmorphism.
 */
object AppColors {
    val Background = Color(0xFF121215)
    val Surface = Color(0xFF1A1A1F)
    val SurfaceCard = Color(0xFF23232A)
    val SurfaceElevated = Color(0xFF2C2C35)
    val NavBarBg = Color(0xFF16161B)
    val Divider = Color(0xFF2A2A32)
    val Border = Color(0xFF34343E)
    val BorderStrong = Color(0xFF474754)

    val Primary = Color(0xFFF5F5F7)
    val OnPrimary = Color(0xFF121215)
    val Accent = Color(0xFFD92632)

    // Poster cutout badge accents (high-contrast, balanced)
    val Gold = Color(0xFFF5BE18)
    val OnGold = Color(0xFF161209)
    val SubPill = Color(0xFFF5F5F7)
    val DubPill = Color(0xFFF5F5F7)
    val AudioBadgeInk = Color(0xFF111114)
    val TypePill = Color(0xFF2F2F3B)
    val EpisodePill = Color(0xFF2F2F3B)

    val SubGreen = Color(0xFF4ADE80)
    val DubBlue = Color(0xFF60A5FA)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)

    val TextPrimary = Color(0xFFF4F4F6)
    val TextSecondary = Color(0xFFA8A8B3)
    val TextMuted = Color(0xFF787885)
}

private val NativeDarkScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.OnPrimary,
    secondary = AppColors.Accent,
    onSecondary = Color.White,
    background = AppColors.Background,
    surface = AppColors.Surface,
    surfaceVariant = AppColors.SurfaceCard,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Border
)

@Composable
fun MovieAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NativeDarkScheme,
        content = content
    )
}
