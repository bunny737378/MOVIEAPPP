package com.xtsream.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.MetaActorItem
import com.xtsream.app.data.MetaEpisodeItem
import com.xtsream.app.data.MetaUiData
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.components.buildCoilRequest
import com.xtsream.app.ui.components.shimmerEffect
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.MetaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class EpisodeLayoutMode { Detailed, Compact, Grid }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetaScreen(
    item: MediaCardItem,
    onBack: () -> Unit,
    onMediaClick: (MediaCardItem) -> Unit
) {
    val vm: MetaViewModel = viewModel(key = "meta_${item.url}")
    LaunchedEffect(item.url) { vm.load(item) }

    val state by vm.uiState.collectAsState()
    val watchlist by WatchlistStore.watchlist.collectAsState()
    val isSaved = remember(watchlist, item.url) { watchlist.any { it.url == item.url } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        when {
            state.isLoading -> {
                MetaSkeletonView(onBack = onBack)
            }

            state.errorMessage != null || state.data == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
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
                        Text(
                            text = item.title,
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ErrorStateView(
                        message = state.errorMessage ?: "Failed to load metadata.",
                        onRetry = { vm.load(item, force = true) }
                    )
                }
            }

            else -> {
                val meta = state.data!!
                MetaLoadedContent(
                    meta = meta,
                    fallbackItem = item,
                    isSaved = isSaved,
                    onBack = onBack,
                    onToggleWatchlist = {
                        val saveItem = MediaCardItem(
                            title = meta.title,
                            url = meta.url,
                            apiName = meta.apiName,
                            posterUrl = meta.posterUrl ?: item.posterUrl,
                            posterHeaders = meta.posterHeaders.ifEmpty { item.posterHeaders },
                            type = meta.typeLabel,
                            quality = item.quality,
                            score = meta.score ?: item.score
                        )
                        WatchlistStore.toggleWatchlist(saveItem)
                    },
                    onMediaClick = onMediaClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaLoadedContent(
    meta: MetaUiData,
    fallbackItem: MediaCardItem,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onMediaClick: (MediaCardItem) -> Unit
) {
    val context = LocalContext.current

    var plotExpanded by remember(meta.url) { mutableStateOf(false) }
    var selectedDub by remember(meta.url) { mutableStateOf(meta.defaultDubKey) }
    var selectedSeasonIndex by remember(meta.url, selectedDub) { mutableIntStateOf(0) }
    var selectedRangeIndex by remember(meta.url, selectedDub, selectedSeasonIndex) { mutableIntStateOf(0) }
    var episodeLayout by remember { mutableStateOf(EpisodeLayoutMode.Detailed) }
    var selectedEpisodeForSheet by remember { mutableStateOf<MetaEpisodeItem?>(null) }

    val seasons = remember(meta.dubStatusMap, selectedDub) {
        meta.dubStatusMap[selectedDub].orEmpty()
    }
    val currentSeason = seasons.getOrNull(selectedSeasonIndex)
    val allSeasonEpisodes = currentSeason?.episodes.orEmpty()
    val hasEpisodes = meta.dubStatusMap.values.any { list -> list.any { it.episodes.isNotEmpty() } }
    val hasRecs = meta.recommendations.isNotEmpty()

    var activeSectionTab by remember(meta.url) {
        mutableIntStateOf(if (hasEpisodes) 0 else 1)
    }

    val chunkSize = 50
    val episodeChunks = remember(allSeasonEpisodes) {
        if (allSeasonEpisodes.size > chunkSize) allSeasonEpisodes.chunked(chunkSize) else listOf(allSeasonEpisodes)
    }
    val visibleEpisodes = episodeChunks.getOrNull(selectedRangeIndex).orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 1. Cinematic Hero Backdrop Header
        item(key = "hero_header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
                    .background(AppColors.SurfaceCard)
            ) {
                val heroBg = meta.backgroundUrl ?: meta.posterUrl ?: fallbackItem.posterUrl
                if (!heroBg.isNullOrBlank()) {
                    AsyncImage(
                        model = buildCoilRequest(context, heroBg, meta.posterHeaders),
                        contentDescription = meta.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Multi-stop dark cinematic gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.55f),
                                    0.35f to Color.Black.copy(alpha = 0.25f),
                                    0.72f to Color(0xCC0B0C10),
                                    1.0f to AppColors.Background
                                )
                            )
                        )
                )

                // Top floating Back & Bookmark Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.52f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = onToggleWatchlist,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.52f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isSaved) AppColors.Accent else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Poster + Title + Metadata anchored at bottom of hero
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val posterUrl = meta.posterUrl ?: fallbackItem.posterUrl
                    Box(
                        modifier = Modifier
                            .width(102.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.SurfaceElevated)
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = buildCoilRequest(context, posterUrl, meta.posterHeaders),
                                contentDescription = meta.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Filled.Movie, null, tint = AppColors.TextMuted, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // Provider & Type badges
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppColors.AccentDim)
                                    .border(0.5.dp, AppColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = meta.apiName,
                                    color = AppColors.Accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (meta.typeLabel.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = meta.typeLabel,
                                        color = AppColors.TextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = meta.title,
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 25.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Metadata pills row: Score • Year • Duration • Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!meta.score.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(meta.score, color = AppColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (meta.year != null && meta.year > 0) {
                                Text("${meta.year}", color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (meta.durationMinutes != null && meta.durationMinutes > 0) {
                                Text("${meta.durationMinutes}m", color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            if (!meta.showStatus.isNullOrBlank()) {
                                Text(meta.showStatus, color = AppColors.Success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Primary Action Row (Watchlist + Metadata Mode Badge)
        item(key = "actions_bar") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onToggleWatchlist,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSaved) AppColors.SurfaceElevated else AppColors.Accent,
                            contentColor = if (isSaved) AppColors.TextPrimary else Color(0xFF071326)
                        ),
                        border = if (isSaved) BorderStroke(1.dp, AppColors.Accent.copy(alpha = 0.5f)) else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = if (isSaved) AppColors.Accent else Color(0xFF071326),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSaved) "Saved in Watchlist" else "Add to Watchlist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (!meta.contentRating.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.SurfaceCard)
                                .border(0.5.dp, AppColors.BorderStrong, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = meta.contentRating,
                                color = AppColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Clear status notice honoring no-streaming / no-downloads scope
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.SurfaceCard)
                        .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (meta.comingSoon) "Coming soon • Streaming & downloads will be added later."
                        else "Catalog & Metadata preview • Streaming & downloads coming soon.",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 3. Plot / Synopsis
        if (meta.plot.isNotBlank()) {
            item(key = "plot_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Synopsis",
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = meta.plot,
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        maxLines = if (plotExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { plotExpanded = !plotExpanded }
                    )
                    if (meta.plot.length > 180) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (plotExpanded) "Show less" else "Read more",
                            color = AppColors.Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { plotExpanded = !plotExpanded }
                        )
                    }
                }
            }
        }

        // 4. Genres / Tags
        if (meta.tags.isNotEmpty()) {
            item(key = "tags_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(meta.tags, key = { it }) { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.SurfaceCard)
                                .border(0.5.dp, AppColors.Border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tag,
                                color = AppColors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 5. Cast Carousel
        if (meta.actors.isNotEmpty()) {
            item(key = "cast_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = "Cast",
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(meta.actors, key = { "${it.name}_${it.role}" }) { actor ->
                            ActorChip(actor = actor, headers = meta.posterHeaders)
                        }
                    }
                }
            }
        }

        // 6. Tabs: Episodes / More Like This
        if (hasEpisodes || hasRecs) {
            item(key = "section_tabs") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    if (hasEpisodes && hasRecs) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.SurfaceCard)
                                .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
                                .padding(3.dp)
                        ) {
                            MetaSegmentTab(
                                label = "Episodes (${allSeasonEpisodes.size})",
                                selected = activeSectionTab == 0,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSectionTab = 0 }
                            )
                            MetaSegmentTab(
                                label = "More Like This (${meta.recommendations.size})",
                                selected = activeSectionTab == 1,
                                modifier = Modifier.weight(1f),
                                onClick = { activeSectionTab = 1 }
                            )
                        }
                    } else {
                        Text(
                            text = if (hasEpisodes) "Episodes (${allSeasonEpisodes.size})" else "More Like This",
                            color = AppColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // 7A. Episodes Section
        if (hasEpisodes && activeSectionTab == 0) {
            item(key = "episode_controls") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Dub/Sub chips (Anime)
                    if (meta.dubStatusMap.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(meta.dubStatusMap.keys.toList(), key = { it }) { dubKey ->
                                val selected = dubKey == selectedDub
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedDub = dubKey },
                                    label = { Text(dubKey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.Accent,
                                        selectedLabelColor = Color(0xFF071326),
                                        containerColor = AppColors.SurfaceCard,
                                        labelColor = AppColors.TextSecondary
                                    )
                                )
                            }
                        }
                    }

                    // Season chips + layout mode switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (seasons.size > 1) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(seasons.size) { idx ->
                                    val season = seasons[idx]
                                    val selected = idx == selectedSeasonIndex
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (selected) AppColors.AccentDim else AppColors.SurfaceCard)
                                            .border(
                                                0.5.dp,
                                                if (selected) AppColors.Accent else AppColors.Border,
                                                RoundedCornerShape(9.dp)
                                            )
                                            .clickable { selectedSeasonIndex = idx }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = season.displayName,
                                            color = if (selected) AppColors.Accent else AppColors.TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = currentSeason?.displayName ?: "Episodes",
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Layout switcher: Detailed / Compact / Grid
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(AppColors.SurfaceCard)
                                .border(0.5.dp, AppColors.Border, RoundedCornerShape(9.dp))
                                .padding(2.dp)
                        ) {
                            LayoutIconButton(
                                icon = Icons.Filled.ViewAgenda,
                                selected = episodeLayout == EpisodeLayoutMode.Detailed,
                                onClick = { episodeLayout = EpisodeLayoutMode.Detailed }
                            )
                            LayoutIconButton(
                                icon = Icons.AutoMirrored.Filled.ViewList,
                                selected = episodeLayout == EpisodeLayoutMode.Compact,
                                onClick = { episodeLayout = EpisodeLayoutMode.Compact }
                            )
                            LayoutIconButton(
                                icon = Icons.Filled.GridView,
                                selected = episodeLayout == EpisodeLayoutMode.Grid,
                                onClick = { episodeLayout = EpisodeLayoutMode.Grid }
                            )
                        }
                    }

                    // Range chunks if > 50 episodes
                    if (episodeChunks.size > 1) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(episodeChunks.size) { chunkIdx ->
                                val start = chunkIdx * chunkSize + 1
                                val end = minOf((chunkIdx + 1) * chunkSize, allSeasonEpisodes.size)
                                val selected = chunkIdx == selectedRangeIndex
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) AppColors.Accent else AppColors.SurfaceCard)
                                        .clickable { selectedRangeIndex = chunkIdx }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "$start–$end",
                                        color = if (selected) Color(0xFF071326) else AppColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            when (episodeLayout) {
                EpisodeLayoutMode.Detailed -> {
                    items(visibleEpisodes, key = { "ep_det_${it.season}_${it.number}_${it.data}" }) { ep ->
                        DetailedEpisodeCard(
                            episode = ep,
                            fallbackPoster = meta.backgroundUrl ?: meta.posterUrl,
                            headers = meta.posterHeaders,
                            onClick = { selectedEpisodeForSheet = ep }
                        )
                    }
                }

                EpisodeLayoutMode.Compact -> {
                    items(visibleEpisodes, key = { "ep_cmp_${it.season}_${it.number}_${it.data}" }) { ep ->
                        CompactEpisodeRow(
                            episode = ep,
                            onClick = { selectedEpisodeForSheet = ep }
                        )
                    }
                }

                EpisodeLayoutMode.Grid -> {
                    val gridRows = visibleEpisodes.chunked(4)
                    items(gridRows.size, key = { "ep_grid_row_$it" }) { rowIdx ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rowEps = gridRows[rowIdx]
                            rowEps.forEach { ep ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(AppColors.SurfaceCard)
                                        .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
                                        .clickable { selectedEpisodeForSheet = ep },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${ep.number ?: "?"}",
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            repeat(4 - rowEps.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 7B. Recommendations Grid (More Like This)
        if (hasRecs && (!hasEpisodes || activeSectionTab == 1)) {
            val recRows = meta.recommendations.chunked(3)
            items(recRows.size, key = { "rec_row_$it" }) { rowIdx ->
                val rowItems = recRows[rowIdx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (rowIdx == 0) 14.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { rec ->
                        PosterCard(
                            item = rec,
                            modifier = Modifier.weight(1f),
                            onClick = { onMediaClick(rec) }
                        )
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    // Episode Info Bottom Sheet (Metadata only — no playback)
    selectedEpisodeForSheet?.let { ep ->
        ModalBottomSheet(
            onDismissRequest = { selectedEpisodeForSheet = null },
            containerColor = AppColors.Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.BorderStrong) }
        ) {
            EpisodeDetailSheetContent(
                episode = ep,
                fallbackPoster = meta.backgroundUrl ?: meta.posterUrl,
                headers = meta.posterHeaders,
                onDismiss = { selectedEpisodeForSheet = null }
            )
        }
    }
}

@Composable
private fun ActorChip(actor: MetaActorItem, headers: Map<String, String>) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.width(74.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceCard)
                .border(0.5.dp, AppColors.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!actor.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = buildCoilRequest(context, actor.imageUrl, headers),
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.Person, null, tint = AppColors.TextMuted, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actor.name,
            color = AppColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
        if (!actor.role.isNullOrBlank()) {
            Text(
                text = actor.role,
                color = AppColors.TextMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailedEpisodeCard(
    episode: MetaEpisodeItem,
    fallbackPoster: String?,
    headers: Map<String, String>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumb = episode.posterUrl ?: fallbackPoster

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceCard)
            .border(0.5.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = buildCoilRequest(context, thumb, headers),
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Filled.Movie, null, tint = AppColors.TextMuted, modifier = Modifier.size(22.dp))
                }
                if (episode.number != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color.Black.copy(alpha = 0.78f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "EP ${episode.number}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (episode.runTime != null && episode.runTime > 0) {
                        Text("${episode.runTime} min", color = AppColors.TextSecondary, fontSize = 11.sp)
                    }
                    if (!episode.score.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(episode.score, color = AppColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (!episode.description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = episode.description,
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactEpisodeRow(
    episode: MetaEpisodeItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.SurfaceCard)
            .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 36.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AppColors.AccentDim)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${episode.number ?: "-"}",
                color = AppColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = episode.title,
            color = AppColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (episode.runTime != null && episode.runTime > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text("${episode.runTime}m", color = AppColors.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EpisodeDetailSheetContent(
    episode: MetaEpisodeItem,
    fallbackPoster: String?,
    headers: Map<String, String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val thumb = episode.posterUrl ?: fallbackPoster
    val formattedDate = remember(episode.airDateMs) {
        episode.airDateMs?.takeIf { it > 0L }?.let { ms ->
            runCatching {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(ms))
            }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!thumb.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.SurfaceElevated)
            ) {
                AsyncImage(
                    model = buildCoilRequest(context, thumb, headers),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val badge = buildString {
                if (episode.season != null) append("Season ${episode.season}  •  ")
                if (episode.number != null) append("Episode ${episode.number}")
            }
            if (badge.isNotBlank()) {
                Text(
                    text = badge,
                    color = AppColors.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = episode.title,
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (episode.runTime != null && episode.runTime > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, null, tint = AppColors.TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${episode.runTime} min", color = AppColors.TextSecondary, fontSize = 12.sp)
                }
            }
            if (!episode.score.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(episode.score, color = AppColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!formattedDate.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarToday, null, tint = AppColors.TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(formattedDate, color = AppColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }

        if (!episode.description.isNullOrBlank()) {
            Text(
                text = episode.description,
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.SurfaceCard)
                .border(0.5.dp, AppColors.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, null, tint = AppColors.Accent, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Streaming & downloads are disabled for now and will be enabled in a future update.",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        OutlinedButton(
            onClick = onDismiss,
            border = BorderStroke(0.5.dp, AppColors.BorderStrong),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("Close", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetaSegmentTab(
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
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LayoutIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) AppColors.AccentDim else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) AppColors.Accent else AppColors.TextMuted,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun MetaSkeletonView(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .shimmerEffect()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(16.dp).clip(RoundedCornerShape(5.dp)).shimmerEffect())
            Box(modifier = Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(10.dp)).shimmerEffect())
            repeat(3) {
                Box(modifier = Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
            }
        }
    }
}
