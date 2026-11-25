package com.xtsream.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.xtsream.app.data.HomeSection
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.ui.components.EmptyNoProvidersView
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.components.PosterCardSkeleton
import com.xtsream.app.ui.components.ProviderPickerSheet
import com.xtsream.app.ui.components.buildCoilRequest
import com.xtsream.app.ui.components.shimmerEffect
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MOVIEAPPP",
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            if (loadedApis.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.SurfaceCard)
                        .border(0.5.dp, AppColors.BorderStrong, RoundedCornerShape(10.dp))
                        .clickable { showProviderPicker = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AppColors.Success)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeApi?.name ?: "Select Provider",
                        color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Switch provider",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { homeViewModel.refresh() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)

        when {
            isBooting -> {
                HomeSkeletonContent()
            }

            loadedApis.isEmpty() -> {
                EmptyNoProvidersView(onOpenProviders = onOpenProviders)
            }

            state.isLoading && state.sections.isEmpty() -> {
                HomeSkeletonContent()
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
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        if (state.featuredItems.isNotEmpty()) {
                            item(key = "hero_carousel") {
                                FeaturedHeroCarousel(
                                    items = state.featuredItems,
                                    watchlistUrls = watchlist.map { it.url }.toSet(),
                                    onDetailsClick = onMediaClick,
                                    onToggleBookmark = { WatchlistStore.toggleWatchlist(it) }
                                )
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

@Composable
private fun FeaturedHeroCarousel(
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
                delay(5500)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % items.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val item = items[page]
            val isSaved = item.url in watchlistUrls

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.SurfaceCard)
                    .border(0.5.dp, AppColors.BorderStrong, RoundedCornerShape(16.dp))
                    .clickable { onDetailsClick(item) }
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = buildCoilRequest(context, item.posterUrl, item.posterHeaders),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Cinematic gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.15f),
                                    0.45f to Color.Black.copy(alpha = 0.35f),
                                    0.85f to Color(0xE60B0C10),
                                    1.0f to Color(0xFF0B0C10)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.type.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppColors.AccentDim)
                                    .border(0.5.dp, AppColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.type.uppercase(),
                                    color = AppColors.Accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (!item.score.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(item.score, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!item.quality.isNullOrBlank()) {
                            Text(
                                text = item.quality,
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onDetailsClick(item) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0B0C10)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Filled.Info, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { onToggleBookmark(item) },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isSaved) AppColors.Accent else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSaved) "Saved" else "Watchlist",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
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
                            .height(5.dp)
                            .width(if (selected) 18.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (selected) AppColors.Accent else AppColors.TextMuted.copy(alpha = 0.45f))
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
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSeeAllClick() }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See all",
                        color = AppColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val cardWidth = if (section.isHorizontal) 186.dp else 116.dp
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

@Composable
private fun HomeSkeletonContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .shimmerEffect()
            )
        }
        items(3) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .width(140.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    repeat(3) {
                        PosterCardSkeleton(modifier = Modifier.width(116.dp))
                    }
                }
            }
        }
    }
}
