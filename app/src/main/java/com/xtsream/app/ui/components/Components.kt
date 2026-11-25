package com.xtsream.app.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.engine.ProviderEntry
import com.xtsream.app.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Search("Search", Icons.Filled.Search, Icons.Outlined.Search),
    Library("Library", AppIcons.Bookmarks, AppIcons.BookmarksOutlined),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/**
 * Material 3 Expressive Loading Indicator (Normal variant):
 * Smoothly rotating, continuously morphing rounded organic polygon sequence
 * (Soft Burst -> Rounded Clover -> Scalloped Pentagon -> Pill -> Rounded Star)
 * matching https://m3.material.io/components/loading-indicator/overview
 */
@Composable
fun M3ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color = AppColors.Primary
) {
    val transition = rememberInfiniteTransition(label = "m3_loading")
    // Continuous morph timeline across 5 signature M3 Expressive shapes
    val morphPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3_morph"
    )
    // Gentle continuous rotation combined with springy step impulses
    val baseRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3_rot"
    )

    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val maxR = this.size.minDimension * 0.44f

        val stage = morphPhase.toInt().coerceIn(0, 4)
        val rawFrac = morphPhase - stage
        // Expressive springy ease-in-out curve for crisp shape-holding & snappy morphs
        val t = smoothExpressiveStep(rawFrac)
        val nextStage = (stage + 1) % 5

        val stepAngle = (stage + t) * 72f
        val angleDeg = (baseRotation * 0.5f + stepAngle * 0.5f) % 360f

        val samples = 72
        val path = Path()
        for (i in 0 until samples) {
            val theta = (2.0 * PI * i) / samples
            val r0 = m3ExpressiveShapeRadius(stage, theta)
            val r1 = m3ExpressiveShapeRadius(nextStage, theta)
            val r = (r0 + (r1 - r0) * t).toFloat() * maxR
            val x = cx + (r * cos(theta)).toFloat()
            val y = cy + (r * sin(theta)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        rotate(degrees = angleDeg, pivot = Offset(cx, cy)) {
            drawPath(path = path, color = color)
        }
    }
}

private fun smoothExpressiveStep(x: Float): Float {
    val clamped = x.coerceIn(0f, 1f)
    // Pause briefly on each target shape, then snap smoothly into the next
    val remapped = ((clamped - 0.12f) / 0.76f).coerceIn(0f, 1f)
    // Cubic Hermite with slight expressive punch
    return remapped * remapped * (3f - 2f * remapped)
}

/**
 * Polar radius r(theta) in [0.68..1.05] for Material 3 Expressive's morphing polygon sequence:
 * 0: Soft Burst (8-lobe flower)
 * 1: Clover (4-lobe quatrefoil)
 * 2: Soft Pentagon (5-lobe rounded polygon)
 * 3: Squircle / Rounded Pill (2-lobe oval)
 * 4: Scalloped Cookie (6-lobe bloom)
 */
private fun m3ExpressiveShapeRadius(stage: Int, theta: Double): Double {
    return when (stage) {
        0 -> 0.86 + 0.14 * cos(8.0 * theta)
        1 -> 0.80 + 0.20 * cos(4.0 * theta)
        2 -> 0.88 + 0.11 * cos(5.0 * theta)
        3 -> {
            // Smooth rounded pill / oval
            val a = 1.04
            val b = 0.72
            val c = cos(theta)
            val s = sin(theta)
            1.0 / ((c / a).pow(4) + (s / b).pow(4)).pow(0.25)
        }
        else -> 0.84 + 0.16 * cos(6.0 * theta)
    }
}

/**
 * Material 3 Expressive Contained Loading Indicator (Contained variant):
 * Morphing polygon centered inside a circular tonal container.
 */
@Composable
fun M3ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerSize: Dp = 50.dp,
    indicatorSize: Dp = 32.dp,
    containerColor: Color = AppColors.SurfaceElevated,
    indicatorColor: Color = AppColors.Primary
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        M3ExpressiveLoadingIndicator(
            size = indicatorSize,
            color = indicatorColor
        )
    }
}

