@file:OptIn(ExperimentalFoundationApi::class)

package com.xtsream.app.screens.meta

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.xtsream.app.R
import com.xtsream.app.data.EpisodeItem
import com.xtsream.app.data.MetaDataViewModel
import com.xtsream.app.screens.skeletonEffect
import kotlin.math.min

@Composable
fun SeasonsSection(
    vm: MetaDataViewModel,
    mainPosterImg: String,
    onEpisodeSelect: (EpisodeItem) -> Unit,
    onDownloadClick: (EpisodeItem) -> Unit,
    onLongPress: (EpisodeItem) -> Unit,
    mainIdx: Int,
    subIdx: Int,
    rangeIdx: Int,
    onActiveSheetChange: (SheetType) -> Unit
) {
    val si = vm.seasonInfo
    val allEps = vm.currentEpisodes ?: si?.initialEpisodes ?: emptyList()
    val loadingMain = vm.isEpLoading
    val loadingSub = vm.isSubEpLoading

    val main = si?.seasons?.getOrNull(mainIdx)
    val sub = main?.parts?.getOrNull(subIdx)
    val hasSub = (main?.parts?.size ?: 0) > 1

    val chunkSize = 50
    val chunkedEps = allEps.chunked(chunkSize)
    val displayEps = chunkedEps.getOrElse(rangeIdx) { emptyList() }
    val hasRanges = chunkedEps.size > 1

    @Composable
    fun SelectorBox(text: String, hasArrow: Boolean, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(enabled = hasArrow) { onClick() }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            if (hasArrow) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(painter = painterResource(id = R.drawable.ic_arrow_down), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        if (si != null && si.seasons.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectorBox(main?.name ?: "Season", (si.seasons.size > 1)) { onActiveSheetChange(SheetType.SEASON) }
                if (hasSub) SelectorBox(sub?.name ?: "Part", true) { onActiveSheetChange(SheetType.SUB_SEASON) }
                if (hasRanges && !loadingMain && !loadingSub) {
                    val start = (rangeIdx * chunkSize) + 1
                    val end = min((rangeIdx + 1) * chunkSize, allEps.size)
                    SelectorBox("$start - $end", true) { onActiveSheetChange(SheetType.RANGE) }
                }
                Spacer(modifier = Modifier.weight(1f))
                
                val layoutIconRes = if (vm.layoutMode == 0) R.drawable.ic_layout_img else R.drawable.ic_layout_grid
                Icon(
                    painter = painterResource(id = layoutIconRes), 
                    contentDescription = "Toggle", 
                    tint = Color.White, 
                    modifier = Modifier.size(24.dp).clickable { vm.layoutMode = if (vm.layoutMode == 0) 1 else 0 }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (loadingMain || loadingSub) {
            EpSkeleton()
        } else if (displayEps.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (vm.layoutMode == 1) {
                    val gridChunks = displayEps.chunked(6)
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        gridChunks.forEach { rowEps ->
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowEps.forEach { ep ->
                                    val isSelected = vm.selectedEpisode?.id == ep.id
                                    val numStr = ep.epNo.padStart(2, '0')
                                    val itemBg = if (isSelected) Color.White.copy(alpha = 0.15f) else Color(0xFF1D1D1D)
                                    Box(
                                        modifier = Modifier.weight(1f).aspectRatio(1.15f).clip(RoundedCornerShape(6.dp)).background(itemBg)
                                            .combinedClickable(
                                                onClick = { onEpisodeSelect(ep) },
                                                onDoubleClick = { onDownloadClick(ep) },
                                                onLongClick = { onLongPress(ep) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) { Text(text = numStr, color = if (isSelected) adaptColorMeta(vm.dominantColor) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                                repeat(6 - rowEps.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                } else {
                    displayEps.forEach { e ->
                        EpisodeImageRow(e, mainPosterImg, vm, onEpisodeSelect, onDownloadClick, onLongPress)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun EpisodeImageRow(
    e: EpisodeItem,
    mainPosterImg: String,
    vm: MetaDataViewModel,
    onEpisodeSelect: (EpisodeItem) -> Unit,
    onDownloadClick: (EpisodeItem) -> Unit,
    onLongPress: (EpisodeItem) -> Unit
) {
    val isSelected = vm.selectedEpisode?.id == e.id
    val itemBg = if (isSelected) Color.White.copy(alpha = 0.08f) else Color.Transparent

    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(itemBg)
                .combinedClickable(
                    onClick = { onEpisodeSelect(e) },
                    onLongClick = { onLongPress(e) }
                )
                .padding(8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .width(135.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF222222)),
                    contentAlignment = Alignment.Center
                ) {
                    var isLoaded by remember { mutableStateOf(false) }
                    val targetImg = if (!e.img.isNullOrBlank()) e.img else mainPosterImg
                    val painter = rememberAsyncImagePainter(
                        model = ImageRequest.Builder(LocalContext.current).data(targetImg).crossfade(true).build(),
                        onState = { state -> isLoaded = state is AsyncImagePainter.State.Success }
                    )
                    Image(painter = painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (!isLoaded) Modifier.skeletonEffect() else Modifier))
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape).padding(4.dp))
                }

                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        text = "${e.epNo}. ${e.title}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                    
                    if (!e.plot.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = e.plot,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onDownloadClick(e) },
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(id = R.drawable.ic_download), contentDescription = "Download", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}