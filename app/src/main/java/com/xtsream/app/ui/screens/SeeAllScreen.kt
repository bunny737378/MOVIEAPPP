package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.M3InlineSpinner
import com.xtsream.app.ui.components.MediaListRowCard
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.SearchDubFilter
import com.xtsream.app.viewmodel.SeeAllSource
import com.xtsream.app.viewmodel.SeeAllViewModel

@Composable
fun SeeAllScreen(
    source: SeeAllSource,
    onBack: () -> Unit,
    onMediaClick: (MediaCardItem, Rect?) -> Unit
) {
    val key = remember(source) {
        when (source) {
            is SeeAllSource.HomeCategory -> "seeall_home_${source.apiKey}_${source.title}"
            is SeeAllSource.SearchProvider -> "seeall_search_${source.apiKey}_${source.query}"
        }
    }
    val vm: SeeAllViewModel = viewModel(key = key)
    LaunchedEffect(source) { vm.init(source) }

    val state by vm.uiState.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val filteredItems = remember(state.items, state.filters) {
        if (!state.filters.isActive) state.items
        else state.items.filter { state.filters.matches(it) }
    }

    val availableTypes = remember(state.items) {
        state.items.mapNotNull { it.type.trim().takeIf { t -> t.isNotBlank() } }.distinct()
    }
    val hasAnyRating = remember(state.items) {
        state.items.any { it.score?.toDoubleOrNull() != null }
    }
    val hasAnySubDub = remember(state.items) {
        state.items.any { it.hasSub || it.hasDub }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.Primary)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = state.title,
                    color = AppColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.subtitle.isNotBlank()) {
                    Text(
                        text = state.subtitle,
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(
                        imageVector = AppIcons.Tune,
                        contentDescription = "Filters",
                        tint = if (state.filters.isActive) AppColors.Gold else AppColors.Primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                if (state.filters.isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp, end = 10.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AppColors.Gold)
                    )
                }
            }

            IconButton(onClick = { vm.toggleLayoutMode() }) {
                Icon(
                    imageVector = if (state.isGridView) AppIcons.ViewList else AppIcons.GridView,
                    contentDescription = "Toggle view",
                    tint = AppColors.Primary
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

        ActiveFilterSummaryBar(
            filters = state.filters,
            onClearTitle = { vm.updateTitleFilter("") },
            onClearType = { vm.updateTypeFilter(null) },
            onClearRating = { vm.updateRatingFilter(null) },
            onClearDub = { vm.updateDubFilter(SearchDubFilter.All) },
            onReset = { vm.resetFilters() }
        )

        if (filteredItems.isEmpty() && state.filters.isActive) {
            NoFilterMatchesView(onReset = { vm.resetFilters() })
        } else if (state.isGridView) {
            val gridState = rememberLazyGridState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    gridState.layoutInfo.totalItemsCount > 0 && last >= gridState.layoutInfo.totalItemsCount - 4
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) vm.loadMore()
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                gridItemsIndexed(filteredItems, key = { idx, it -> "${idx}_${it.apiKey}_${it.url}" }) { _, item ->
                    PosterCard(item = item, modifier = Modifier.fillMaxWidth(), onClick = { bounds -> onMediaClick(item, bounds) })
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(3) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            M3InlineSpinner(size = 24.dp)
                        }
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    listState.layoutInfo.totalItemsCount > 0 && last >= listState.layoutInfo.totalItemsCount - 3
                }
            }
            LaunchedEffect(shouldLoadMore) {
                if (shouldLoadMore) vm.loadMore()
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(filteredItems, key = { idx, it -> "${idx}_${it.apiKey}_${it.url}" }) { _, item ->
                    MediaListRowCard(item = item, onClick = { onMediaClick(item, null) })
                }
                if (state.isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            M3InlineSpinner(size = 24.dp)
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        SearchFilterBottomSheet(
            filters = state.filters,
            availableTypes = availableTypes,
            hasAnyRating = hasAnyRating,
            hasAnySubDub = hasAnySubDub,
            onTitleFilterChange = { vm.updateTitleFilter(it) },
            onTypeChange = { vm.updateTypeFilter(it) },
            onRatingChange = { vm.updateRatingFilter(it) },
            onDubChange = { vm.updateDubFilter(it) },
            onReset = { vm.resetFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}