/**
 * Full-screen Material 3 Contained Loading Indicator.
 */
@Composable
fun M3LoadingView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        M3ContainedLoadingIndicator()
    }
}

/**
 * Inline Material 3 Normal (Uncontained) Loading Indicator.
 */
@Composable
fun M3InlineSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    color: Color = AppColors.Primary
) {
    M3ExpressiveLoadingIndicator(
        modifier = modifier,
        size = size,
        color = color
    )
}

fun buildCoilRequest(
    context: Context,
    url: String?,
    headers: Map<String, String> = emptyMap()
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(url)
        .crossfade(220)
    if (headers.isNotEmpty()) {
        val nh = NetworkHeaders.Builder().apply {
            headers.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) set(k, v)
            }
        }.build()
        builder.httpHeaders(nh)
    }
    return builder.build()
}

/**
 * Clean anchored bottom navigation bar (non-floating), soft charcoal dark surface.
 */
@Composable
fun AppBottomBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.NavBarBg)
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val tint = if (isSelected) AppColors.Primary else AppColors.TextMuted

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectTab(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(23.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Draws a crisp concave inverted-fillet flare matching the notch background color.
 * cornerPosition:
 *  - "TR_LEFT": flare to the left of a Top-Right notch (concave faces bottom-left)
 *  - "TR_BOTTOM": flare below a Top-Right notch (concave faces bottom-left)
 *  - "BL_TOP": flare above a Bottom-Left notch (concave faces top-right)
 *  - "BL_RIGHT": flare to the right of a Bottom-Left notch (concave faces top-right)
 */
@Composable
private fun ConcaveCornerFlare(
    flareSize: Dp,
    bgColor: Color,
    concaveAtBottomLeft: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(flareSize)) {
        val s = this.size.minDimension
        val path = Path()
        if (concaveAtBottomLeft) {
            // Solid corner sits at Top-Right; concave curve faces Bottom-Left
            path.moveTo(0f, 0f)
            path.lineTo(s, 0f)
            path.lineTo(s, s)
            path.quadraticTo(s, 0f, 0f, 0f)
        } else {
            // Solid corner sits at Bottom-Left; concave curve faces Top-Right
            path.moveTo(0f, 0f)
            path.lineTo(0f, s)
            path.lineTo(s, s)
            path.quadraticTo(0f, s, 0f, 0f)
        }
        path.close()
        drawPath(path = path, color = bgColor)
    }
}

/**
 * Refined Cutout / Concave-Flare Poster Card:
 * - Rounded poster art with seamless background-colored corner notches & concave flares
 * - Top-Right notch (only if score exists): warm gold rating pill with star icon
 * - Bottom-Left notch (only if sub/dub/ep/type exists): stacked colored pills (CC sub + Mic dub joined pair, crimson type pill)
 */
@Composable
fun PosterCard(
    item: MediaCardItem,
    modifier: Modifier = Modifier,
    forceHorizontal: Boolean = false,
    notchBgColor: Color = AppColors.Background,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val horizontal = forceHorizontal || item.isHorizontal
    val aspectRatio = if (horizontal) 16f / 9f else 2f / 3f

    val ratingText = item.score?.takeIf { it.isNotBlank() }
    val typeText = item.shortType.takeIf { it.isNotBlank() }
        ?: item.type.takeIf { it.isNotBlank() }?.uppercase()?.take(7)
        ?: item.quality?.takeIf { it.isNotBlank() }

    val hasAudioOrEp = item.hasSub || item.hasDub || (item.episodeCount != null && item.episodeCount > 0)
    val hasBottomNotch = hasAudioOrEp || typeText != null

    val cardRadius = 14.dp
    val notchGap = 3.dp
    val flareSize = 10.dp
    val badgeHeight = 17.dp
    val innerCutRadius = 11.dp

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        ) {
            // Base rounded poster image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cardRadius))
                    .background(AppColors.SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = buildCoilRequest(context, item.posterUrl, item.posterHeaders),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = AppIcons.Movie,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Top-Right Concave Rating Notch (only when rating exists)
            if (ratingText != null) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        ConcaveCornerFlare(
                            flareSize = flareSize,
                            bgColor = notchBgColor,
                            concaveAtBottomLeft = true
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(bottomStart = innerCutRadius))
                                .background(notchBgColor)
                                .padding(start = notchGap, bottom = notchGap)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(badgeHeight)
                                    .clip(RoundedCornerShape(50))
                                    .background(AppColors.Gold)
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = AppColors.OnGold,
                                    modifier = Modifier.size(9.dp)
                                )
                                Text(
                                    text = ratingText,
                                    color = AppColors.OnGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    ConcaveCornerFlare(
                        flareSize = flareSize,
                        bgColor = notchBgColor,
                        concaveAtBottomLeft = true
                    )
                }
            }

            // Bottom-Left Concave Info Notch (Sub/Dub/EP + Type pills — only when data exists)
            if (hasBottomNotch) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    horizontalAlignment = Alignment.Start
                ) {
                    ConcaveCornerFlare(
                        flareSize = flareSize,
                        bgColor = notchBgColor,
                        concaveAtBottomLeft = false
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topEnd = innerCutRadius))
                                .background(notchBgColor)
                                .padding(top = notchGap, end = notchGap)
                        ) {
                            Column(
                                modifier = Modifier.width(IntrinsicSize.Max),
                                verticalArrangement = Arrangement.spacedBy(2.5.dp)
                            ) {
                                if (hasAudioOrEp) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val bothAudio = item.hasSub && item.hasDub
                                        if (item.hasSub) {
                                            val subShape = if (bothAudio) {
                                                RoundedCornerShape(
                                                    topStart = 9.dp, bottomStart = 9.dp,
                                                    topEnd = 3.dp, bottomEnd = 3.dp
                                                )
                                            } else {
                                                RoundedCornerShape(50)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f, fill = bothAudio)
                                                    .height(badgeHeight)
                                                    .clip(subShape)
                                                    .background(AppColors.SubPill)
                                                    .padding(horizontal = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.ClosedCaption,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(9.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = item.subEpisodes?.toString() ?: "SUB",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    lineHeight = 8.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (item.hasDub) {
                                            val dubShape = if (bothAudio) {
                                                RoundedCornerShape(
                                                    topStart = 3.dp, bottomStart = 3.dp,
                                                    topEnd = 9.dp, bottomEnd = 9.dp
                                                )
                                            } else {
                                                RoundedCornerShape(50)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f, fill = bothAudio)
                                                    .height(badgeHeight)
                                                    .clip(dubShape)
                                                    .background(AppColors.DubPill)
                                                    .padding(horizontal = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = AppIcons.Mic,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(9.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = item.dubEpisodes?.toString() ?: "DUB",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    lineHeight = 8.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        if (!item.hasSub && !item.hasDub && item.episodeCount != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(badgeHeight)
                                                    .clip(RoundedCornerShape(50))
                                                    .background(AppColors.EpisodePill)
                                                    .padding(horizontal = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "EP ${item.episodeCount}",
                                                    color = AppColors.TextPrimary,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    lineHeight = 8.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                if (typeText != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minWidth = 44.dp)
                                            .height(16.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(AppColors.TypePill)
                                            .padding(horizontal = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = typeText,
                                            color = Color.White,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.5.sp,
                                            lineHeight = 8.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        ConcaveCornerFlare(
                            flareSize = flareSize,
                            bgColor = notchBgColor,
                            concaveAtBottomLeft = false
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = item.title,
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/**
 * Episode Thumbnail with matching Concave Corner Cutout for Episode Number (and optional Rating).
 */
@Composable
fun NotchedEpisodeThumbnail(
    imageUrl: String?,
    headers: Map<String, String>,
    episodeNumber: Int?,
    score: String? = null,
    notchBgColor: Color = AppColors.Background,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val flareSize = 8.dp
    val notchGap = 2.5.dp

    Box(modifier = modifier.aspectRatio(16f / 9f)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(11.dp))
                .background(AppColors.SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = buildCoilRequest(context, imageUrl, headers),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(AppIcons.Movie, null, tint = AppColors.TextMuted, modifier = Modifier.size(22.dp))
            }
        }

        // Optional Top-Right Score Cutout
        if (!score.isNullOrBlank()) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    ConcaveCornerFlare(flareSize = flareSize, bgColor = notchBgColor, concaveAtBottomLeft = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(bottomStart = 9.dp))
                            .background(notchBgColor)
                            .padding(start = notchGap, bottom = notchGap)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(16.dp)
                                .clip(RoundedCornerShape(50))
                                .background(AppColors.Gold)
                                .padding(horizontal = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(Icons.Filled.Star, null, tint = AppColors.OnGold, modifier = Modifier.size(8.dp))
                            Text(
                                text = score,
                                color = AppColors.OnGold,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 9.sp
                            )
                        }
                    }
                }
                ConcaveCornerFlare(flareSize = flareSize, bgColor = notchBgColor, concaveAtBottomLeft = true)
            }
        }

        // Bottom-Left Episode Number Cutout
        if (episodeNumber != null) {
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalAlignment = Alignment.Start
            ) {
                ConcaveCornerFlare(flareSize = flareSize, bgColor = notchBgColor, concaveAtBottomLeft = false)
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topEnd = 9.dp))
                            .background(notchBgColor)
                            .padding(top = notchGap, end = notchGap)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(17.dp)
                                .clip(RoundedCornerShape(50))
                                .background(AppColors.EpisodePill)
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "EP $episodeNumber",
                                color = AppColors.TextPrimary,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.3.sp,
                                lineHeight = 9.sp
                            )
                        }
                    }
                    ConcaveCornerFlare(flareSize = flareSize, bgColor = notchBgColor, concaveAtBottomLeft = false)
                }
            }
        }
    }
}

