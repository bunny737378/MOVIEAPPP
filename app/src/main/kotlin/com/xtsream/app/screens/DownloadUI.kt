package com.xtsream.app.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.xtsream.app.AppDarkBg
import com.xtsream.app.downloader.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class DownloadItemInfo(
    val fileBaseName: String,
    val targetDir: File,
    val seriesTitle: String,
    val episodeTitle: String,
    val displayTitle: String,
    val providerId: String,
    val qualityTag: String,
    val serverName: String,
    val thumbFile: File,
    val thumbUrl: String,
    val mkvFile: File,
    val fileSize: Long,
    val isCompleted: Boolean,
    val isActive: Boolean,
    val json: JSONObject
)

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return if (gb >= 1.0) {
        String.format(Locale.US, "%.1f GB", gb)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

fun splitTitle(title: String, subtitle: String? = null): Pair<String, String?> {
    if (!subtitle.isNullOrBlank() && subtitle.trim() != title.trim()) {
        return Pair(title.trim(), subtitle.trim())
    }
    
    val clean = title.trim()
    val limit = 38
    
    if (clean.length > limit && clean.contains(" ")) {
        val words = clean.split(" ")
        var l1 = ""
        var l2 = ""
        
        for (w in words) {
            if ((l1 + " " + w).trim().length <= limit && l2.isEmpty()) {
                l1 = (l1 + " " + w).trim()
            } else {
                l2 = (l2 + " " + w).trim()
            }
        }
        return Pair(l1.ifEmpty { clean }, l2.ifEmpty { null })
    }
    return Pair(clean, null)
}

fun playDownloadedMedia(context: Context, mkvFile: File) {
    try {
        if (!mkvFile.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            mkvFile
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Play Video with...")
        context.startActivity(chooser)
        
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to play video: Check FileProvider setup", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun rememberDominantColors(imageModel: Any?): Pair<Color, Color> {
    val context = LocalContext.current
    var colors by remember { 
        mutableStateOf(Pair(Color(0xFF303039), Color(0xFF20202B))) 
    }

    LaunchedEffect(imageModel) {
        if (imageModel == null || (imageModel is File && !imageModel.exists()) || (imageModel is String && imageModel.isBlank())) {
            return@LaunchedEffect
        }
        
        launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(imageModel)
                    .allowHardware(false)
                    .build()
                    
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val bitmap = result?.toBitmap()
                
                bitmap?.let {
                    val palette = Palette.from(it).generate()
                    val color1 = Color(palette.getVibrantColor(0xFF303039.toInt()))
                    val color2 = Color(palette.getDarkMutedColor(0xFF20202B.toInt()))
                    colors = Pair(color1, color2)
                }
            } catch (e: Exception) {
            }
        }
    }
    return colors
}

@Composable
fun DownloadingEpisodeCard(
    item: DownloadItemInfo,
    onPauseResume: () -> Unit
) {
    val context = LocalContext.current
    val isMerging = DownloadManager.downloadStatus == 2 && item.isActive
    val isErrPaused = !item.isCompleted && DownloadManager.downloadStatus == 4 && item.isActive
    
    val progressFraction = if (isMerging) {
        (DownloadManager.mergeProgressPercent / 100f).coerceIn(0f, 1f)
    } else {
        (DownloadManager.progressPercent / 100f).coerceIn(0f, 1f)
    }

    val (line1, line2) = splitTitle(
        title = item.episodeTitle.ifBlank { item.displayTitle }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(124.dp)
                .height(70.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF181818)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(if (item.thumbFile.exists()) item.thumbFile else item.thumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .height(70.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = line1,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight(650),
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val downloadedText = if (DownloadManager.totalMb.isNotBlank()) {
                "${DownloadManager.downloadedMb}/${DownloadManager.totalMb} MB"
            } else {
                "${DownloadManager.downloadedMb} MB"
            }
            
            val speedText = if (isMerging) {
                "Merging ${DownloadManager.mergeProgressPercent}%"
            } else {
                "${DownloadManager.downloadSpeed} MB/s"
            }
            
            val timeText = if (isMerging) "" else DownloadManager.etaText

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$downloadedText   $speedText",
                    color = Color(0xFFBCBCBC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (timeText.isNotBlank() && timeText != "--") {
                    Text(
                        text = timeText,
                        color = Color(0xFF9EBEE7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF242831))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.01f))
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF87CEEB), Color(0xFF102A56))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(
            onClick = onPauseResume,
            modifier = Modifier.size(22.dp)
        ) {
            if (item.isActive && !isErrPaused) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun CompletedEpisodeCard(
    item: DownloadItemInfo,
    isInnerPlaylist: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val (line1, line2) = splitTitle(
        title = item.episodeTitle.ifBlank { item.displayTitle }
    )
    
    val showSeries = !isInnerPlaylist && 
                     item.seriesTitle.isNotBlank() && 
                     item.seriesTitle.trim() != item.episodeTitle.trim() && 
                     item.seriesTitle.trim() != item.displayTitle.trim()
                     
    val sizeText = formatFileSize(item.fileSize)
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(124.dp)
                .height(70.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF181818)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(if (item.thumbFile.exists()) item.thumbFile else item.thumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .height(70.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = line1,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight(650),
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                )
            }
            
            if (showSeries) {
                Text(
                    text = item.seriesTitle.trim(),
                    color = Color(0xFF999999),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isInnerPlaylist) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF252525))
                            .padding(horizontal = 7.dp, vertical = 1.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.providerId.uppercase(),
                            color = Color(0xFFD2D2D2),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
                
                Text(
                    text = sizeText,
                    color = Color(0xFF858585),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF1E1E24),
            title = {
                Text(
                    text = item.displayTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Choose an action for this video ($sizeText)",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    showDialog = false
                    onClick() 
                }) {
                    Text(
                        text = "Play",
                        color = Color(0xFF87CEEB),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { 
                        showDialog = false
                        onDelete() 
                    }) {
                        Text(
                            text = "Delete",
                            color = Color(0xFFFF5252)
                        )
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text(
                            text = "Cancel",
                            color = Color.Gray
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun SeriesPlaylistCard(
    seriesTitle: String,
    episodes: List<DownloadItemInfo>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val firstItem = episodes.firstOrNull() ?: return
    val (line1, line2) = splitTitle(title = seriesTitle)
    val totalBytes = episodes.sumOf { it.fileSize }
    val totalSizeText = formatFileSize(totalBytes)
    val provider = firstItem.providerId.uppercase()

    val imageModel = if (firstItem.thumbFile.exists()) firstItem.thumbFile else firstItem.thumbUrl.ifBlank { null }
    val (color1, color2) = rememberDominantColors(imageModel)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(124.dp)
                .height(70.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = 10.dp.toPx()
                        translationY = (-6).dp.toPx()
                        rotationZ = 6f
                        scaleX = 0.80f
                        scaleY = 0.80f
                    }
                    .clip(RoundedCornerShape(7.dp))
                    .background(Brush.linearGradient(listOf(color1, color2)))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = 5.dp.toPx()
                        translationY = (-3).dp.toPx()
                        rotationZ = 3f
                        scaleX = 0.88f
                        scaleY = 0.88f
                    }
                    .clip(RoundedCornerShape(7.dp))
                    .background(Brush.linearGradient(listOf(color2, color1)))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (-3).dp.toPx()
                        translationY = 3.dp.toPx()
                        scaleX = 0.96f
                        scaleY = 0.96f
                    }
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF181818)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(18.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .height(70.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = line1,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight(650),
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF252525))
                        .padding(horizontal = 7.dp, vertical = 1.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = provider,
                        color = Color(0xFFD2D2D2),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
                
                Text(
                    text = totalSizeText,
                    color = Color(0xFF858585),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = "• ${episodes.size} Eps",
                    color = Color(0xFF858585),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SeriesPlaylistSubPage(
    seriesTitle: String,
    episodes: List<DownloadItemInfo>,
    onBack: () -> Unit,
    onItemClick: (DownloadItemInfo) -> Unit,
    onItemDelete: (DownloadItemInfo) -> Unit
) {
    BackHandler { onBack() }
    val totalBytes = episodes.sumOf { it.fileSize }
    
    val (hLine1, hLine2) = splitTitle(title = seriesTitle)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDarkBg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hLine1,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (!hLine2.isNullOrBlank()) {
                    Text(
                        text = hLine2,
                        color = Color(0xFFC4C8D2),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp, bottom = 2.dp)
                    )
                }
                
                Text(
                    text = "${episodes.size} Episodes • Total ${formatFileSize(totalBytes)}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        HorizontalDivider(
            color = Color(0x22FFFFFF),
            thickness = 1.dp
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
        ) {
            items(episodes, key = { it.fileBaseName }) { ep ->
                CompletedEpisodeCard(
                    item = ep,
                    isInnerPlaylist = true,
                    onClick = { onItemClick(ep) },
                    onDelete = { onItemDelete(ep) }
                )
            }
        }
    }
}

@Composable
fun LiveDownloadingCard(
    onPauseResume: () -> Unit
) {
    val context = LocalContext.current
    val isMerging = DownloadManager.downloadStatus == 2
    val isErrPaused = DownloadManager.downloadStatus == 4
    
    val progressFraction = if (isMerging) {
        (DownloadManager.mergeProgressPercent / 100f).coerceIn(0f, 1f)
    } else {
        (DownloadManager.progressPercent / 100f).coerceIn(0f, 1f)
    }

    val (line1, line2) = splitTitle(
        title = DownloadManager.lastEpisodeTitle.ifBlank { DownloadManager.activeTitle }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(124.dp)
                .height(70.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF181818)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(DownloadManager.lastThumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .height(70.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = line1,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight(650),
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val downloadedText = if (DownloadManager.totalMb.isNotBlank()) {
                "${DownloadManager.downloadedMb}/${DownloadManager.totalMb} MB"
            } else {
                "${DownloadManager.downloadedMb} MB"
            }
            
            val speedText = if (isMerging) {
                "Merging ${DownloadManager.mergeProgressPercent}%"
            } else {
                "${DownloadManager.downloadSpeed} MB/s"
            }
            
            val timeText = if (isMerging) "" else DownloadManager.etaText

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$downloadedText   $speedText",
                    color = Color(0xFFBCBCBC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (timeText.isNotBlank() && timeText != "--") {
                    Text(
                        text = timeText,
                        color = Color(0xFF9EBEE7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF242831))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.01f))
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF87CEEB), Color(0xFF102A56))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        IconButton(
            onClick = onPauseResume,
            modifier = Modifier.size(22.dp)
        ) {
            if (!isErrPaused) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
