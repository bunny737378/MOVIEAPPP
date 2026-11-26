package com.xtsream.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.MetaActorItem
import com.xtsream.app.data.MetaEpisodeItem
import com.xtsream.app.data.MetaUiData
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.AppToastManager
import com.xtsream.app.ui.components.AutoFitGridText
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.M3InlineSpinner
import com.xtsream.app.ui.components.M3LoadingView
import com.xtsream.app.ui.components.NotchedEpisodeThumbnail
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.components.buildCoilRequest
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.MetaViewModel
import com.xtsream.app.viewmodel.StreamServerItem
import com.xtsream.app.viewmodel.StreamSheetState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class EpisodeLayoutMode { Detailed, Compact, Grid }

private enum class ActiveMetaPanel {
    None,
    Description,
    Season,
    Range
}

@Composable
fun MetaScreen(
    item: MediaCardItem,
    onBack: () -> Unit,
    onMediaClick: (MediaCardItem, Rect?) -> Unit
) {
    val vm: MetaViewModel = viewModel(key = "meta_${item.apiKey}_${item.url}")
    LaunchedEffect(item.apiKey, item.url) { vm.load(item) }

    val state by vm.uiState.collectAsState()
    val streamState by vm.streamState.collectAsState()
    val watchlist by WatchlistStore.watchlist.collectAsState()
    val isSaved = remember(watchlist, item.url) { watchlist.any { it.url == item.url } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    M3LoadingView()
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.Primary)
                    }
                }
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.Primary)
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
                        message = state.errorMessage ?: "Failed to load details.",
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
                    streamState = streamState,
                    onBack = onBack,
                    onToggleWatchlist = {
                        val saveItem = MediaCardItem(
                            title = meta.title,
                            url = meta.url,
                            apiName = meta.apiName,
                            apiKey = meta.apiKey.ifBlank { item.apiKey },
                            posterUrl = meta.posterUrl ?: item.posterUrl,
                            posterHeaders = meta.posterHeaders.ifEmpty { item.posterHeaders },
                            type = meta.typeLabel,
                            shortType = item.shortType,
                            quality = item.quality,
                            score = meta.score ?: item.score,
                            year = meta.year ?: item.year
                        )
                        val added = WatchlistStore.toggleWatchlist(saveItem)
                        AppToastManager.showSuccess(
                            if (added) "Added to My List" else "Removed from My List"
                        )
                    },
                    onEpisodeClick = { ep -> vm.openStreamsForEpisode(meta, ep) },
                    onMovieServersClick = { vm.openStreamsForMovie(meta) },
                    onMinimizeStreams = { vm.minimizeStreams() },
                    onExpandStreams = { vm.expandStreams() },
                    onRetryStreams = { vm.retryStreams() },
                    onMediaClick = onMediaClick
                )
            }
        }
    }
}

