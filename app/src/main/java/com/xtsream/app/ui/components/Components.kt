package com.xtsream.app.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lagradost.cloudstream3.MainAPI
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.ui.theme.AppColors
import kotlin.math.min

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
 * Standard Material 3 indeterminate spinner used across all loading states.
 */
@Composable
fun M3LoadingView(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size),
            color = AppColors.Primary,
            trackColor = AppColors.SurfaceElevated,
            strokeWidth = 3.dp,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun M3InlineSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = AppColors.Primary
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
        strokeWidth = 2.2.dp,
        strokeCap = StrokeCap.Round
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
 * Clean anchored bottom navigation bar (non-floating), YouTube / CloudStream / Metrolist style:
 * pure dark surface, hairline top divider, crisp white selected icon, zero glass pills.
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
 * Architectural Inverted-Fillet / Corner Cutout Shape for poster art.
 * Carves clean concave notches ONLY on corners where badge data exists,
 * so metadata integrates directly into the card's silhouette rather than floating over art.
 */
private fun notchedPosterShape(
    cornerRadiusPx: Float,
    notchRadiusPx: Float,
    topLeftNotchW: Float,
    topLeftNotchH: Float,
    topRightNotchW: Float,
    topRightNotchH: Float,
    bottomLeftNotchW: Float,
    bottomLeftNotchH: Float
): Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val r = min(cornerRadiusPx, min(w, h) / 4f)
    val nr = min(notchRadiusPx, r)

    // Clamp notch dimensions safely so adjacent corners never collide
    val tlW = if (topLeftNotchW > 0f && topLeftNotchH > 0f) min(topLeftNotchW, w * 0.46f) else 0f
    val tlH = if (tlW > 0f) min(topLeftNotchH, h * 0.25f) else 0f

    val trW = if (topRightNotchW > 0f && topRightNotchH > 0f) min(topRightNotchW, w * 0.46f) else 0f
    val trH = if (trW > 0f) min(topRightNotchH, h * 0.25f) else 0f

    val blW = if (bottomLeftNotchW > 0f && bottomLeftNotchH > 0f) min(bottomLeftNotchW, w * 0.78f) else 0f
    val blH = if (blW > 0f) min(bottomLeftNotchH, h * 0.25f) else 0f

    // Start along top edge
    if (tlW > 0f) {
        moveTo(tlW + nr, 0f)
    } else {
        moveTo(r, 0f)
    }

    // Top-right corner
    if (trW > 0f) {
        val xCut = w - trW
        lineTo(xCut - nr, 0f)
        // Convex outer fillet into vertical drop
        arcTo(Rect(xCut - 2 * nr, 0f, xCut, 2 * nr), -90f, 90f, false)
        lineTo(xCut, trH - nr)
        // Concave inner corner turning right
        arcTo(Rect(xCut, trH - 2 * nr, xCut + 2 * nr, trH), 180f, -90f, false)
        lineTo(w - nr, trH)
        // Convex fillet turning down along right edge
        arcTo(Rect(w - 2 * nr, trH, w, trH + 2 * nr), -90f, 90f, false)
    } else {
        lineTo(w - r, 0f)
        arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
    }

    // Right edge -> Bottom-right smooth corner
    lineTo(w, h - r)
    arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)

    // Bottom edge -> Bottom-left corner (optional SUB/DUB cutout)
    if (blW > 0f) {
        lineTo(blW + nr, h)
        // Convex fillet going up into notch
        arcTo(Rect(blW, h - 2 * nr, blW + 2 * nr, h), 90f, 90f, false)
        lineTo(blW, h - blH + nr)
        // Concave inner corner turning left
        arcTo(Rect(blW - 2 * nr, h - blH, blW, h - blH + 2 * nr), 0f, -90f, false)
        lineTo(nr, h - blH)
        // Convex fillet turning up along left edge
        arcTo(Rect(0f, h - blH - 2 * nr, 2 * nr, h - blH), 90f, 90f, false)
    } else {
        lineTo(r, h)
        arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
    }

    // Left edge -> Top-left corner (optional rating cutout)
    if (tlW > 0f) {
        lineTo(0f, tlH + nr)
        // Convex fillet turning right into notch
        arcTo(Rect(0f, tlH, 2 * nr, tlH + 2 * nr), 180f, 90f, false)
        lineTo(tlW - nr, tlH)
        // Concave inner corner turning up
        arcTo(Rect(tlW - 2 * nr, tlH - 2 * nr, tlW, tlH), 90f, -90f, false)
        lineTo(tlW, nr)
        // Convex fillet returning to top edge
        arcTo(Rect(tlW, 0f, tlW + 2 * nr, 2 * nr), 180f, 90f, false)
    } else {
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
    }

    close()
}