/**
 * Clean borderless editorial list row (YouTube / Prime Video style — no heavy card boxes).
 */
@Composable
fun MediaListRowCard(
    item: MediaCardItem,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = buildCoilRequest(context, item.posterUrl, item.posterHeaders),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(AppIcons.Movie, null, tint = AppColors.TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!item.score.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(item.score, color = AppColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val subline = listOfNotNull(
                        item.type.takeIf { it.isNotBlank() },
                        item.year?.takeIf { it > 0 }?.toString(),
                        if (item.hasSub && item.hasDub) "Sub & Dub"
                        else if (item.hasSub) "Sub"
                        else if (item.hasDub) "Dub"
                        else null,
                        item.apiName.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (subline.isNotBlank()) {
                        Text(
                            text = subline,
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 86.dp, end = 16.dp),
            thickness = 0.5.dp,
            color = AppColors.Divider
        )
    }
}

@Composable
fun EmptyNoProvidersView(
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = AppIcons.Extension,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "No Providers Installed",
                color = AppColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add a CloudStream repository or .cs3 extension in Providers to browse movies, shows, and anime.",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenProviders,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) {
                Text("Open Providers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = AppIcons.CloudOff,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedButton(
                onClick = onRetry,
                border = BorderStroke(1.dp, AppColors.BorderStrong),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPickerSheet(
    providers: List<ProviderEntry>,
    activeProvider: ProviderEntry?,
    onSelect: (ProviderEntry) -> Unit,
    onManageProviders: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.BorderStrong) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Switch Provider",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    onDismiss()
                    onManageProviders()
                }) {
                    Text("Manage", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(providers, key = { it.key }) { entry ->
                    val isSelected = activeProvider?.key == entry.key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(entry)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.displayName,
                                color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            val meta = listOfNotNull(
                                if (entry.isDuplicateName) entry.sourceLabel else null,
                                entry.api.lang.uppercase().takeIf { it.isNotBlank() },
                                entry.api.supportedTypes.take(3).joinToString(" • ") { it.name }.takeIf { it.isNotBlank() }
                            ).joinToString("  •  ")
                            if (meta.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(meta, color = AppColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = AppColors.Primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = AppColors.Divider
                    )
                }
            }
        }
    }
}
