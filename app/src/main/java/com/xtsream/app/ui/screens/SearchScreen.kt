package com.xtsream.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import com.xtsream.app.ui.components.AppToastManager
import com.xtsream.app.ui.components.EmptyNoProvidersView
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.M3InlineSpinner
import com.xtsream.app.ui.components.M3LoadingView
import com.xtsream.app.ui.components.MediaListRowCard
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.ProviderSearchGroup
import com.xtsream.app.viewmodel.SearchDubFilter
import com.xtsream.app.viewmodel.SearchFilters
import com.xtsream.app.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    onMediaClick: (MediaCardItem) -> Unit,
    onSeeAllGroup: (ProviderSearchGroup, String) -> Unit,
    onOpenProviders: () -> Unit
) {
    val state by searchViewModel.uiState.collectAsState()
    val loadedProviders by ExtensionController.loadedProviders.collectAsState()
    val recentSearches by WatchlistStore.recentSearches.collectAsState()
    val focusManager = LocalFocusManager.current

    var showFilterSheet by remember { mutableStateOf(false) }

    // Apply live filters (title match, type, rating, dub status) to both global and single-provider results
    val filteredGlobalGroups = remember(state.globalGroups, state.filters) {
        if (!state.filters.isActive) state.globalGroups
        else state.globalGroups.mapNotNull { grp ->
            val matched = grp.items.filter { state.filters.matches(it) }
            if (matched.isEmpty()) null else grp.copy(items = matched)
        }
    }

    val filteredSingleItems = remember(state.singleProviderItems, state.filters) {
        if (!state.filters.isActive) state.singleProviderItems
        else state.singleProviderItems.filter { state.filters.matches(it) }
    }

    // Collect distinct media types from current results + common defaults for the filter sheet
    val availableTypes = remember(state.globalGroups, state.singleProviderItems) {
        val dynamicTypes = (state.globalGroups.flatMap { it.items } + state.singleProviderItems)
            .mapNotNull { it.type.takeIf { t -> t.isNotBlank() } }
            .distinct()
        val defaults = listOf("Movie", "Series", "Anime", "Drama", "OVA", "Cartoon")
        (dynamicTypes + defaults).distinct()
    }

    val selectedProviderDisplayName = remember(state.selectedProviderKey, loadedProviders) {
        state.selectedProviderKey?.let { key ->
            loadedProviders.firstOrNull { it.key == key }?.displayName
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        // Search Input Bar + Filter Trigger + Layout Toggle
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
                                text = if (selectedProviderDisplayName != null) {
                                    "Search in $selectedProviderDisplayName..."
                                } else {
                                    "Search movies, series, anime..."
                                },
                                color = AppColors.TextMuted,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

            if (loadedProviders.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Tune,
                            contentDescription = "Filters",
                            tint = if (state.filters.isActive) AppColors.Gold else AppColors.TextPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    if (state.filters.isActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(AppColors.Gold)
                        )
                    }
                }
            }

            if (state.selectedProviderKey != null && state.hasSearched) {
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

        if (loadedProviders.isEmpty()) {
            EmptyNoProvidersView(onOpenProviders = onOpenProviders)
            return
        }

        // Sleek Underline Provider Scope Bar (NO raw count numbers cluttering "All" or providers!)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item(key = "scope_all") {
                ProviderScopeTab(
                    label = "All",
                    selected = state.selectedProviderKey == null,
                    onClick = { searchViewModel.selectProviderFilter(null) }
                )
            }
            items(loadedProviders, key = { it.key }) { entry ->
                ProviderScopeTab(
                    label = entry.displayName,
                    selected = state.selectedProviderKey == entry.key,
                    onClick = { searchViewModel.selectProviderFilter(entry.key) }
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

        // Active Filter Summary Bar (only shown when at least one filter is active)
        AnimatedVisibility(visible = state.filters.isActive) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
            ) {
                if (state.filters.titleContains.isNotBlank()) {
                    item(key = "af_name") {
                        ActiveFilterTag(
                            label = "Name: \"${state.filters.titleContains}\"",
                            onClear = { searchViewModel.updateTitleFilter("") }
                        )
                    }
                }
                state.filters.selectedType?.let { type ->
                    item(key = "af_type") {
                        ActiveFilterTag(
                            label = type,
                            onClear = { searchViewModel.updateTypeFilter(null) }
                        )
                    }
                }
                state.filters.minRating?.let { rating ->
                    item(key = "af_rating") {
                        ActiveFilterTag(
                            label = "★ ${rating.toInt()}+",
                            onClear = { searchViewModel.updateRatingFilter(null) }
                        )
                    }
                }
                if (state.filters.dubFilter != SearchDubFilter.All) {
                    item(key = "af_dub") {
                        ActiveFilterTag(
                            label = state.filters.dubFilter.label,
                            onClear = { searchViewModel.updateDubFilter(SearchDubFilter.All) }
                        )
                    }
                }
                item(key = "af_reset") {
                    Text(
                        text = "Reset",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { searchViewModel.resetFilters() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        when {
            !state.hasSearched -> {
                if (state.quickSuggestions.isNotEmpty()) {
                    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(state.quickSuggestions, key = { "${it.apiKey}_${it.url}" }) { suggestion ->
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
                                    modifier = Modifier.clickable {
                                        WatchlistStore.clearRecentSearches()
                                        AppToastManager.showSuccess("Search history cleared")
                                    }
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
                                text = if (selectedProviderDisplayName != null) {
                                    "Search in $selectedProviderDisplayName"
                                } else {
                                    "Search across installed providers"
                                },
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

            state.selectedProviderKey == null -> {
                if (filteredGlobalGroups.isEmpty() && !state.isSearching && state.filters.isActive) {
                    NoFilterMatchesView(onReset = { searchViewModel.resetFilters() })
                } else {
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
                                    M3InlineSpinner(size = 18.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Searching providers...", color = AppColors.TextSecondary, fontSize = 12.sp)
                                }
                            }
                        }

                        items(filteredGlobalGroups, key = { it.providerKey }) { group ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSeeAllGroup(group, state.query) }
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.displayName,
                                        color = AppColors.TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        null,
                                        tint = AppColors.TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(group.items, key = { "${group.providerKey}_${it.url}" }) { item ->
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
            }

            else -> {
                if (filteredSingleItems.isEmpty() && !state.isSearching && state.filters.isActive) {
                    NoFilterMatchesView(onReset = { searchViewModel.resetFilters() })
                } else if (state.isGridView) {
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
                        items(filteredSingleItems, key = { "${it.apiKey}_${it.url}" }) { item ->
                            PosterCard(item = item, modifier = Modifier.fillMaxWidth(), onClick = { onMediaClick(item) })
                        }
                        if (state.isLoadingMore) {
                            item(span = { GridItemSpan(3) }) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    M3InlineSpinner(size = 26.dp)
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
                        items(filteredSingleItems, key = { "${it.apiKey}_${it.url}" }) { item ->
                            MediaListRowCard(item = item, onClick = { onMediaClick(item) })
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    M3InlineSpinner(size = 26.dp)
                                }
                            }
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
            onTitleFilterChange = { searchViewModel.updateTitleFilter(it) },
            onTypeChange = { searchViewModel.updateTypeFilter(it) },
            onRatingChange = { searchViewModel.updateRatingFilter(it) },
            onDubChange = { searchViewModel.updateDubFilter(it) },
            onReset = { searchViewModel.resetFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun ProviderScopeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.Primary else AppColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 9.dp)
        )
        Box(
            modifier = Modifier
                .width(if (selected) 28.dp else 0.dp)
                .height(2.5.dp)
                .clip(CircleShape)
                .background(if (selected) AppColors.Primary else Color.Transparent)
        )
    }
}

@Composable
private fun ActiveFilterTag(
    label: String,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceElevated)
            .clickable { onClear() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(5.dp))
        Icon(Icons.Filled.Close, "Remove filter", tint = AppColors.TextSecondary, modifier = Modifier.size(13.dp))
    }
}

@Composable
private fun NoFilterMatchesView(onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(AppIcons.Tune, null, tint = AppColors.TextMuted, modifier = Modifier.size(38.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No results match your active filters",
                color = AppColors.TextSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onReset,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Reset Filters", color = AppColors.TextPrimary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterBottomSheet(
    filters: SearchFilters,
    availableTypes: List<String>,
    onTitleFilterChange: (String) -> Unit,
    onTypeChange: (String?) -> Unit,
    onRatingChange: (Double?) -> Unit,
    onDubChange: (SearchDubFilter) -> Unit,
    onReset: () -> Unit,
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filter Results",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (filters.isActive) {
                    TextButton(onClick = onReset) {
                        Text("Reset All", color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            // 1. Title / Name contains filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TITLE CONTAINS",
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.SurfaceCard)
                        .border(0.8.dp, AppColors.Border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = filters.titleContains,
                        onValueChange = onTitleFilterChange,
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AppColors.Primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (filters.titleContains.isEmpty()) {
                                Text("Word or letters in title...", color = AppColors.TextMuted, fontSize = 13.sp)
                            }
                            inner()
                        }
                    )
                    if (filters.titleContains.isNotEmpty()) {
                        IconButton(onClick = { onTitleFilterChange("") }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, "Clear", tint = AppColors.TextMuted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // 2. Type Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TYPE",
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "type_all") {
                        FilterOptionChip(
                            label = "All",
                            selected = filters.selectedType == null,
                            onClick = { onTypeChange(null) }
                        )
                    }
                    items(availableTypes, key = { "type_$it" }) { type ->
                        FilterOptionChip(
                            label = type,
                            selected = filters.selectedType.equals(type, ignoreCase = true),
                            onClick = { onTypeChange(if (filters.selectedType == type) null else type) }
                        )
                    }
                }
            }

            // 3. Rating Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "MINIMUM RATING",
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ratingOptions = listOf<Double?>(null, 6.0, 7.0, 8.0, 9.0)
                    ratingOptions.forEach { min ->
                        val selected = filters.minRating == min
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) AppColors.Primary else AppColors.SurfaceCard)
                                .clickable { onRatingChange(min) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (min != null) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (selected) AppColors.OnPrimary else AppColors.Gold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = if (min == null) "Any" else "${min.toInt()}+",
                                color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 4. Dub Status Filter
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DUB STATUS",
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchDubFilter.entries, key = { it.name }) { opt ->
                        FilterOptionChip(
                            label = opt.label,
                            selected = filters.dubFilter == opt,
                            onClick = { onDubChange(opt) }
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FilterOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.Primary else AppColors.SurfaceCard)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
