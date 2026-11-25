package com.xtsream.app.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Search("Search", Icons.Filled.Search, Icons.Outlined.Search),
    Library("Library", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF181C26),
            Color(0xFF262C3B),
            Color(0xFF181C26)
        ),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 300f, 220f)
    )
    background(brush)
}

fun buildCoilRequest(
    context: Context,
    url: String?,
    headers: Map<String, String> = emptyMap()
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(url)
        .crossfade(true)
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
 * Normal anchored bottom navigation bar (non-floating), edge-to-edge aware.
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
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val contentColor = if (isSelected) AppColors.Accent else AppColors.TextMuted

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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) AppColors.AccentDim else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.label,
                        color = if (isSelected) AppColors.TextPrimary else AppColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun PosterCard(
    item: MediaCardItem,
    modifier: Modifier = Modifier,
    forceHorizontal: Boolean = false,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val horizontal = forceHorizontal || item.isHorizontal
    val aspectRatio = if (horizontal) 16f / 9f else 2f / 3f

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.SurfaceCard)
                .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                var loaded by remember(item.posterUrl) { mutableStateOf(false) }
                if (!loaded) {
                    Box(modifier = Modifier.fillMaxSize().shimmerEffect())
                }
                AsyncImage(
                    model = buildCoilRequest(context, item.posterUrl, item.posterHeaders),
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    onSuccess = { loaded = true },
                    onError = { loaded = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Score badge (top-left)
            if (!item.score.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
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
                        text = item.score,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quality badge (top-right)
            if (!item.quality.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(AppColors.Accent.copy(alpha = 0.9f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.quality,
                        color = Color(0xFF071326),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

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

@Composable
fun PosterCardSkeleton(
    modifier: Modifier = Modifier,
    horizontal: Boolean = false
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (horizontal) 16f / 9f else 2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
    }
}

@Composable
fun MediaListRowCard(
    item: MediaCardItem,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceCard)
            .border(0.5.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceElevated),
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
                Icon(Icons.Filled.Movie, null, tint = AppColors.TextMuted, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.apiName.isNotBlank()) {
                    Text(
                        text = item.apiName,
                        color = AppColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (item.type.isNotBlank()) {
                    Text(
                        text = "• ${item.type}",
                        color = AppColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }
                if (!item.score.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(item.score, color = AppColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
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
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.AccentDim)
                    .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Extension,
                    contentDescription = null,
                    tint = AppColors.Accent,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "Welcome to MOVIEAPPP",
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Add a CloudStream repository (like megarepo or any repo shortname) and install an extension to browse movies, shows, and anime.",
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.SurfaceCard)
                    .border(0.5.dp, AppColors.Border, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StepRow("1", "Open Settings → Providers")
                StepRow("2", "Enter a CloudStream repo shortname or URL")
                StepRow("3", "Tap Install on any extension to load Home & Search")
            }

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onOpenProviders,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    contentColor = Color(0xFF071326)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Provider Repository", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StepRow(num: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(AppColors.AccentDim),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = AppColors.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, color = AppColors.TextPrimary, fontSize = 13.sp)
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
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = AppColors.TextMuted,
                modifier = Modifier.size(46.dp)
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
                border = BorderStroke(1.dp, AppColors.Accent),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Refresh, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry", color = AppColors.Accent, fontWeight = FontWeight.SemiBold)
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    Text("Manage", color = AppColors.Accent, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(providers, key = { it.name }) { api ->
                    val isSelected = activeProvider?.name == api.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AppColors.AccentDim else AppColors.SurfaceCard)
                            .border(
                                1.dp,
                                if (isSelected) AppColors.Accent.copy(alpha = 0.6f) else AppColors.Border,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelect(api)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = api.name,
                                color = AppColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            val meta = listOfNotNull(
                                api.lang.uppercase().takeIf { it.isNotBlank() },
                                api.supportedTypes.take(3).joinToString(" • ") { it.name }.takeIf { it.isNotBlank() }
                            ).joinToString("  |  ")
                            if (meta.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(meta, color = AppColors.TextSecondary, fontSize = 11.sp)
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = AppColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
