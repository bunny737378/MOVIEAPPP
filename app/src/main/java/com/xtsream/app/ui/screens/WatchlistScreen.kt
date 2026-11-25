package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.AppToastManager
import com.xtsream.app.ui.components.MediaListRowCard
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.theme.AppColors

@Composable
fun WatchlistScreen(
    onMediaClick: (MediaCardItem, Rect?) -> Unit
) {
    val watchlist by WatchlistStore.watchlist.collectAsState()
    val recentViewed by WatchlistStore.recentlyViewed.collectAsState()
    var activeTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Library",
                color = AppColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (activeTab == 1 && recentViewed.isNotEmpty()) {
                TextButton(
                    onClick = {
                        WatchlistStore.clearRecentlyViewed()
                        AppToastManager.showSuccess("Cleared recent history")
                    }
                ) {
                    Text("Clear", color = AppColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Clean Underline Tabs
        Row(modifier = Modifier.fillMaxWidth()) {
            LibraryUnderlineTab(
                label = "Watchlist (${watchlist.size})",
                selected = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 0 }
            )
            LibraryUnderlineTab(
                label = "Recent (${recentViewed.size})",
                selected = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 1 }
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

        if (activeTab == 0) {
            if (watchlist.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(AppIcons.Bookmarks, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Watchlist is Empty", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Bookmark any title to keep it saved here.",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(watchlist, key = { "${it.apiKey}_${it.url}" }) { item ->
                        PosterCard(
                            item = item,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { bounds -> onMediaClick(item, bounds) }
                        )
                    }
                }
            }
        } else {
            if (recentViewed.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(AppIcons.History, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Recent History", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Titles you open appear here automatically.",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recentViewed, key = { "${it.apiKey}_${it.url}" }) { item ->
                        MediaListRowCard(
                            item = item,
                            onClick = { onMediaClick(item, null) },
                            trailingContent = {
                                if (watchlist.any { it.url == item.url }) {
                                    IconButton(
                                        onClick = {
                                            WatchlistStore.removeFromWatchlist(item.url)
                                            AppToastManager.showSuccess("Removed from Watchlist")
                                        }
                                    ) {
                                        Icon(Icons.Filled.Delete, "Remove", tint = AppColors.TextMuted, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryUnderlineTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = if (selected) AppColors.Primary else AppColors.TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(2.dp)
                .background(if (selected) AppColors.Primary else Color.Transparent)
        )
    }
}
