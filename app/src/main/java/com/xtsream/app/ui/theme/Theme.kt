package com.xtsream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * True OLED cinema-neutral dark palette inspired by native Android media apps
 * (CloudStream, YouTube, Prime Video, Metrolist) — zero blue-slate tint or glassmorphism.
 */
object AppColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0E0E10)
    val SurfaceCard = Color(0xFF161619)
    val SurfaceElevated = Color(0xFF1F1F24)
    val NavBarBg = Color(0xFF08080A)
    val Divider = Color(0xFF222227)
    val Border = Color(0xFF27272D)
    val BorderStrong = Color(0xFF3A3A42)

    val Primary = Color(0xFFFFFFFF)
    val Accent = Color(0xFFE53935)
    val Gold = Color(0xFFF5C518)
    val SubGreen = Color(0xFF4ADE80)
    val DubBlue = Color(0xFF60A5FA)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextMuted = Color(0xFF71717A)
}

private val NativeDarkScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color(0xFF000000),
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
