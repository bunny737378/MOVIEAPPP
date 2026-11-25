package com.xtsream.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.xtsream.app.engine.ProviderEntry
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.AppToastManager
import com.xtsream.app.ui.components.EmptyNoProvidersView
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.M3ContainedLoadingIndicator
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
    barsVisible: Boolean = true,
    onBarsVisibilityChange: (Boolean) -> Unit = {},
    onMediaClick: (MediaCardItem, Rect?) -> Unit,
    onSeeAllSection: (HomeSection) -> Unit,
    onOpenProviders: () -> Unit,
    onSearchActiveProvider: (ProviderEntry) -> Unit
) {
    val state by homeViewModel.uiState.collectAsState()
    val loadedProviders by ExtensionController.loadedProviders.collectAsState()
    val activeProvider by ExtensionController.activeProvider.collectAsState()
    val isBooting by ExtensionController.isBooting.collectAsState()
    val watchlist by WatchlistStore.watchlist.collectAsState()

    var showProviderPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val hasHero = !isBooting && loadedProviders.isNotEmpty() && !state.isLoading && state.featuredItems.isNotEmpty()

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 36
        }
    }

    val isScrolledPastHero by remember(hasHero) {
        derivedStateOf {
            if (!hasHero) true
            else listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 300
        }
    }

    LaunchedEffect(isAtTop) {
        if (isAtTop) onBarsVisibilityChange(true)
    }

    LaunchedEffect(activeProvider?.key) {
        onBarsVisibilityChange(true)
    }

    val barsVisibleState by rememberUpdatedState(barsVisible)
    val onBarsChangeState by rememberUpdatedState(onBarsVisibilityChange)

    val scrollConnection = remember {
        object : NestedScrollConnection {
            private var accumulated = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (kotlin.math.abs(dy) < 0.5f) return Offset.Zero

                if (dy < 0f) {
                    // Scrolling down -> hide both top bar & bottom nav bar
                    if (accumulated > 0f) accumulated = 0f
                    accumulated += dy
                    if (accumulated < -30f && barsVisibleState &&
                        (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 64)
                    ) {
                        onBarsChangeState(false)
                        accumulated = 0f
                    }
                } else {
                    // Scrolling up slightly -> restore both bars
                    if (accumulated < 0f) accumulated = 0f
                    accumulated += dy
                    if (accumulated > 14f && !barsVisibleState) {
                        onBarsChangeState(true)
                        accumulated = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    val solidHeaderBg by animateColorAsState(
        targetValue = if (isScrolledPastHero) AppColors.NavBarBg else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "home_header_bg"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        when {
            isBooting -> {
                M3LoadingView()
            }

            loadedProviders.isEmpty() -> {
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
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    state = pullState,
                    isRefreshing = state.isRefreshing,
                    onRefresh = { homeViewModel.refresh() },
                    indicator = {
                        val progress = pullState.distanceFraction.coerceIn(0f, 1.2f)
                        if (state.isRefreshing || progress > 0.08f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                                    .padding(top = 56.dp)
                                    .graphicsLayer {
                                        val s = if (state.isRefreshing) 1f else progress.coerceIn(0.3f, 1f)
                                        scaleX = s
                                        scaleY = s
                                        alpha = if (state.isRefreshing) 1f else progress.coerceIn(0f, 1f)
                                    }
                            ) {
                                M3ContainedLoadingIndicator(
                                    containerSize = 44.dp,
                                    indicatorSize = 26.dp
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollConnection)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (state.featuredItems.isNotEmpty()) {
                            item(key = "hero_banner") {
                                FullBleedHeroSlider(
                                    items = state.featuredItems,
                                    watchlistUrls = watchlist.map { it.url }.toSet(),
                                    onDetailsClick = { item -> onMediaClick(item, null) },
                                    onToggleBookmark = { item ->
                                        val added = WatchlistStore.toggleWatchlist(item)
                                        AppToastManager.showSuccess(
                                            if (added) "Added to My List" else "Removed from My List"
                                        )
                                    }
                                )
                            }
                        } else {
                            item(key = "top_spacer") {
                                Spacer(modifier = Modifier.statusBarsPadding().height(56.dp))
                            }
                        }

                        itemsIndexed(state.sections, key = { idx, s -> "${idx}_${s.id}" }) { _, section ->
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

        // Auto-hiding Top Bar — transparent gradient over top hero, smoothly solid AppColors.NavBarBg when scrolled past hero
        if (loadedProviders.isNotEmpty()) {
            AnimatedVisibility(
                visible = barsVisible,
                enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(200)) + fadeIn(tween(180)),
                exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(200)) + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isScrolledPastHero) {
                                Brush.verticalGradient(listOf(solidHeaderBg, solidHeaderBg))
                            } else {
                                Brush.verticalGradient(
                                    listOf(AppColors.Background.copy(alpha = 0.78f), Color.Transparent)
                                )
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                text = activeProvider?.displayName ?: "Select Provider",
                                color = AppColors.Primary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp)
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
                            onClick = { activeProvider?.let { onSearchActiveProvider(it) } },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Search this provider",
                                tint = AppColors.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (isScrolledPastHero) {
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
                    }
                }
            }
        }
    }

    if (showProviderPicker) {
        ProviderPickerSheet(
            providers = loadedProviders,
            activeProvider = activeProvider,
            onSelect = { ExtensionController.selectProvider(it) },
            onManageProviders = onOpenProviders,
            onDismiss = { showProviderPicker = false }
        )
    }
}

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
                .height(450.dp)
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

                    // Seamless cinema fade into charcoal background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to AppColors.Background.copy(alpha = 0.58f),
                                        0.28f to Color.Transparent,
                                        0.56f to AppColors.Background.copy(alpha = 0.45f),
                                        0.85f to AppColors.Background.copy(alpha = 0.92f),
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
                                    contentColor = AppColors.OnPrimary
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
    onMediaClick: (MediaCardItem, Rect?) -> Unit,
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
            itemsIndexed(section.items, key = { idx, it -> "${idx}_${section.id}_${it.url}" }) { _, item ->
                PosterCard(
                    item = item,
                    forceHorizontal = section.isHorizontal,
                    modifier = Modifier.width(cardWidth),
                    onClick = { bounds -> onMediaClick(item, bounds) }
                )
            }
        }
    }
}