@Composable
private fun MetaLoadedContent(
    meta: MetaUiData,
    fallbackItem: MediaCardItem,
    isSaved: Boolean,
    streamState: StreamSheetState,
    onBack: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onEpisodeClick: (MetaEpisodeItem) -> Unit,
    onMovieServersClick: () -> Unit,
    onMinimizeStreams: () -> Unit,
    onExpandStreams: () -> Unit,
    onRetryStreams: () -> Unit,
    onMediaClick: (MediaCardItem, Rect?) -> Unit
) {
    val context = LocalContext.current

    var activePanel by remember(meta.url) { mutableStateOf(ActiveMetaPanel.None) }
    var selectedSeasonIndex by remember(meta.url) { mutableIntStateOf(0) }
    var selectedRangeIndex by remember(meta.url, selectedSeasonIndex) { mutableIntStateOf(0) }
    var reverseOrder by remember(meta.url) { mutableStateOf(false) }
    var episodeLayout by remember { mutableStateOf(EpisodeLayoutMode.Detailed) }

    val seasons = remember(meta.seasons) { meta.seasons }
    val safeSeasonIndex = selectedSeasonIndex.coerceIn(0, (seasons.size - 1).coerceAtLeast(0))
    val currentSeason = seasons.getOrNull(safeSeasonIndex)
    val baseSeasonEpisodes = remember(currentSeason) { currentSeason?.episodes.orEmpty() }
    val hasEpisodes = seasons.any { it.episodes.isNotEmpty() }

    // Chunk raw ascending episodes into 50-ep ranges FIRST, then reverse ONLY the active chunk
    val chunkSize = 50
    val rawEpisodeChunks = remember(baseSeasonEpisodes) {
        if (baseSeasonEpisodes.size > chunkSize) baseSeasonEpisodes.chunked(chunkSize)
        else listOf(baseSeasonEpisodes)
    }
    val safeRangeIndex = selectedRangeIndex.coerceIn(0, (rawEpisodeChunks.size - 1).coerceAtLeast(0))
    val activeChunk = rawEpisodeChunks.getOrNull(safeRangeIndex).orEmpty()
    val visibleEpisodes = remember(activeChunk, reverseOrder) {
        if (reverseOrder) activeChunk.asReversed() else activeChunk
    }

    // Hide hero title overlay whenever Description OR Streams panel is open
    val hideHeroTitle = activePanel == ActiveMetaPanel.Description || streamState.isExpanded
    val showMinimizedBar = streamState.isMinimized && !streamState.isExpanded

    // Non-duplicated Left-Label / Right-Value details rows inside Description Panel
    val detailRows = remember(meta) {
        buildList {
            meta.durationMinutes?.takeIf { it > 0 }?.let { add("Runtime" to "$it min") }
            meta.showStatus?.takeIf { it.isNotBlank() }?.let { add("Status" to it) }
            meta.contentRating?.takeIf { it.isNotBlank() }?.let { add("Rated" to it) }
            if (meta.hasAnySub || meta.hasAnyDub) {
                val audioText = listOfNotNull(
                    if (meta.hasAnySub) "Sub" else null,
                    if (meta.hasAnyDub) "Dub" else null
                ).joinToString(" / ")
                if (audioText.isNotBlank()) add("Audio" to audioText)
            }
            if (meta.tags.isNotEmpty()) {
                add("Genres" to meta.tags.take(10).joinToString(", "))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = if (showMinimizedBar) 92.dp else 36.dp)
        ) {
            // 1. Full-Bleed Top Hero Artwork + Gradient Scrim + Title + Quick Meta
            item(key = "hero_backdrop") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .background(AppColors.Surface)
                ) {
                    val heroImg = meta.backgroundUrl ?: meta.posterUrl ?: fallbackItem.posterUrl
                    if (!heroImg.isNullOrBlank()) {
                        AsyncImage(
                            model = buildCoilRequest(context, heroImg, meta.posterHeaders),
                            contentDescription = meta.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to AppColors.Background.copy(alpha = 0.65f),
                                        0.26f to Color.Transparent,
                                        0.54f to AppColors.Background.copy(alpha = 0.40f),
                                        0.83f to AppColors.Background.copy(alpha = 0.92f),
                                        1.0f to AppColors.Background
                                    )
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = AppColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = onToggleWatchlist) {
                            Icon(
                                imageVector = if (isSaved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                                contentDescription = "Watchlist",
                                tint = AppColors.Primary,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }

                    // Title & quick meta hidden cleanly whenever Description or Streams panel is open
                    AnimatedVisibility(
                        visible = !hideHeroTitle,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(140)),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = meta.title,
                                color = AppColors.Primary,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 30.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!meta.score.isNullOrBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(meta.score, color = AppColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                val quickLine = listOfNotNull(
                                    meta.typeLabel.takeIf { it.isNotBlank() },
                                    meta.year?.takeIf { it > 0 }?.toString(),
                                    meta.showStatus?.takeIf { it.isNotBlank() },
                                    meta.durationMinutes?.takeIf { it > 0 }?.let { "${it}m" }
                                ).joinToString("  •  ")
                                if (quickLine.isNotBlank()) {
                                    Text(
                                        text = quickLine,
                                        color = AppColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. YouTube-Style Compact 2-Line Synopsis + Down Arrow -> Opens Hard Description Panel
            item(key = "yt_description_trigger") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (streamState.isExpanded) onMinimizeStreams()
                            activePanel = ActiveMetaPanel.Description
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = meta.plot.ifBlank { "Tap to view details, genres, cast & info" },
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 1.dp)
                    ) {
                        Text(
                            text = "more",
                            color = AppColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Open full description",
                            tint = AppColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 3. Primary Actions (Movies also get a direct Servers button alongside My List)
            item(key = "watchlist_action") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!hasEpisodes && (meta.isMovieLike || !meta.movieDataUrl.isNullOrBlank())) {
                        Button(
                            onClick = {
                                activePanel = ActiveMetaPanel.None
                                onMovieServersClick()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Primary,
                                contentColor = AppColors.OnPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Servers",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Button(
                            onClick = onToggleWatchlist,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.SurfaceElevated,
                                contentColor = AppColors.Primary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isSaved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = if (isSaved) "In My List" else "My List",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = onToggleWatchlist,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSaved) AppColors.SurfaceElevated else AppColors.Primary,
                                contentColor = if (isSaved) AppColors.Primary else AppColors.OnPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = if (isSaved) AppIcons.Bookmark else AppIcons.BookmarkBorder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSaved) "In My List" else "Add to My List",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // 4. Episodes Section (Unified list — NO Sub/Dub dropdown at top!)
            if (hasEpisodes) {
                item(key = "episodes_header_bar") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes",
                                color = AppColors.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${baseSeasonEpisodes.size}",
                                color = AppColors.TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = { reverseOrder = !reverseOrder },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.SwapVert,
                                    contentDescription = "Reverse episode order",
                                    tint = if (reverseOrder) AppColors.Primary else AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    episodeLayout = when (episodeLayout) {
                                        EpisodeLayoutMode.Detailed -> EpisodeLayoutMode.Compact
                                        EpisodeLayoutMode.Compact -> EpisodeLayoutMode.Grid
                                        EpisodeLayoutMode.Grid -> EpisodeLayoutMode.Detailed
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = when (episodeLayout) {
                                        EpisodeLayoutMode.Detailed -> AppIcons.ViewAgenda
                                        EpisodeLayoutMode.Compact -> AppIcons.ViewList
                                        EpisodeLayoutMode.Grid -> AppIcons.GridView
                                    },
                                    contentDescription = "Switch episode layout",
                                    tint = AppColors.TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        val showSeasonPicker = seasons.size > 1
                        val showRangePicker = rawEpisodeChunks.size > 1

                        if (showSeasonPicker || showRangePicker) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (showSeasonPicker) {
                                    DropdownTriggerChip(
                                        label = currentSeason?.displayName ?: "Season 1",
                                        onClick = {
                                            if (streamState.isExpanded) onMinimizeStreams()
                                            activePanel = ActiveMetaPanel.Season
                                        }
                                    )
                                }
                                if (showRangePicker) {
                                    val start = safeRangeIndex * chunkSize + 1
                                    val end = minOf((safeRangeIndex + 1) * chunkSize, baseSeasonEpisodes.size)
                                    DropdownTriggerChip(
                                        label = formatRangeLabel(start, end),
                                        onClick = {
                                            if (streamState.isExpanded) onMinimizeStreams()
                                            activePanel = ActiveMetaPanel.Range
                                        }
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }

                when (episodeLayout) {
                    EpisodeLayoutMode.Detailed -> {
                        itemsIndexed(visibleEpisodes, key = { idx, it -> "ep_det_${idx}_${it.season}_${it.number}_${it.data}" }) { _, ep ->
                            BorderlessDetailedEpisodeRow(
                                episode = ep,
                                fallbackPoster = meta.backgroundUrl ?: meta.posterUrl,
                                headers = meta.posterHeaders,
                                onClick = {
                                    activePanel = ActiveMetaPanel.None
                                    onEpisodeClick(ep)
                                }
                            )
                        }
                    }

                    EpisodeLayoutMode.Compact -> {
                        itemsIndexed(visibleEpisodes, key = { idx, it -> "ep_cmp_${idx}_${it.season}_${it.number}_${it.data}" }) { _, ep ->
                            BorderlessCompactEpisodeRow(
                                episode = ep,
                                onClick = {
                                    activePanel = ActiveMetaPanel.None
                                    onEpisodeClick(ep)
                                }
                            )
                        }
                    }

                    EpisodeLayoutMode.Grid -> {
                        val gridRows = visibleEpisodes.chunked(5)
                        items(gridRows.size, key = { "ep_grid_$it" }) { rowIdx ->
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
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AppColors.SurfaceCard)
                                            .clickable {
                                                activePanel = ActiveMetaPanel.None
                                                onEpisodeClick(ep)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${ep.number ?: "?"}",
                                            color = AppColors.TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                repeat(5 - rowEps.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recommendations ("More Like This")
            if (meta.recommendations.isNotEmpty()) {
                item(key = "recommendations_carousel") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    ) {
                        Text(
                            text = "More Like This",
                            color = AppColors.TextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(meta.recommendations, key = { idx, it -> "rec_${idx}_${it.url}" }) { _, rec ->
                                PosterCard(
                                    item = rec,
                                    modifier = Modifier.width(118.dp),
                                    onClick = { bounds -> onMediaClick(rec, bounds) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Minimized Rectangular Bottom Bar (e.g. "S01 • EP04") ---
        AnimatedVisibility(
            visible = showMinimizedBar,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(tween(160)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180)) + fadeOut(tween(140)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MinimizedStreamBottomBar(
                streamState = streamState,
                onExpand = {
                    activePanel = ActiveMetaPanel.None
                    onExpandStreams()
                }
            )
        }

        // --- Hard YouTube-Style Docked Panels ---

        // 1. Description Panel (NO black shadow scrim behind it; hero title hidden while open)
        HardDockedPanel(
            visible = activePanel == ActiveMetaPanel.Description,
            title = "Description",
            heightFraction = 0.64f,
            showScrim = false,
            onDismiss = { activePanel = ActiveMetaPanel.None }
        ) {
            DescriptionPanelBody(
                meta = meta,
                detailRows = detailRows
            )
        }

        // 2. Season Picker Panel — Equal-Size 3-Column Grid with Smart Auto-Fit Text (up to 3 lines)
        HardDockedPanel(
            visible = activePanel == ActiveMetaPanel.Season,
            title = "Select Season",
            heightFraction = 0.54f,
            showScrim = true,
            onDismiss = { activePanel = ActiveMetaPanel.None }
        ) {
            val seasonRows = remember(seasons) { seasons.withIndex().toList().chunked(3) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(seasonRows.size, key = { "season_grid_$it" }) { rowIdx ->
                    val rowItems = seasonRows[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { (idx, season) ->
                            val selected = idx == safeSeasonIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AppColors.Primary else AppColors.SurfaceCard)
                                    .border(
                                        width = if (selected) 0.dp else 0.75.dp,
                                        color = if (selected) Color.Transparent else AppColors.Border,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedSeasonIndex = idx
                                        selectedRangeIndex = 0
                                        activePanel = ActiveMetaPanel.None
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AutoFitGridText(
                                    text = season.displayName,
                                    color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
                                    maxFontSizeSp = 13f,
                                    minFontSizeSp = 7.5f,
                                    maxLines = 3,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 3. Episode Range Picker Panel — Clean 3-Column Grid (01-50, 51-100, 101-150...)
        HardDockedPanel(
            visible = activePanel == ActiveMetaPanel.Range,
            title = "Episode Range",
            heightFraction = 0.52f,
            showScrim = true,
            onDismiss = { activePanel = ActiveMetaPanel.None }
        ) {
            val rangeLabels = remember(rawEpisodeChunks, baseSeasonEpisodes.size) {
                rawEpisodeChunks.indices.map { idx ->
                    val start = idx * chunkSize + 1
                    val end = minOf((idx + 1) * chunkSize, baseSeasonEpisodes.size)
                    idx to formatRangeLabel(start, end)
                }
            }
            val gridRows = remember(rangeLabels) { rangeLabels.chunked(3) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(gridRows.size, key = { "range_grid_$it" }) { rowIdx ->
                    val rowItems = gridRows[rowIdx]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { (chunkIdx, label) ->
                            val selected = chunkIdx == safeRangeIndex
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) AppColors.Primary else AppColors.SurfaceCard)
                                    .border(
                                        width = if (selected) 0.dp else 0.75.dp,
                                        color = if (selected) Color.Transparent else AppColors.Border,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedRangeIndex = chunkIdx
                                        activePanel = ActiveMetaPanel.None
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) AppColors.OnPrimary else AppColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 4. Real-Time Stream Servers Panel (Half-screen docked, NO black shadow scrim, hero title hidden, closes into bottom rectangular bar)
        HardDockedPanel(
            visible = streamState.isExpanded,
            title = streamState.barLabel.ifBlank { "Servers" },
            heightFraction = 0.64f,
            showScrim = false,
            onDismiss = onMinimizeStreams
        ) {
            StreamServersPanelBody(
                streamState = streamState,
                onRetry = onRetryStreams
            )
        }
    }
}

@Composable
private fun MinimizedStreamBottomBar(
    streamState: StreamSheetState,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceElevated)
            .clickable { onExpand() }
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.8.dp, color = AppColors.BorderStrong)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = streamState.barLabel.ifBlank { "Servers" },
                        color = AppColors.Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (streamState.isLoading) {
                        M3InlineSpinner(size = 14.dp, color = AppColors.TextSecondary)
                    }
                    val statusText = when {
                        streamState.isLoading && streamState.servers.isEmpty() -> "Loading..."
                        streamState.isLoading -> "${streamState.servers.size} found..."
                        streamState.servers.isNotEmpty() -> "${streamState.servers.size} ${if (streamState.servers.size == 1) "server" else "servers"}"
                        else -> "No servers"
                    }
                    Text(
                        text = statusText,
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (streamState.subtitle.isNotBlank()) {
                    Text(
                        text = streamState.subtitle,
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Expand servers",
                tint = AppColors.Primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StreamServersPanelBody(
    streamState: StreamSheetState,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Subtitle + Live Status Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = streamState.subtitle.ifBlank { streamState.barLabel },
                color = AppColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (streamState.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    M3InlineSpinner(size = 16.dp, color = AppColors.Primary)
                    Text(
                        text = if (streamState.servers.isEmpty()) "Loading..." else "${streamState.servers.size} found",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (streamState.servers.isNotEmpty()) {
                Text(
                    text = "${streamState.servers.size} ${if (streamState.servers.size == 1) "server" else "servers"}",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

        when {
            streamState.isLoading && streamState.servers.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        M3InlineSpinner(size = 34.dp, color = AppColors.Primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching stream servers...",
                            color = AppColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            !streamState.isLoading && streamState.servers.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = streamState.errorMessage ?: "No stream servers found.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = onRetry,
                            border = BorderStroke(1.dp, AppColors.BorderStrong),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry", color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = streamState.servers,
                        key = { idx, srv -> "srv_${idx}_${srv.id}" }
                    ) { _, server ->
                        StreamServerRow(server = server)
                    }
                }
            }
        }
    }
}

/**
 * Display-only server row (clicking does nothing for now).
 */
@Composable
private fun StreamServerRow(server: StreamServerItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.SurfaceCard)
            .border(0.6.dp, AppColors.Border, RoundedCornerShape(10.dp))
            .clickable { /* Display-only for now; player not wired yet */ }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = server.name,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            if (!server.extraInfo.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = server.extraInfo,
                    color = AppColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (!server.audioTag.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(AppColors.SubPill)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = server.audioTag,
                        color = AppColors.AudioBadgeInk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            if (server.qualityLabel.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(AppColors.Primary)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = server.qualityLabel,
                        color = AppColors.OnPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            if (server.typeBadge.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(AppColors.EpisodePill)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = server.typeBadge,
                        color = AppColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatRangeLabel(start: Int, end: Int): String {
    val s = if (start < 10) start.toString().padStart(2, '0') else start.toString()
    val e = if (end < 10) end.toString().padStart(2, '0') else end.toString()
    return "$s-$e"
}

/**
 * Hard YouTube-Style Docked Bottom Panel:
 * - Anchored cleanly to bottom of Meta screen, rising up to meet the top poster/backdrop.
 * - Optional scrim ([showScrim] = false draws ZERO black shadow behind Description / Streams).
 * - Swiping/dragging down ONLY works on the header area ABOVE the white divider line.
 */
@Composable
private fun HardDockedPanel(
    visible: Boolean,
    title: String,
    heightFraction: Float = 0.64f,
    showScrim: Boolean = true,
    onDismiss: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    BackHandler(enabled = visible) { onDismiss() }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragOffsetPx = remember(visible) { Animatable(0f) }
    val dismissThresholdPx = with(density) { 78.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tap-above-panel dismiss zone (transparent when showScrim = false so NO black shadow appears!)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (showScrim) Color.Black.copy(alpha = 0.32f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 230)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 190)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(heightFraction)
                    .offset { IntOffset(0, dragOffsetPx.value.roundToInt().coerceAtLeast(0)) }
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(AppColors.Surface)
                    .navigationBarsPadding()
            ) {
                // HEADER AREA (ABOVE THE WHITE LINE) — ONLY area that accepts drag-down-to-close!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(title) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetPx.value >= dismissThresholdPx) {
                                        onDismiss()
                                    } else {
                                        scope.launch {
                                            dragOffsetPx.animateTo(
                                                0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { dragOffsetPx.animateTo(0f) }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    scope.launch {
                                        val next = (dragOffsetPx.value + dragAmount).coerceAtLeast(0f)
                                        dragOffsetPx.snapTo(next)
                                    }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height(3.5.dp)
                                .clip(CircleShape)
                                .background(AppColors.BorderStrong)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            color = AppColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = AppColors.Primary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                // BODY AREA (BELOW THE WHITE LINE) — scrolls freely, NEVER drags/closes the panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DescriptionPanelBody(
    meta: MetaUiData,
    detailRows: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = meta.title,
            color = AppColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 23.sp
        )

        // Equal-width centered stats strip: Source (shortDisplayName), Type, Release, Rating
        val stats = remember(meta.sourceDisplayName, meta.typeLabel, meta.year, meta.score) {
            buildList {
                meta.sourceDisplayName.trim().takeIf { it.isNotBlank() }?.let { add(it to "Source") }
                meta.typeLabel.trim().takeIf { it.isNotBlank() }?.let { add(it to "Type") }
                meta.year?.takeIf { it > 0 }?.let { add(it.toString() to "Release") }
                meta.score?.takeIf { it.isNotBlank() }?.let { add("★ $it" to "Rating") }
            }
        }

        if (stats.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                stats.forEach { (value, label) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = value,
                            color = if (label == "Rating") AppColors.Gold else AppColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = label,
                            color = AppColors.TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (meta.plot.isNotBlank()) {
            Text(
                text = meta.plot,
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }

        if (detailRows.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
                detailRows.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = label,
                            color = AppColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.widthIn(min = 84.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = value,
                            color = AppColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (index < detailRows.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
                    }
                }
            }
        }

        if (meta.actors.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Cast",
                    color = AppColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(meta.actors, key = { idx, it -> "${idx}_${it.name}_${it.role}" }) { _, actor ->
                        ActorItem(actor = actor, headers = meta.posterHeaders)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun DropdownTriggerChip(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceCard)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ActorItem(actor: MetaActorItem, headers: Map<String, String>) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceCard),
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
                Icon(Icons.Filled.Person, null, tint = AppColors.TextMuted, modifier = Modifier.size(24.dp))
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
private fun BorderlessDetailedEpisodeRow(
    episode: MetaEpisodeItem,
    fallbackPoster: String?,
    headers: Map<String, String>,
    onClick: () -> Unit
) {
    val thumb = episode.posterUrl ?: fallbackPoster
    val airDate = remember(episode.airDateMs) {
        episode.airDateMs?.takeIf { it > 0L }?.let { ms ->
            runCatching { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms)) }.getOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NotchedEpisodeThumbnail(
                    imageUrl = thumb,
                    headers = headers,
                    episodeNumber = episode.number,
                    score = episode.score,
                    hasSub = episode.hasSub,
                    hasDub = episode.hasDub,
                    notchBgColor = AppColors.Background,
                    modifier = Modifier.width(138.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (episode.number != null && !episode.title.startsWith("Episode", ignoreCase = true)) {
                            "${episode.number}. ${episode.title}"
                        } else {
                            episode.title
                        },
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    val subMeta = listOfNotNull(
                        episode.runTime?.takeIf { it > 0 }?.let { "${it}m" },
                        airDate
                    ).joinToString(" • ")
                    if (subMeta.isNotBlank()) {
                        Text(subMeta, color = AppColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }

            if (!episode.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = episode.description,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = AppColors.Divider
        )
    }
}

@Composable
private fun BorderlessCompactEpisodeRow(
    episode: MetaEpisodeItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = episode.number?.toString()?.padStart(2, '0') ?: "--",
                color = AppColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(34.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    color = AppColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!episode.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episode.description,
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (episode.hasSub || episode.hasDub) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (episode.hasSub) {
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .width(20.dp)
                                .clip(RoundedCornerShape(50))
                                .background(AppColors.SubPill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.ClosedCaption,
                                contentDescription = "Sub",
                                tint = AppColors.AudioBadgeInk,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                    if (episode.hasDub) {
                        Box(
                            modifier = Modifier
                                .height(18.dp)
                                .width(20.dp)
                                .clip(RoundedCornerShape(50))
                                .background(AppColors.DubPill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Mic,
                                contentDescription = "Dub",
                                tint = AppColors.AudioBadgeInk,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
            if (episode.runTime != null && episode.runTime > 0) {
                Spacer(modifier = Modifier.width(10.dp))
                Text("${episode.runTime}m", color = AppColors.TextMuted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = AppColors.Divider
        )
    }
}
