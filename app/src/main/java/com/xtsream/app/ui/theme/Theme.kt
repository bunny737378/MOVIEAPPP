package com.xtsream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Background = Color(0xFF0B0C10)
    val Surface = Color(0xFF12141C)
    val SurfaceCard = Color(0xFF171A24)
    val SurfaceElevated = Color(0xFF1F2433)
    val NavBarBg = Color(0xFF0E1016)
    val Border = Color(0x1AFFFFFF)
    val BorderStrong = Color(0x33FFFFFF)

    val Accent = Color(0xFF6EA8FE)
    val AccentDim = Color(0x266EA8FE)
    val Gold = Color(0xFFF5C451)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)

    val TextPrimary = Color(0xFFF4F6FA)
    val TextSecondary = Color(0xFFA0A8B8)
    val TextMuted = Color(0xFF687082)
}

private val CinemaDarkScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color(0xFF071326),
    secondary = AppColors.Gold,
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
        colorScheme = CinemaDarkScheme,
        content = content
    )
}
