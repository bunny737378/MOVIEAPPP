package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.EmptyNoProvidersView
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.M3InlineSpinner
import com.xtsream.app.ui.components.M3LoadingView
import com.xtsream.app.ui.components.MediaListRowCard
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.ProviderSearchGroup
import com.xtsream.app.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    onMediaClick: (MediaCardItem) -> Unit,
    onSeeAllGroup: (ProviderSearchGroup, String) -> Unit,
    onOpenProviders: () -> Unit
) {
    val state by searchViewModel.uiState.collectAsState()
    val loadedApis by ExtensionController.loadedApis.collectAsState()
    val recentSearches by WatchlistStore.recentSearches.collectAsState()
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        // Search Input Bar (clean flat YouTube/CloudStream style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SurfaceCard)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = state.query,
                    onValueChange = { searchViewModel.onQueryChange(it) },
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(AppColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            searchViewModel.executeSearch()
                        }
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (state.query.isEmpty()) {
                            Text(
                                text = "Search movies, series, anime...",
                                color = AppColors.TextMuted,
                                fontSize = 15.sp
                            )
                        }
                        inner()
                    }
                )
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = { searchViewModel.clearQuery() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Filled.Close, "Clear", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (state.selectedProviderFilter != null && state.hasSearched) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { searchViewModel.toggleLayoutMode() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (state.isGridView) AppIcons.ViewList else AppIcons.GridView,
                        contentDescription = "Toggle layout",
                        tint = AppColors.Primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        if (loadedApis.isEmpty()) {
            EmptyNoProvidersView(onOpenProviders = onOpenProviders)
            return
        }

        // Clean Provider Filter Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            item(key = "filter_all") {
                val selected = state.selectedProviderFilter == null
                SearchScopeChip(
                    label = "All (${loadedApis.size})",
                    selected = selected,
                    onClick = { searchViewModel.selectProviderFilter(null) }
                )
            }
            items(loadedApis, key = { it.name }) { api ->
                SearchScopeChip(
                    label = api.name,
                    selected = state.selectedProviderFilter == api.name,
                    onClick = { searchViewModel.selectProviderFilter(api.name) }
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

        when {
            !state.hasSearched -> {
                if (state.quickSuggestions.isNotEmpty()) {
                    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(state.quickSuggestions, key = { it.url }) { suggestion ->
                            MediaListRowCard(item = suggestion, onClick = { onMediaClick(suggestion) })
                        }
                    }
                } else if (recentSearches.isNotEmpty()) {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent",
                                    color = AppColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Clear",
                                    color = AppColors.TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable { WatchlistStore.clearRecentSearches() }
                                )
                            }
                        }
                        items(recentSearches, key = { it }) { recent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        focusManager.clearFocus()
                                        searchViewModel.executeSearch(recent)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(AppIcons.History, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = recent,
                                    color = AppColors.TextPrimary,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { WatchlistStore.removeRecentSearch(recent) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "Remove", tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Search, null, tint = AppColors.TextMuted, modifier = Modifier.size(42.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Search across ${loadedApis.size} installed provider(s)",
                                color = AppColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            state.isSearching && state.globalGroups.isEmpty() && state.singleProviderItems.isEmpty() -> {
                M3LoadingView()
            }

            state.errorMessage != null && state.globalGroups.isEmpty() && state.singleProviderItems.isEmpty() -> {
                ErrorStateView(
                    message = state.errorMessage!!,
                    onRetry = { searchViewModel.executeSearch() }
                )
            }

            state.selectedProviderFilter == null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    if (state.isSearching) {
                        item(key = "searching_banner") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                M3InlineSpinner(size = 14.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Searching more providers...", color = AppColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    items(state.globalGroups, key = { it.apiName }) { group ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeeAllGroup(group, state.query) }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = group.apiName,
                                    color = AppColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${group.items.size}",
                                    color = AppColors.TextMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    null,
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(group.items, key = { "${group.apiName}_${it.url}" }) { item ->
                                    PosterCard(
                                        item = item,
                                        modifier = Modifier.width(116.dp),
                                        onClick = { onMediaClick(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                if (state.isGridView) {
                    val gridState = rememberLazyGridState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            gridState.layoutInfo.totalItemsCount > 0 && last >= gridState.layoutInfo.totalItemsCount - 4
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) searchViewModel.loadMoreSingleProvider()
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.singleProviderItems, key = { it.url }) { item ->
                            PosterCard(item = item, modifier = Modifier.fillMaxWidth(), onClick = { onMediaClick(item) })
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
                        if (shouldLoadMore) searchViewModel.loadMoreSingleProvider()
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.singleProviderItems, key = { it.url }) { item ->
                            MediaListRowCard(item = item, onClick = { onMediaClick(item) })
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
        }
    }
}

@Composable
private fun SearchScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.Primary else AppColors.SurfaceCard)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else AppColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
