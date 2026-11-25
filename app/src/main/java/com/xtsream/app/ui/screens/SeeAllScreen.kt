package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.ui.components.MediaListRowCard
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.SeeAllSource
import com.xtsream.app.viewmodel.SeeAllViewModel

@Composable
fun SeeAllScreen(
    source: SeeAllSource,
    onBack: () -> Unit,
    onMediaClick: (MediaCardItem) -> Unit
) {
    val key = remember(source) {
        when (source) {
            is SeeAllSource.HomeCategory -> "seeall_home_${source.apiName}_${source.title}"
            is SeeAllSource.SearchProvider -> "seeall_search_${source.apiName}_${source.query}"
        }
    }
    val vm: SeeAllViewModel = viewModel(key = key)
    LaunchedEffect(source) { vm.init(source) }

    val state by vm.uiState.collectAsState()

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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextPrimary)
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
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = { vm.toggleLayoutMode() }) {
                Icon(
                    imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = "Toggle view",
                    tint = AppColors.TextPrimary
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)

        if (state.isGridView) {
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.items, key = { it.url }) { item ->
                    PosterCard(item = item, modifier = Modifier.fillMaxWidth(), onClick = { onMediaClick(item) })
                }
                if (state.isLoadingMore) {
                    item(span = { GridItemSpan(3) }) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.items, key = { it.url }) { item ->
                    MediaListRowCard(item = item, onClick = { onMediaClick(item) })
                }
                if (state.isLoadingMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
