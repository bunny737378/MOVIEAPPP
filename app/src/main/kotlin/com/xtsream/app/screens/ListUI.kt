package com.xtsream.app.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.AppDarkBg
import com.xtsream.app.AppGlobalState
import com.xtsream.app.R
import com.xtsream.app.downloader.DownloadManager
import com.xtsream.app.manage.PluginConfig
import com.xtsream.app.manage.ProviderManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

fun loadAllDownloads(context: Context): List<DownloadItemInfo> {
    val root = File(context.getExternalFilesDir(null), "downloads")
    val list = mutableListOf<DownloadItemInfo>()
    if (!root.exists()) return list

    val activeFileBase = DownloadManager.getFinalFileName(
        DownloadManager.lastProviderId, DownloadManager.lastSeriesTitle, DownloadManager.lastEpisodeTitle, 
        DownloadManager.lastEpisodeId, DownloadManager.lastServerName, DownloadManager.selQualityTag
    )

    root.walkTopDown().filter { it.isFile && it.name.endsWith(".info") }.forEach { infoFile ->
        try {
            val jsonStr = infoFile.readText()
            val json = JSONObject(jsonStr)
            val fileBaseName = infoFile.nameWithoutExtension
            val targetDir = infoFile.parentFile ?: return@forEach

            val safeEpTitleFolder = json.optString("episodeTitle", "").replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
            val thumbFile = File(targetDir, "$safeEpTitleFolder.jpg")
            val mkvFile = File(targetDir, "$fileBaseName.mkv")

            val seriesTitle = json.optString("seriesTitle", "").ifBlank { json.optString("displayTitle", fileBaseName) }
            val episodeTitle = json.optString("episodeTitle", "")
            val displayTitle = json.optString("displayTitle", fileBaseName)
            val providerId = json.optString("providerId", "XSTREAM")
            val qualityTag = json.optString("qualityTag", "")
            val serverName = json.optString("serverName", "")
            val thumbUrl = json.optString("thumbUrl", "")

            val isCompleted = mkvFile.exists() && mkvFile.length() > 0L
            val isActive = DownloadManager.downloadStatus in listOf(1, 2) && (activeFileBase == fileBaseName)
            val fileSize = if (isCompleted) mkvFile.length() else 0L

            list.add(
                DownloadItemInfo(
                    fileBaseName = fileBaseName,
                    targetDir = targetDir,
                    seriesTitle = seriesTitle,
                    episodeTitle = episodeTitle,
                    displayTitle = displayTitle,
                    providerId = providerId,
                    qualityTag = qualityTag,
                    serverName = serverName,
                    thumbFile = thumbFile,
                    thumbUrl = thumbUrl,
                    mkvFile = mkvFile,
                    fileSize = fileSize,
                    isCompleted = isCompleted,
                    isActive = isActive,
                    json = json
                )
            )
        } catch (e: Exception) {}
    }
    return list
}