/**
 * Notched Corner-Cutout Poster Card:
 * Dynamically carves inverted-radius corner cutouts ONLY for metadata returned by the provider
 * (Rating top-left, Type/Quality top-right, Sub/Dub or Episode count bottom-left).
 */
@Composable
fun PosterCard(
    item: MediaCardItem,
    modifier: Modifier = Modifier,
    forceHorizontal: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val horizontal = forceHorizontal || item.isHorizontal
    val aspectRatio = if (horizontal) 16f / 9f else 2f / 3f

    val ratingText = item.score?.takeIf { it.isNotBlank() }
    val typeText = item.shortType.takeIf { it.isNotBlank() }
        ?: item.quality?.takeIf { it.isNotBlank() }
        ?: item.type.takeIf { it.isNotBlank() }?.uppercase()?.take(6)

    val hasBottomData = item.hasSub || item.hasDub || (item.episodeCount != null && item.episodeCount > 0)

    var tlSize by remember(ratingText) { mutableStateOf(IntSize.Zero) }
    var trSize by remember(typeText) { mutableStateOf(IntSize.Zero) }
    var blSize by remember(item.hasSub, item.hasDub, item.subEpisodes, item.dubEpisodes, item.episodeCount) {
        mutableStateOf(IntSize.Zero)
    }

    val cornerPx = with(density) { 12.dp.toPx() }
    val notchPx = with(density) { 8.dp.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }

    val posterShape = remember(tlSize, trSize, blSize, cornerPx, notchPx) {
        notchedPosterShape(
            cornerRadiusPx = cornerPx,
            notchRadiusPx = notchPx,
            topLeftNotchW = if (tlSize.width > 0) tlSize.width + gapPx else 0f,
            topLeftNotchH = if (tlSize.height > 0) tlSize.height + gapPx else 0f,
            topRightNotchW = if (trSize.width > 0) trSize.width + gapPx else 0f,
            topRightNotchH = if (trSize.height > 0) trSize.height + gapPx else 0f,
            bottomLeftNotchW = if (blSize.width > 0) blSize.width + gapPx else 0f,
            bottomLeftNotchH = if (blSize.height > 0) blSize.height + gapPx else 0f
        )
    }

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
            // Poster image clipped to the dynamic corner-cutout shape
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(posterShape)
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

            // Top-Left Cutout Slot: Rating
            if (ratingText != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onSizeChanged { tlSize = it }
                        .padding(end = 3.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = AppColors.Gold,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = ratingText,
                        color = AppColors.TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Top-Right Cutout Slot: Type (MOVIE / TV / ANIME / etc.)
            if (typeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .onSizeChanged { trSize = it }
                        .padding(start = 3.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = typeText,
                        color = AppColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                }
            }

            // Bottom-Left Cutout Slot: SUB / DUB / Episodes
            if (hasBottomData) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .onSizeChanged { blSize = it }
                        .padding(top = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (item.hasSub) {
                        Text(
                            text = if (item.subEpisodes != null) "SUB ${item.subEpisodes}" else "SUB",
                            color = AppColors.SubGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                    if (item.hasDub) {
                        Text(
                            text = if (item.dubEpisodes != null) "DUB ${item.dubEpisodes}" else "DUB",
                            color = AppColors.DubBlue,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                    if (!item.hasSub && !item.hasDub && item.episodeCount != null) {
                        Text(
                            text = "EP ${item.episodeCount}",
                            color = AppColors.TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
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
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
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
                    .clip(RoundedCornerShape(7.dp))
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
                    contentColor = Color.Black
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
    providers: List<MainAPI>,
    activeProvider: MainAPI?,
    onSelect: (MainAPI) -> Unit,
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
                    Text("Manage", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(providers, key = { it.name }) { api ->
                    val isSelected = activeProvider?.name == api.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(api)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = api.name,
                                color = if (isSelected) AppColors.Primary else AppColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            val meta = listOfNotNull(
                                api.lang.uppercase().takeIf { it.isNotBlank() },
                                api.supportedTypes.take(3).joinToString(" • ") { it.name }.takeIf { it.isNotBlank() }
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
