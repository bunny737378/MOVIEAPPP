package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.ui.components.MediaListRowCard
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.theme.AppColors

@Composable
fun WatchlistScreen(
    onMediaClick: (MediaCardItem) -> Unit
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
                TextButton(onClick = { WatchlistStore.clearRecentlyViewed() }) {
                    Text("Clear History", color = AppColors.Danger, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Clean segmented tab control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.SurfaceCard)
                .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
                .padding(3.dp)
        ) {
            SegmentTab(
                label = "Watchlist (${watchlist.size})",
                selected = activeTab == 0,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 0 }
            )
            SegmentTab(
                label = "Recent (${recentViewed.size})",
                selected = activeTab == 1,
                modifier = Modifier.weight(1f),
                onClick = { activeTab = 1 }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Border)

        if (activeTab == 0) {
            if (watchlist.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.Bookmarks, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Your Watchlist is Empty", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tap the bookmark icon on any movie or series to save it here.",
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(watchlist, key = { it.url }) { item ->
                        PosterCard(
                            item = item,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onMediaClick(item) }
                        )
                    }
                }
            }
        } else {
            if (recentViewed.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Icon(Icons.Filled.History, null, tint = AppColors.TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Recently Viewed Titles", color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Titles you open will appear here for quick access.",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(recentViewed, key = { it.url }) { item ->
                        MediaListRowCard(
                            item = item,
                            onClick = { onMediaClick(item) },
                            trailingContent = {
                                if (watchlist.any { it.url == item.url }) {
                                    IconButton(onClick = { WatchlistStore.removeFromWatchlist(item.url) }) {
                                        Icon(Icons.Filled.DeleteOutline, "Remove", tint = AppColors.TextMuted)
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
private fun SegmentTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.Accent else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF071326) else AppColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
