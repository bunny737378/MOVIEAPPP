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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xtsream.app.AppDarkBg
import com.xtsream.app.downloader.DownloadManager
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
    if (clean.length > 26 && clean.contains(" ")) {
        val words = clean.split(" ")
        var l1 = ""
        var l2 = ""
        for (w in words) {
            if ((l1 + " " + w).trim().length <= 26 && l2.isEmpty()) {
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
        context.startActivity(Intent.createChooser(intent, "Play with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to play video: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/* =========================================================================
   IN-PROGRESS / ACTIVE DOWNLOAD CARD (HTML Episode 2)
   16:9 Thumbnail, Title lines, Provider tag, Download stats, Sky-blue to Navy Gradient Bar, Pause button
   ========================================================================= */
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
        title = item.seriesTitle.ifBlank { item.displayTitle },
        subtitle = item.episodeTitle.takeIf { it.isNotBlank() && it != item.seriesTitle }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Plain 16:9 Thumbnail
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

        // Details exactly matching thumbnail height (70dp)
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
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Metadata: Provider badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF252525))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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

            // Download stats: "78.4/302 MB", "5 MB/s", "45 sec left"
            val downloadedText = if (DownloadManager.totalMb.isNotBlank()) {
                "${DownloadManager.downloadedMb}/${DownloadManager.totalMb}"
            } else {
                DownloadManager.downloadedMb
            }
            val speedText = if (isMerging) {
                "Merging... ${DownloadManager.mergeProgressPercent}%"
            } else {
                DownloadManager.downloadSpeed
            }
            val timeText = if (isMerging) "" else DownloadManager.etaText

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = downloadedText,
                    color = Color(0xFFBCBCBC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = speedText,
                    color = Color(0xFFDEDEDE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                if (timeText.isNotBlank() && timeText != "--") {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = timeText,
                        color = Color(0xFF9EBEE7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sky-blue to Navy progress bar
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
                        .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF87CEEB), Color(0xFF102A56))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pause/Resume button
        IconButton(
            onClick = onPauseResume,
            modifier = Modifier.size(30.dp)
        ) {
            if (item.isActive && !isErrPaused) {
                Row(
                    modifier = Modifier.size(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                    Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/* =========================================================================
   SINGLE COMPLETED EPISODE CARD (HTML Episode 1)
   Plain 16:9 Thumbnail, 2-line title, Provider tag, Size (No progress bar / percentage)
   ========================================================================= */
@Composable
fun CompletedEpisodeCard(
    item: DownloadItemInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val (line1, line2) = splitTitle(
        title = item.seriesTitle.ifBlank { item.displayTitle },
        subtitle = item.episodeTitle.takeIf { it.isNotBlank() && it != item.seriesTitle }
    )
    val sizeText = formatFileSize(item.fileSize)
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Plain 16:9 Thumbnail
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

        // Details matching thumbnail height (70dp)
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
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Metadata: Provider badge + file size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF252525))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.providerId.uppercase(),
                        color = Color(0xFFD2D2D2),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
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
                Text(text = item.displayTitle, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Choose an action for this video (${sizeText})", color = Color.LightGray, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onClick()
                }) {
                    Text("Play", color = Color(0xFF87CEEB), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showDialog = false
                        onDelete()
                    }) {
                        Text("Delete", color = Color(0xFFFF5252))
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            }
        )
    }
}

/* =========================================================================
   SERIES PLAYLIST CARD (HTML Mockup 2)
   Multi-Card Stacked Cover effect, Series Title, Provider, Combined Size, Episode Count
   ========================================================================= */
@Composable
fun SeriesPlaylistCard(
    seriesTitle: String,
    episodes: List<DownloadItemInfo>,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val firstItem = episodes.firstOrNull() ?: return
    val (line1, line2) = splitTitle(seriesTitle)
    val totalBytes = episodes.sumOf { it.fileSize }
    val totalSizeText = formatFileSize(totalBytes)
    val provider = firstItem.providerId.uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Multi-card Stacked Cover effect (matching CSS .cover-stack::before and ::after)
        Box(
            modifier = Modifier
                .width(124.dp)
                .height(70.dp),
            contentAlignment = Alignment.Center
        ) {
            // Bottom card: rotated -3deg, scaled 0.90, background #20202B
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = 4.dp.toPx()
                        translationY = -6.dp.toPx()
                        rotationZ = -3f
                        scaleX = 0.90f
                        scaleY = 0.90f
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF20202B))
            )

            // Middle card: rotated 3deg, scaled 0.94, background #303039
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = 7.dp.toPx()
                        translationY = -3.dp.toPx()
                        rotationZ = 3f
                        scaleX = 0.94f
                        scaleY = 0.94f
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF303039))
            )

            // Top front thumbnail (16:9)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF181818)),
                contentAlignment = Alignment.Center
            ) {
                val thumbFile = episodes.firstOrNull { it.thumbFile.exists() }?.thumbFile
                val thumbUrl = episodes.firstOrNull { it.thumbUrl.isNotBlank() }?.thumbUrl ?: ""
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(if (thumbFile != null && thumbFile.exists()) thumbFile else thumbUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(18.dp))

        // Details matching thumbnail height (70dp)
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
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata row: Provider + Total size + Episode count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF252525))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
                    text = "• ${episodes.size} Episodes",
                    color = Color(0xFF858585),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* =========================================================================
   SERIES PLAYLIST SUB-PAGE (Opens on clicking Series Playlist Card)
   Header with Back button + Series title + count, followed by each episode (HTML Episode 1 UI)
   ========================================================================= */
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppDarkBg)
            .statusBarsPadding()
    ) {
        // Sub-page top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = seriesTitle,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${episodes.size} Episodes • Total ${formatFileSize(totalBytes)}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        HorizontalDivider(color = Color(0x22FFFFFF), thickness = 1.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)
        ) {
            items(episodes, key = { it.fileBaseName }) { ep ->
                CompletedEpisodeCard(
                    item = ep,
                    onClick = { onItemClick(ep) },
                    onDelete = { onItemDelete(ep) }
                )
            }
        }
    }
}