fun deleteDownload(item: DownloadItemInfo) {
    try {
        if (item.mkvFile.exists()) item.mkvFile.delete()
        val infoFile = File(item.targetDir, "${item.fileBaseName}.info")
        if (infoFile.exists()) infoFile.delete()
        val tmpDir = File(item.targetDir, "${item.fileBaseName}_tmp")
        if (tmpDir.exists()) tmpDir.deleteRecursively()
        if (item.thumbFile.exists()) item.thumbFile.delete()
        if (item.targetDir.listFiles()?.isEmpty() == true) {
            item.targetDir.delete()
        }
    } catch (e: Exception) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListUI(onProviderSelected: (PluginConfig) -> Unit) {
    var selectedTab by remember(AppGlobalState.activeListTab) { mutableIntStateOf(AppGlobalState.activeListTab) }
    val context = LocalContext.current
    var selectedPlaylistSeries by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(AppGlobalState.activeListTab) {
        selectedTab = AppGlobalState.activeListTab
    }

    LaunchedEffect(DownloadManager.downloadStatus) {
        refreshKey++
    }

    val downloadStatus = DownloadManager.downloadStatus
    val allDownloads = remember(refreshKey, downloadStatus) {
        loadAllDownloads(context)
    }

    if (selectedPlaylistSeries != null) {
        val seriesEpisodes = allDownloads.filter {
            it.isCompleted && (it.seriesTitle.trim().equals(selectedPlaylistSeries?.trim(), ignoreCase = true) ||
                    it.displayTitle.trim().equals(selectedPlaylistSeries?.trim(), ignoreCase = true))
        }
        if (seriesEpisodes.isNotEmpty()) {
            SeriesPlaylistSubPage(
                seriesTitle = selectedPlaylistSeries!!,
                episodes = seriesEpisodes,
                onBack = { selectedPlaylistSeries = null },
                onItemClick = { item -> playDownloadedMedia(context, item.mkvFile) },
                onItemDelete = { item ->
                    deleteDownload(item)
                    refreshKey++
                }
            )
            return
        } else {
            selectedPlaylistSeries = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppDarkBg).pointerInput(Unit) {}.statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(if (selectedTab == 0) Color.White else Color(0xFF222222))
                    .clickable {
                        selectedTab = 0
                        AppGlobalState.activeListTab = 0
                        selectedPlaylistSeries = null
                    }.padding(horizontal = 32.dp, vertical = 12.dp)
            ) { Text("Providers", color = if (selectedTab == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(if (selectedTab == 1) Color.White else Color(0xFF222222))
                    .clickable {
                        selectedTab = 1
                        AppGlobalState.activeListTab = 1
                    }.padding(horizontal = 32.dp, vertical = 12.dp)
            ) { Text("Downloads", color = if (selectedTab == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
        }

        if (selectedTab == 1) {
            val isLiveDownloading = DownloadManager.downloadStatus in listOf(1, 2, 4) && DownloadManager.lastUrlStr.isNotBlank()
            val activeBase = DownloadManager.getFinalFileName(
                DownloadManager.lastProviderId, DownloadManager.lastSeriesTitle, DownloadManager.lastEpisodeTitle,
                DownloadManager.lastEpisodeId, DownloadManager.lastServerName, DownloadManager.selQualityTag
            )
            val inProgressDownloads = allDownloads.filter { !it.isCompleted && (!isLiveDownloading || it.fileBaseName != activeBase) }
            val completedDownloads = allDownloads.filter { it.isCompleted }
            val completedGroups = completedDownloads.groupBy { it.seriesTitle.ifBlank { it.displayTitle }.trim() }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (DownloadManager.downloadStatus == -1) {
                    item {
                        Text(
                            text = "Download failed unrecoverably",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // 1. Live Active Downloading Card (Always shown when DownloadManager is downloading or paused)
                if (isLiveDownloading) {
                    item(key = "live_active_download") {
                        LiveDownloadingCard(
                            onPauseResume = {
                                if (DownloadManager.downloadStatus in listOf(1, 2)) {
                                    DownloadManager.cancelDownload()
                                } else {
                                    DownloadManager.resumeDownload(context)
                                }
                                refreshKey++
                            }
                        )
                    }
                }

                // 2. Any other paused downloads on disk
                if (inProgressDownloads.isNotEmpty()) {
                    items(inProgressDownloads, key = { it.fileBaseName }) { item ->
                        DownloadingEpisodeCard(
                            item = item,
                            onPauseResume = {
                                if (item.isActive) {
                                    DownloadManager.cancelDownload()
                                    refreshKey++
                                } else {
                                    val j = item.json
                                    val hMap = mutableMapOf<String, String>()
                                    j.optJSONObject("headers")?.let { h -> h.keys().forEach { hMap[it] = h.getString(it) } }
                                    val aList = mutableListOf<String>()
                                    j.optJSONArray("audioLangs")?.let { a -> for (i in 0 until a.length()) aList.add(a.getString(i)) }
                                    val sList = mutableListOf<String>()
                                    j.optJSONArray("subLangs")?.let { s -> for (i in 0 until s.length()) sList.add(s.getString(i)) }
                                    val extList = mutableListOf<String>()
                                    j.optJSONArray("externalSubs")?.let { ex -> for (i in 0 until ex.length()) extList.add(ex.getString(i)) }

                                    DownloadManager.startDownload(
                                        context = context,
                                        masterUrl = j.getString("masterUrl"),
                                        providerId = j.getString("providerId"),
                                        seriesTitle = j.getString("seriesTitle"),
                                        episodeTitle = j.getString("episodeTitle"),
                                        episodeId = j.getString("episodeId"),
                                        serverName = j.getString("serverName"),
                                        headers = hMap,
                                        streamType = j.getString("streamType"),
                                        qualityTag = j.getString("qualityTag"),
                                        audioLangs = aList,
                                        subLangs = sList,
                                        externalSubs = extList,
                                        thumbUrl = j.optString("thumbUrl", "")
                                    )
                                    DownloadManager.resumeDownload(context)
                                    refreshKey++
                                }
                            }
                        )
                    }
                }

                // Empty state
                if (!isLiveDownloading && inProgressDownloads.isEmpty() && completedDownloads.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            Text(text = "No downloaded episodes yet.", color = Color.Gray, fontSize = 15.sp)
                        }
                    }
                } else {
                    // 3. Completed downloads: Single card (HTML Episode 1 UI) or Series Playlist Card (HTML Mockup 2 UI)
                    completedGroups.forEach { (seriesTitle, episodes) ->
                        if (episodes.size == 1) {
                            item(key = episodes.first().fileBaseName) {
                                val single = episodes.first()
                                CompletedEpisodeCard(
                                    item = single,
                                    onClick = { playDownloadedMedia(context, single.mkvFile) },
                                    onDelete = {
                                        deleteDownload(single)
                                        refreshKey++
                                    }
                                )
                            }
                        } else {
                            item(key = "playlist_$seriesTitle") {
                                SeriesPlaylistCard(
                                    seriesTitle = seriesTitle,
                                    episodes = episodes,
                                    onClick = { selectedPlaylistSeries = seriesTitle }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Providers Tab
            val scope = rememberCoroutineScope()
            var selectedSheetPlugin by remember { mutableStateOf<PluginConfig?>(null) }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var isInstalling by remember { mutableStateOf(false) }
            var installProgress by remember { mutableIntStateOf(0) }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
                items(ProviderManager.plugins) { plugin ->
                    val isActive = ProviderManager.currentProvider?.id == plugin.id
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp)).background(if (isActive) Color(0xFF333333) else Color(0xFF1E1E1E)).border(1.dp, if (isActive) Color.White else Color.Transparent, RoundedCornerShape(8.dp)).clickable { if (plugin.isInstalled) onProviderSelected(plugin) else selectedSheetPlugin = plugin }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        coil.compose.AsyncImage(model = plugin.icon, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = plugin.name, color = Color.White, fontSize = 16.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))

                        if (!plugin.isInstalled) {
                            Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFBEF3D9)).clickable { selectedSheetPlugin = plugin; isInstalling = true; scope.launch { ProviderManager.installOrUpdatePlugin(context, plugin) { p -> installProgress = p }; isInstalling = false; selectedSheetPlugin = null } }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(text = "Install", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(painter = painterResource(id = R.drawable.ic_arrow_down), contentDescription = "Options", tint = Color.White, modifier = Modifier.size(20.dp).clickable { selectedSheetPlugin = plugin })
                        }
                    }
                }
            }

            if (selectedSheetPlugin != null) {
                val plugin = selectedSheetPlugin!!
                ModalBottomSheet(onDismissRequest = { if (!isInstalling) selectedSheetPlugin = null }, sheetState = sheetState, containerColor = Color(0xFF1A1A1A), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            coil.compose.AsyncImage(model = plugin.icon, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = plugin.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        if (isInstalling) {
                            WaveProgress(progress = installProgress, modifier = Modifier.size(120.dp))
                        } else {
                            if (plugin.isInstalled) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF333333)).clickable { ProviderManager.uninstallPlugin(context, plugin); selectedSheetPlugin = null }.padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(text = "Uninstall", color = Color.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFFBEF3D9)).clickable { isInstalling = true; scope.launch { ProviderManager.installOrUpdatePlugin(context, plugin) { p -> installProgress = p }; isInstalling = false; selectedSheetPlugin = null } }.padding(12.dp), contentAlignment = Alignment.Center) {
                                        Text(text = "Update", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFBEF3D9)).clickable { isInstalling = true; scope.launch { ProviderManager.installOrUpdatePlugin(context, plugin) { p -> installProgress = p }; isInstalling = false; selectedSheetPlugin = null } }.padding(12.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Install", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
