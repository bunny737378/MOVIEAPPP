package com.xtsream.app.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
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

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Search("Search", Icons.Filled.Search, Icons.Outlined.Search),
    Library("Library", AppIcons.Bookmarks, AppIcons.BookmarksOutlined),
    Providers("Providers", AppIcons.Extension, AppIcons.Extension),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

/**
 * Official Material 3 Expressive LoadingIndicator (Normal / Uncontained variant).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    color: Color = AppColors.Primary
) {
    LoadingIndicator(
        modifier = modifier.size(size),
        color = color
    )
}

/**
 * Official Material 3 Expressive ContainedLoadingIndicator (Contained variant).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun M3ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerSize: Dp = 48.dp,
    indicatorSize: Dp = 32.dp,
    containerColor: Color = AppColors.SurfaceElevated,
    indicatorColor: Color = AppColors.Primary
) {
    ContainedLoadingIndicator(
        modifier = modifier.size(containerSize),
        containerColor = containerColor,
        indicatorColor = indicatorColor
    )
}

/**
 * Full-screen official Material 3 ContainedLoadingIndicator.
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
 * Inline official Material 3 Normal (Uncontained) LoadingIndicator.
 */
@Composable
fun M3InlineSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
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
            path.moveTo(0f, 0f)
            path.lineTo(s, 0f)
            path.lineTo(s, s)
            path.quadraticTo(s, 0f, 0f, 0f)
        } else {
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
 * - Top-Right notch (only if rating exists): warm gold rating pill with star icon
 * - Bottom-Left notch: ALWAYS occupies exact half-poster width (same width Type uses),
 *   stacking Sub/Dub cleanly directly above Type:
 *   - No Sub & No Dub: normal fully-rounded Type pill only
 *   - Both Sub & Dub: top split into Sub & Dub boxes (icon+count if count exists; larger centered icon ONLY if count missing, NO "SUB"/"DUB" text) over Type box
 *   - Single Sub OR Single Dub: top box spans exact same width as Type with rounded top & FLAT bottom, Type below has FLAT top & rounded bottom!
 */
@Composable
fun PosterCard(
    item: MediaCardItem,
    modifier: Modifier = Modifier,
    forceHorizontal: Boolean = false,
    notchBgColor: Color = AppColors.Background,
    onClick: (Rect?) -> Unit
) {
    val context = LocalContext.current
    val horizontal = forceHorizontal || item.isHorizontal
    val aspectRatio = if (horizontal) 16f / 9f else 2f / 3f

    val ratingText = item.score?.takeIf { it.isNotBlank() }
    val typeText = item.shortType.takeIf { it.isNotBlank() }
        ?: item.type.takeIf { it.isNotBlank() }?.uppercase()?.take(7)
        ?: item.quality?.takeIf { it.isNotBlank() }

    val hasSubOrDub = item.hasSub || item.hasDub
    val hasEpOnly = !hasSubOrDub && (item.episodeCount != null && item.episodeCount > 0)
    val hasTopRow = hasSubOrDub || hasEpOnly
    val hasBottomNotch = hasTopRow || typeText != null

    val cardRadius = 14.dp
    val notchGap = 3.dp
    val flareSize = 10.dp
    val badgeHeight = 17.dp
    val typeHeight = 16.dp
    val innerCutRadius = 11.dp

    var posterBounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick(posterBounds) }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .onGloballyPositioned { coords ->
                    posterBounds = coords.boundsInRoot()
                }
        ) {
            // Bottom-left stack width is ALWAYS fixed to half the poster width
            val notchStackWidth = (maxWidth * 0.50f).coerceAtLeast(48.dp)

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

            // Bottom-Left Concave Info Notch (Fixed half-poster width stack)
            if (hasBottomNotch) {
                val hasTypeBelow = typeText != null
                val bothAudio = item.hasSub && item.hasDub
                val singleTopBox = hasTopRow && !bothAudio

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
                                modifier = Modifier.width(notchStackWidth),
                                verticalArrangement = Arrangement.spacedBy(if (singleTopBox && hasTypeBelow) 1.5.dp else 2.dp)
                            ) {
                                if (hasTopRow) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (bothAudio) {
                                            // Split 50/50 within the exact same half-poster width
                                            val subShape = RoundedCornerShape(
                                                topStart = 8.dp,
                                                topEnd = 3.dp,
                                                bottomStart = if (hasTypeBelow) 2.dp else 8.dp,
                                                bottomEnd = if (hasTypeBelow) 2.dp else 3.dp
                                            )
                                            val dubShape = RoundedCornerShape(
                                                topStart = 3.dp,
                                                topEnd = 8.dp,
                                                bottomStart = if (hasTypeBelow) 2.dp else 3.dp,
                                                bottomEnd = if (hasTypeBelow) 2.dp else 8.dp
                                            )
                                            AudioBadgeBox(
                                                icon = AppIcons.ClosedCaption,
                                                count = item.subEpisodes,
                                                bgColor = AppColors.SubPill,
                                                shape = subShape,
                                                height = badgeHeight,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AudioBadgeBox(
                                                icon = AppIcons.Mic,
                                                count = item.dubEpisodes,
                                                bgColor = AppColors.DubPill,
                                                shape = dubShape,
                                                height = badgeHeight,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else if (item.hasSub) {
                                            // Single Sub box: rounded top, FLAT bottom when stacked over Type
                                            val shape = if (hasTypeBelow) {
                                                RoundedCornerShape(
                                                    topStart = 8.dp,
                                                    topEnd = 8.dp,
                                                    bottomStart = 0.dp,
                                                    bottomEnd = 0.dp
                                                )
                                            } else {
                                                RoundedCornerShape(50)
                                            }
                                            AudioBadgeBox(
                                                icon = AppIcons.ClosedCaption,
                                                count = item.subEpisodes,
                                                bgColor = AppColors.SubPill,
                                                shape = shape,
                                                height = badgeHeight,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else if (item.hasDub) {
                                            // Single Dub box: rounded top, FLAT bottom when stacked over Type
                                            val shape = if (hasTypeBelow) {
                                                RoundedCornerShape(
                                                    topStart = 8.dp,
                                                    topEnd = 8.dp,
                                                    bottomStart = 0.dp,
                                                    bottomEnd = 0.dp
                                                )
                                            } else {
                                                RoundedCornerShape(50)
                                            }
                                            AudioBadgeBox(
                                                icon = AppIcons.Mic,
                                                count = item.dubEpisodes,
                                                bgColor = AppColors.DubPill,
                                                shape = shape,
                                                height = badgeHeight,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else if (hasEpOnly && item.episodeCount != null) {
                                            val shape = if (hasTypeBelow) {
                                                RoundedCornerShape(
                                                    topStart = 8.dp,
                                                    topEnd = 8.dp,
                                                    bottomStart = 0.dp,
                                                    bottomEnd = 0.dp
                                                )
                                            } else {
                                                RoundedCornerShape(50)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(badgeHeight)
                                                    .clip(shape)
                                                    .background(AppColors.EpisodePill)
                                                    .padding(horizontal = 4.dp),
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
                                    // Type box shape:
                                    // - Standalone (no top row): fully rounded pill
                                    // - Single Sub/Dub/EP above: FLAT top, rounded bottom
                                    // - Both Sub+Dub above: tight flat-ish top, rounded bottom
                                    val typeShape = when {
                                        !hasTopRow -> RoundedCornerShape(50)
                                        singleTopBox -> RoundedCornerShape(
                                            topStart = 0.dp,
                                            topEnd = 0.dp,
                                            bottomStart = 8.dp,
                                            bottomEnd = 8.dp
                                        )
                                        else -> RoundedCornerShape(
                                            topStart = 2.dp,
                                            topEnd = 2.dp,
                                            bottomStart = 8.dp,
                                            bottomEnd = 8.dp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(typeHeight)
                                            .clip(typeShape)
                                            .background(AppColors.TypePill)
                                            .border(0.6.dp, Color(0xFF2E2E36), typeShape)
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = typeText,
                                            color = Color.White,
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.4.sp,
                                            lineHeight = 8.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip
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
 * White Sub/Dub mini badge with crisp dark ink inside the half-poster-width notch.
 * Automatically scales icon, spacing, and font size down so even 4-digit counts (e.g. 1120)
 * fit cleanly without clipping.
 */
@Composable
private fun AudioBadgeBox(
    icon: ImageVector,
    count: Int?,
    bgColor: Color,
    shape: RoundedCornerShape,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val ink = AppColors.AudioBadgeInk
    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        val boxWidthDp = maxWidth.value
        if (count != null && count > 0) {
            val countText = count.toString()
            val digits = countText.length
            val isTight = boxWidthDp < 36f

            val initialFontSp = when {
                digits >= 4 && isTight -> 5.8f
                digits >= 4 -> 6.8f
                digits == 3 && isTight -> 6.8f
                digits == 3 -> 7.4f
                else -> 8.0f
            }
            val iconDp = when {
                digits >= 4 && isTight -> 6.0.dp
                digits >= 4 -> 7.0.dp
                digits == 3 && isTight -> 7.2.dp
                else -> 8.5.dp
            }
            val gapDp = when {
                digits >= 4 && isTight -> 1.dp
                digits >= 3 -> 1.5.dp
                else -> 2.dp
            }
            val hPadDp = if (digits >= 4 && isTight) 1.dp else 2.dp

            var fittedFontSp by remember(countText, boxWidthDp) { mutableFloatStateOf(initialFontSp) }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPadDp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(iconDp)
                )
                Spacer(modifier = Modifier.width(gapDp))
                Text(
                    text = countText,
                    color = ink,
                    style = TextStyle(
                        fontSize = fittedFontSp.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = fittedFontSp.sp,
                        letterSpacing = if (digits >= 4) (-0.3).sp else 0.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    onTextLayout = { result ->
                        if (result.didOverflowWidth && fittedFontSp > 4.2f) {
                            fittedFontSp = (fittedFontSp - 0.5f).coerceAtLeast(4.2f)
                        }
                    }
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(11.5.dp)
            )
        }
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
                                if (entry.isStremio) "Stremio" else if (entry.isDuplicateName) entry.sourceLabel else null,
                                entry.api.lang.uppercase().takeIf { it.isNotBlank() && !entry.isStremio },
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