/* =========================================================================
   LIVE ACTIVE DOWNLOADING CARD
   Directly bound to DownloadManager's observable StateFlow/MutableState properties
   ========================================================================= */
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
        title = DownloadManager.lastSeriesTitle.ifBlank { DownloadManager.activeTitle },
        subtitle = DownloadManager.lastEpisodeTitle.takeIf { it.isNotBlank() && it != DownloadManager.lastSeriesTitle }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 16:9 Thumbnail
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

        // Details exactly matching thumbnail height (70dp)
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
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!line2.isNullOrBlank()) {
                Text(
                    text = line2,
                    color = Color(0xFFC4C8D2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Metadata: Provider badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF252525))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = DownloadManager.lastProviderId.ifBlank { "STREAM" }.uppercase(),
                        color = Color(0xFFD2D2D2),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp
                    )
                }
            }

            // Download stats: "78.4/302 MB", "5 MB/s", "45 sec left"
            val downloadedText = if (DownloadManager.totalMb.isNotBlank()) {
                "${DownloadManager.downloadedMb}/${DownloadManager.totalMb}"
            } else {
                DownloadManager.downloadedMb
            }
            val speedText = if (isMerging) {
                "Merging... ${DownloadManager.mergeProgressPercent}%"
            } else {
                DownloadManager.downloadSpeed
            }
            val timeText = if (isMerging) "" else DownloadManager.etaText

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = downloadedText,
                    color = Color(0xFFBCBCBC),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = speedText,
                    color = Color(0xFFDEDEDE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                if (timeText.isNotBlank() && timeText != "--") {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = timeText,
                        color = Color(0xFF9EBEE7),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Sky-blue to Navy progress bar
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
                        .fillMaxWidth(fraction = progressFraction.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF87CEEB), Color(0xFF102A56))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pause/Resume button
        IconButton(
            onClick = onPauseResume,
            modifier = Modifier.size(30.dp)
        ) {
            if (!isErrPaused) {
                Row(
                    modifier = Modifier.size(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                    Box(modifier = Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(1.dp)).background(Color.White))
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
