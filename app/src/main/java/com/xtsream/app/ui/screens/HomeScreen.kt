package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xtsream.app.data.HomeSection
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.EmptyNoProvidersView
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.M3LoadingView
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.components.ProviderPickerSheet
import com.xtsream.app.ui.components.buildCoilRequest
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onMediaClick: (MediaCardItem) -> Unit,
    onSeeAllSection: (HomeSection) -> Unit,
    onOpenProviders: () -> Unit
) {
    val state by homeViewModel.uiState.collectAsState()
    val loadedApis by ExtensionController.loadedApis.collectAsState()
    val activeApi by ExtensionController.activeApi.collectAsState()
    val isBooting by ExtensionController.isBooting.collectAsState()
    val watchlist by WatchlistStore.watchlist.collectAsState()

    var showProviderPicker by remember { mutableStateOf(false) }

    val hasHero = !isBooting && loadedApis.isNotEmpty() && !state.isLoading && state.featuredItems.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        when {
            isBooting -> {
                M3LoadingView()
            }

            loadedApis.isEmpty() -> {
                EmptyNoProvidersView(onOpenProviders = onOpenProviders)
            }

            state.isLoading && state.sections.isEmpty() -> {
                M3LoadingView()
            }

            state.errorMessage != null && state.sections.isEmpty() -> {
                ErrorStateView(
                    message = state.errorMessage!!,
                    onRetry = { homeViewModel.retry() }
                )
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { homeViewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (state.featuredItems.isNotEmpty()) {
                            item(key = "hero_banner") {
                                FullBleedHeroSlider(
                                    items = state.featuredItems,
                                    watchlistUrls = watchlist.map { it.url }.toSet(),
                                    onDetailsClick = onMediaClick,
                                    onToggleBookmark = { WatchlistStore.toggleWatchlist(it) }
                                )
                            }
                        } else {
                            item(key = "top_spacer") {
                                Spacer(modifier = Modifier.statusBarsPadding().height(56.dp))
                            }
                        }

                        items(state.sections, key = { it.id }) { section ->
                            HomeSectionRow(
                                section = section,
                                onMediaClick = onMediaClick,
                                onSeeAllClick = { onSeeAllSection(section) }
                            )
                        }
                    }
                }
            }
        }

        // Clean overlay top bar (transparent over hero scrim, pitch black otherwise)
        if (loadedApis.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (hasHero) {
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                            )
                        } else {
                            Brush.verticalGradient(listOf(AppColors.Background, AppColors.Background))
                        }
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Provider selector button directly on bar (clean, zero clutter)
                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showProviderPicker = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeApi?.name ?: "Select Provider",
                        color = AppColors.Primary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Switch provider",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { homeViewModel.refresh() },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = AppColors.Primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }

    if (showProviderPicker) {
        ProviderPickerSheet(
            providers = loadedApis,
            activeProvider = activeApi,
            onSelect = { ExtensionController.selectProvider(it) },
            onManageProviders = onOpenProviders,
            onDismiss = { showProviderPicker = false }
        )
    }
}

/**
 * Full-bleed edge-to-edge cinematic Hero Slider (Prime Video / CloudStream / Netflix style).
 */
@Composable
private fun FullBleedHeroSlider(
    items: List<MediaCardItem>,
    watchlistUrls: Set<String>,
    onDetailsClick: (MediaCardItem) -> Unit,
    onToggleBookmark: (MediaCardItem) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.size) {
        if (items.size > 1) {
            while (true) {
                delay(6000)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % items.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(455.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = items[page]
                val isSaved = item.url in watchlistUrls

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppColors.Surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDetailsClick(item) }
                ) {
                    if (!item.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = buildCoilRequest(context, item.posterUrl, item.posterHeaders),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Seamless cinema fade into OLED black
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = 0.55f),
                                        0.28f to Color.Transparent,
                                        0.58f to Color.Black.copy(alpha = 0.45f),
                                        0.86f to Color(0xEB000000),
                                        1.0f to AppColors.Background
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.title,
                            color = AppColors.Primary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 29.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Clean dot-separated metadata line (no cluttered pill spam)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!item.score.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(item.score, color = AppColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            val metaParts = buildList {
                                item.type.takeIf { it.isNotBlank() }?.let { add(it) }
                                item.year?.takeIf { it > 0 }?.let { add(it.toString()) }
                                item.quality?.takeIf { it.isNotBlank() }?.let { add(it) }
                                if (item.hasSub && item.hasDub) add("Sub & Dub")
                                else if (item.hasSub) add("Sub")
                                else if (item.hasDub) add("Dub")
                            }
                            if (metaParts.isNotEmpty()) {
                                Text(
                                    text = metaParts.joinToString("  •  "),
                                    color = AppColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onDetailsClick(item) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.Primary,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Icon(Icons.Filled.Info, null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(7.dp))
                                Text("Details", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = { onToggleBookmark(item) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.SurfaceElevated,
                                    contentColor = AppColors.Primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSaved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                                    contentDescription = null,
                                    tint = if (isSaved) AppColors.Primary else AppColors.TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isSaved) "Saved" else "My List",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(3.dp)
                            .width(if (selected) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (selected) AppColors.Primary else AppColors.BorderStrong)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSectionRow(
    section: HomeSection,
    onMediaClick: (MediaCardItem) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = section.mainPageData != null && section.items.size >= 4) {
                    onSeeAllClick()
                }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                color = AppColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (section.mainPageData != null && section.items.size >= 4) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "See all",
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val cardWidth = if (section.isHorizontal) 196.dp else 118.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(section.items, key = { "${section.id}_${it.url}" }) { item ->
                PosterCard(
                    item = item,
                    forceHorizontal = section.isHorizontal,
                    modifier = Modifier.width(cardWidth),
                    onClick = { onMediaClick(item) }
                )
            }
        }
    }
}
