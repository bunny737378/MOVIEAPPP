package com.xtsream.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.xtsream.app.AppDarkBg
import com.xtsream.app.AppGlobalState
import com.xtsream.app.R
import com.xtsream.app.downloader.DownloadManager
import com.xtsream.app.manage.PluginConfig
import com.xtsream.app.manage.ProviderManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

data class DownloadItemInfo(
    val fileBaseName: String,
    val targetDir: File,
    val displayTitle: String,
    val thumbFile: File,
    val isCompleted: Boolean,
    val isActive: Boolean,
    val json: JSONObject
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListUI(onProviderSelected: (PluginConfig) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(AppGlobalState.activeListTab) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(AppDarkBg).pointerInput(Unit){}.statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                    .background(if (selectedTab == 0) Color.White else Color(0xFF222222))
                    .clickable { selectedTab = 0; AppGlobalState.activeListTab = 0 }.padding(horizontal = 32.dp, vertical = 12.dp)
            ) { Text("Providers", color = if (selectedTab == 0) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(if (selectedTab == 1) Color.White else Color(0xFF222222))
                    .clickable { selectedTab = 1; AppGlobalState.activeListTab = 1 }.padding(horizontal = 32.dp, vertical = 12.dp)
            ) { Text("Downloads", color = if (selectedTab == 1) Color.Black else Color.White, fontWeight = FontWeight.Bold) }
        }

        if (selectedTab == 1) {
            val root = File(context.getExternalFilesDir(null), "downloads")
            val downloadItems = mutableListOf<DownloadItemInfo>()
            
            if (root.exists()) {
                val activeFileBase = DownloadManager.getFinalFileName(
                    DownloadManager.lastProviderId, DownloadManager.lastSeriesTitle, DownloadManager.lastEpisodeTitle, 
                    DownloadManager.lastEpisodeId, DownloadManager.lastServerName, DownloadManager.selQualityTag
                )
                
                root.walkTopDown().filter { it.isFile && it.name.endsWith(".info") }.forEach { infoFile ->
                    try {
                        val jsonStr = infoFile.readText()
                        val json = JSONObject(jsonStr)
                        val fileBaseName = infoFile.nameWithoutExtension
                        val targetDir = infoFile.parentFile!!
                        
                        val safeEpTitleFolder = json.optString("episodeTitle", "").replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
                        val thumbFile = File(targetDir, "$safeEpTitleFolder.jpg")
                        val mkvFile = File(targetDir, "$fileBaseName.mkv")
                        
                        val displayTitle = json.optString("displayTitle", fileBaseName)
                        val isCompleted = mkvFile.exists() && mkvFile.length() > 0
                        
                        val isActive = DownloadManager.downloadStatus in listOf(1, 2) && (activeFileBase == fileBaseName)
                        
                        downloadItems.add(DownloadItemInfo(fileBaseName, targetDir, displayTitle, thumbFile, isCompleted, isActive, json))
                    } catch(e: Exception) {}
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
                
                if (DownloadManager.downloadStatus == -1) {
                    item {
                        Text("Last Download Failed Unrecoverably", color = Color.Red, modifier = Modifier.padding(bottom = 16.dp))
                    }
                }

                if (downloadItems.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                            Text(text = "No downloaded episodes yet.", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                } else {
                    items(downloadItems.sortedBy { it.isCompleted }) { item ->
                        var isLoaded by remember { mutableStateOf(false) }
                        val painter = rememberAsyncImagePainter(
                            model = ImageRequest.Builder(context).data(item.thumbFile).crossfade(true).build(),
                            onState = { state -> isLoaded = state is AsyncImagePainter.State.Success }
                        )
                        
                        val isPaused = !item.isCompleted && !item.isActive
                        val isErrPaused = !item.isCompleted && DownloadManager.downloadStatus == 4 && item.fileBaseName == DownloadManager.getFinalFileName(DownloadManager.lastProviderId, DownloadManager.lastSeriesTitle, DownloadManager.lastEpisodeTitle, DownloadManager.lastEpisodeId, DownloadManager.lastServerName, DownloadManager.selQualityTag)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.width(100.dp).aspectRatio(16f/9f)
                                    .clip(RoundedCornerShape(6.dp)).background(Color(0xFF151515)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(painter = painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (!isLoaded) Modifier.skeletonEffect() else Modifier))
                            }
                            
                            Spacer(modifier = Modifier.width(14.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.displayTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                
                                if (item.isCompleted) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val mkv = File(item.targetDir, "${item.fileBaseName}.mkv")
                                    val sizeMb = String.format("%.1f MB", mkv.length() / (1024f * 1024f))
                                    val pName = item.json.optString("providerId", "")
                                    val sName = item.json.optString("serverName", "")
                                    Text(text = "$sizeMb • $pName • $sName", color = Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                } else if (item.isActive || isErrPaused) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val sizeText = if (DownloadManager.totalMb.isBlank()) DownloadManager.downloadedMb else "${DownloadManager.downloadedMb} / ${DownloadManager.totalMb}"
                                    val isMerging = DownloadManager.downloadStatus == 2
                                    
                                    LinearProgressIndicator(
                                        progress = { if (isMerging) DownloadManager.mergeProgressPercent / 100f else DownloadManager.progressPercent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                        color = if (isErrPaused) Color(0xFFFFB74D) else Color(0xFFBEF3D9),
                                        trackColor = Color(0xFF333333)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = sizeText, color = Color.Gray, fontSize = 11.sp)
                                        Text(
                                            text = if (isErrPaused) "Error/Paused" else if (isMerging) "Merging... ${DownloadManager.mergeProgressPercent}%" else DownloadManager.downloadSpeed, 
                                            color = if (isErrPaused || isMerging) Color(0xFFFFB74D) else Color(0xFFBEF3D9), 
                                            fontSize = 11.sp, fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Paused - Tap play to resume", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                            
                            // Control Button
                            if (!item.isCompleted) {
                                Spacer(modifier = Modifier.width(12.dp))
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(Color.White.copy(alpha=0.1f)).clickable {
                                            if (item.isActive) {
                                                DownloadManager.cancelDownload()
                                            } else {
                                                val j = item.json
                                                val hMap = mutableMapOf<String, String>()
                                                j.optJSONObject("headers")?.let { h -> h.keys().forEach { hMap[it] = h.getString(it) } }
                                                val aList = mutableListOf<String>(); j.optJSONArray("audioLangs")?.let { a -> for(i in 0 until a.length()) aList.add(a.getString(i)) }
                                                val sList = mutableListOf<String>(); j.optJSONArray("subLangs")?.let { s -> for(i in 0 until s.length()) sList.add(s.getString(i)) }
                                                val extList = mutableListOf<String>(); j.optJSONArray("externalSubs")?.let { ex -> for(i in 0 until ex.length()) extList.add(ex.getString(i)) }

                                                DownloadManager.startDownload(
                                                    context = context, masterUrl = j.getString("masterUrl"), providerId = j.getString("providerId"),
                                                    seriesTitle = j.getString("seriesTitle"), episodeTitle = j.getString("episodeTitle"),
                                                    episodeId = j.getString("episodeId"), serverName = j.getString("serverName"),
                                                    headers = hMap, streamType = j.getString("streamType"), qualityTag = j.getString("qualityTag"),
                                                    audioLangs = aList, subLangs = sList, externalSubs = extList, thumbUrl = j.optString("thumbUrl", "")
                                                )
                                                DownloadManager.resumeDownload(context)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isActive) Icons.Default.Pause else Icons.Default.PlayArrow, 
                                        contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val scope = rememberCoroutineScope()
            var selectedSheetPlugin by remember { mutableStateOf<PluginConfig?>(null) }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            var isInstalling by remember { mutableStateOf(false) }
            var installProgress by remember { mutableIntStateOf(0) }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
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
