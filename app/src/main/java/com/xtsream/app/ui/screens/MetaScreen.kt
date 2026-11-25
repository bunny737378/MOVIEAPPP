package com.xtsream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
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
import com.xtsream.app.ui.components.AppIcons
import com.xtsream.app.ui.components.ErrorStateView
import com.xtsream.app.ui.components.M3LoadingView
import com.xtsream.app.ui.components.PosterCard
import com.xtsream.app.ui.components.buildCoilRequest
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.viewmodel.MetaViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class EpisodeLayoutMode { Detailed, Compact, Grid }

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
                    onBack = onBack,
                    onToggleWatchlist = {
                        val saveItem = MediaCardItem(
                            title = meta.title,
                            url = meta.url,
                            apiName = meta.apiName,
                            posterUrl = meta.posterUrl ?: item.posterUrl,
                            posterHeaders = meta.posterHeaders.ifEmpty { item.posterHeaders },
                            type = meta.typeLabel,
                            shortType = item.shortType,
                            quality = item.quality,
                            score = meta.score ?: item.score,
                            year = meta.year ?: item.year
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
    var reverseOrder by remember(meta.url) { mutableStateOf(false) }
    var episodeLayout by remember { mutableStateOf(EpisodeLayoutMode.Detailed) }

    var showSeasonSheet by remember { mutableStateOf(false) }
    var showRangeSheet by remember { mutableStateOf(false) }
    var showDubSheet by remember { mutableStateOf(false) }
    var selectedEpisodeForSheet by remember { mutableStateOf<MetaEpisodeItem?>(null) }

    val seasons = remember(meta.dubStatusMap, selectedDub) {
        meta.dubStatusMap[selectedDub].orEmpty()
    }
    val currentSeason = seasons.getOrNull(selectedSeasonIndex)
    val orderedSeasonEpisodes = remember(currentSeason, reverseOrder) {
        val base = currentSeason?.episodes.orEmpty()
        if (reverseOrder) base.asReversed() else base
    }
    val hasEpisodes = meta.dubStatusMap.values.any { list -> list.any { it.episodes.isNotEmpty() } }

    val chunkSize = 50
    val episodeChunks = remember(orderedSeasonEpisodes) {
        if (orderedSeasonEpisodes.size > chunkSize) orderedSeasonEpisodes.chunked(chunkSize)
        else listOf(orderedSeasonEpisodes)
    }
    val safeRangeIndex = selectedRangeIndex.coerceIn(0, (episodeChunks.size - 1).coerceAtLeast(0))
    val visibleEpisodes = episodeChunks.getOrNull(safeRangeIndex).orEmpty()

    // Structured Left-Label / Right-Value details rows (only non-empty fields)
    val detailRows = remember(meta) {
        buildList {
            if (meta.typeLabel.isNotBlank()) add("Type" to meta.typeLabel)
            meta.year?.takeIf { it > 0 }?.let { add("Year" to it.toString()) }
            meta.score?.takeIf { it.isNotBlank() }?.let { add("Rating" to "★ $it / 10") }
            meta.durationMinutes?.takeIf { it > 0 }?.let { add("Runtime" to "$it min") }
            meta.showStatus?.takeIf { it.isNotBlank() }?.let { add("Status" to it) }
            meta.contentRating?.takeIf { it.isNotBlank() }?.let { add("Rated" to it) }
            if (meta.dubStatusMap.size > 1) {
                add("Audio" to meta.dubStatusMap.keys.joinToString(" / "))
            }
            if (meta.tags.isNotEmpty()) {
                add("Genres" to meta.tags.take(6).joinToString(", "))
            }
            if (meta.apiName.isNotBlank()) add("Source" to meta.apiName)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        // 1. Full-Bleed Top Hero Artwork + Smooth Dark Gradient Scrim (NO portrait poster overlay!)
        item(key = "hero_backdrop") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
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

                // Multi-stop shadow scrim blending top controls and bottom title into pitch black
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.65f),
                                    0.26f to Color.Transparent,
                                    0.56f to Color.Black.copy(alpha = 0.35f),
                                    0.84f to Color(0xE8000000),
                                    1.0f to AppColors.Background
                                )
                            )
                        )
                )

                // Clean top navigation bar (NO glass circles!)
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

                // Title & quick summary anchored cleanly over bottom shadow
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = meta.title,
                        color = AppColors.Primary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 31.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                        val quickLine = listOfNotNull(
                            meta.year?.takeIf { it > 0 }?.toString(),
                            meta.typeLabel.takeIf { it.isNotBlank() },
                            meta.durationMinutes?.takeIf { it > 0 }?.let { "${it}m" },
                            meta.showStatus?.takeIf { it.isNotBlank() }
                        ).joinToString("  •  ")
                        if (quickLine.isNotBlank()) {
                            Text(
                                text = quickLine,
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // 2. Clean Primary Watchlist Action Button
        item(key = "watchlist_action") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleWatchlist,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSaved) AppColors.SurfaceElevated else AppColors.Primary,
                        contentColor = if (isSaved) AppColors.Primary else Color.Black
                    ),
                    shape = RoundedCornerShape(8.dp),
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

        // 3. Synopsis / Description (classic clean layout)
        if (meta.plot.isNotBlank()) {
            item(key = "plot") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { plotExpanded = !plotExpanded }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = meta.plot,
                        color = AppColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        maxLines = if (plotExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (meta.plot.length > 170) {
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = if (plotExpanded) "Less" else "More",
                            color = AppColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 4. Structured Left-Label / Right-Value Info Rows
        if (detailRows.isNotEmpty()) {
            item(key = "details_spec_table") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    detailRows.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = label,
                                color = AppColors.TextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.widthIn(min = 90.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
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
        }

        // 5. Cast Carousel
        if (meta.actors.isNotEmpty()) {
            item(key = "cast_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 6.dp)
                ) {
                    Text(
                        text = "Cast",
                        color = AppColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(meta.actors, key = { "${it.name}_${it.role}" }) { actor ->
                            ActorItem(actor = actor, headers = meta.posterHeaders)
                        }
                    }
                }
            }
        }

        // 6. Episodes Section — Compact Dropdown Selectors + Borderless Flat Episode Rows!
        if (hasEpisodes) {
            item(key = "episodes_header_bar") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                ) {
                    HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Title + Compact Sort/Layout Icons (NO glass boxes!)
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
                            text = "${orderedSeasonEpisodes.size}",
                            color = AppColors.TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        // Sort order toggle (Asc/Desc)
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

                        // Cycle layout: Detailed -> Compact -> Grid
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

                    // Compact Dropdown Trigger Row: Season ▾  |  Range ▾  |  Sub/Dub ▾
                    val showSeasonPicker = seasons.size > 1
                    val showRangePicker = episodeChunks.size > 1
                    val showDubPicker = meta.dubStatusMap.size > 1

                    if (showSeasonPicker || showRangePicker || showDubPicker) {
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
                                    onClick = { showSeasonSheet = true }
                                )
                            }
                            if (showRangePicker) {
                                val start = safeRangeIndex * chunkSize + 1
                                val end = minOf((safeRangeIndex + 1) * chunkSize, orderedSeasonEpisodes.size)
                                DropdownTriggerChip(
                                    label = "$start–$end",
                                    onClick = { showRangeSheet = true }
                                )
                            }
                            if (showDubPicker) {
                                DropdownTriggerChip(
                                    label = selectedDub,
                                    onClick = { showDubSheet = true }
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // Borderless Episodes List (NO boxes around each episode!)
            when (episodeLayout) {
                EpisodeLayoutMode.Detailed -> {
                    items(visibleEpisodes, key = { "ep_det_${it.season}_${it.number}_${it.data}" }) { ep ->
                        BorderlessDetailedEpisodeRow(
                            episode = ep,
                            fallbackPoster = meta.backgroundUrl ?: meta.posterUrl,
                            headers = meta.posterHeaders,
                            onClick = { selectedEpisodeForSheet = ep }
                        )
                    }
                }

                EpisodeLayoutMode.Compact -> {
                    items(visibleEpisodes, key = { "ep_cmp_${it.season}_${it.number}_${it.data}" }) { ep ->
                        BorderlessCompactEpisodeRow(
                            episode = ep,
                            onClick = { selectedEpisodeForSheet = ep }
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
                                        .clickable { selectedEpisodeForSheet = ep },
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

        // 7. Recommendations ("More Like This") — ALWAYS visible directly on Meta page!
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
                        items(meta.recommendations, key = { "rec_${it.url}" }) { rec ->
                            PosterCard(
                                item = rec,
                                modifier = Modifier.width(118.dp),
                                onClick = { onMediaClick(rec) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Season Selector Bottom Sheet
    if (showSeasonSheet) {
        SelectionBottomSheet(
            title = "Select Season",
            options = seasons.mapIndexed { idx, s ->
                SelectionOption(
                    title = s.displayName,
                    subtitle = "${s.episodes.size} episodes",
                    selected = idx == selectedSeasonIndex
                )
            },
            onSelect = { idx ->
                selectedSeasonIndex = idx
                selectedRangeIndex = 0
                showSeasonSheet = false
            },
            onDismiss = { showSeasonSheet = false }
        )
    }

    // Episode Range Selector Bottom Sheet
    if (showRangeSheet) {
        SelectionBottomSheet(
            title = "Episode Range",
            options = episodeChunks.mapIndexed { idx, chunk ->
                val start = idx * chunkSize + 1
                val end = minOf((idx + 1) * chunkSize, orderedSeasonEpisodes.size)
                SelectionOption(
                    title = "Episodes $start – $end",
                    subtitle = "${chunk.size} episodes",
                    selected = idx == safeRangeIndex
                )
            },
            onSelect = { idx ->
                selectedRangeIndex = idx
                showRangeSheet = false
            },
            onDismiss = { showRangeSheet = false }
        )
    }

    // Sub / Dub Selector Bottom Sheet
    if (showDubSheet) {
        val dubKeys = meta.dubStatusMap.keys.toList()
        SelectionBottomSheet(
            title = "Audio / Track",
            options = dubKeys.map { key ->
                val epTotal = meta.dubStatusMap[key]?.sumOf { it.episodes.size } ?: 0
                SelectionOption(
                    title = key,
                    subtitle = "$epTotal episodes",
                    selected = key == selectedDub
                )
            },
            onSelect = { idx ->
                selectedDub = dubKeys[idx]
                selectedSeasonIndex = 0
                selectedRangeIndex = 0
                showDubSheet = false
            },
            onDismiss = { showDubSheet = false }
        )
    }

    // Episode Details Bottom Sheet (clean metadata only — zero placeholder banners)
    selectedEpisodeForSheet?.let { ep ->
        ModalBottomSheet(
            onDismissRequest = { selectedEpisodeForSheet = null },
            containerColor = AppColors.Surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.BorderStrong) }
        ) {
            EpisodeDetailSheetContent(
                episode = ep,
                fallbackPoster = meta.backgroundUrl ?: meta.posterUrl,
                headers = meta.posterHeaders
            )
        }
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
            maxLines = 1
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

private data class SelectionOption(
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBottomSheet(
    title: String,
    options: List<SelectionOption>,
    onSelect: (Int) -> Unit,
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
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = title,
                color = AppColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(options.size) { idx ->
                    val opt = options[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(idx) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opt.title,
                                color = if (opt.selected) AppColors.Primary else AppColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (opt.selected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (!opt.subtitle.isNullOrBlank()) {
                                Text(
                                    text = opt.subtitle,
                                    color = AppColors.TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        if (opt.selected) {
                            Icon(Icons.Filled.Check, null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        thickness = 0.5.dp,
                        color = AppColors.Divider
                    )
                }
            }
        }
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

/**
 * Borderless Netflix / Prime Video style episode row directly on pitch-black surface (NO outer card box).
 */
@Composable
private fun BorderlessDetailedEpisodeRow(
    episode: MetaEpisodeItem,
    fallbackPoster: String?,
    headers: Map<String, String>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
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
                Box(
                    modifier = Modifier
                        .width(134.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceCard),
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
                        Icon(AppIcons.Movie, null, tint = AppColors.TextMuted, modifier = Modifier.size(22.dp))
                    }
                    if (episode.number != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "E${episode.number}",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!episode.score.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, tint = AppColors.Gold, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(episode.score, color = AppColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        val subMeta = listOfNotNull(
                            episode.runTime?.takeIf { it > 0 }?.let { "${it}m" },
                            airDate
                        ).joinToString(" • ")
                        if (subMeta.isNotBlank()) {
                            Text(subMeta, color = AppColors.TextMuted, fontSize = 11.sp)
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

/**
 * Clean flat borderless compact episode row.
 */
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

@Composable
private fun EpisodeDetailSheetContent(
    episode: MetaEpisodeItem,
    fallbackPoster: String?,
    headers: Map<String, String>
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
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!thumb.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.SurfaceCard)
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
                    color = AppColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
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
                    Icon(AppIcons.Schedule, null, tint = AppColors.TextMuted, modifier = Modifier.size(14.dp))
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
                    Icon(AppIcons.CalendarToday, null, tint = AppColors.TextMuted, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(formattedDate, color = AppColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }

        if (!episode.description.isNullOrBlank()) {
            Text(
                text = episode.description,
                color = AppColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}
