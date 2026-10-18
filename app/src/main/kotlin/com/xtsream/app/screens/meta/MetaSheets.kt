package com.xtsream.app.screens.meta

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xtsream.app.R
import com.xtsream.app.data.EpisodeItem
import com.xtsream.app.data.FullMetaData
import com.xtsream.app.data.MediaItem
import com.xtsream.app.data.MetaDataViewModel
import com.xtsream.app.downloader.DownloadManager
import com.xtsream.app.player.core.StreamServer
import com.xtsream.app.screens.CustomLoader

@Composable
fun DescriptionSheet(data: FullMetaData, mediaItem: MediaItem, descBg: Color, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(descBg).pointerInput(Unit) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> if (dragAmount > 15f) onClose() } }.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Description", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.clickable { onClose() })
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Text(text = mediaItem.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (data.basic.tags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(data.basic.tags) { tag -> Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha=0.1f)).padding(horizontal = 8.dp, vertical = 4.dp)) { Text(text = tag, color = Color.LightGray, fontSize = 11.sp) } }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(text = data.basic.plot, color = Color(0xDDFFFFFF), fontSize = 14.sp, lineHeight = 22.sp)
                Spacer(modifier = Modifier.height(24.dp))

                if (data.actors.isNotEmpty()) {
                    Text(text = "Cast", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    data.actors.chunked(4).forEach { rowActors ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowActors.forEach { actor ->
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (!actor.img.isNullOrBlank()) {
                                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(actor.img).crossfade(true).build(), contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.DarkGray), contentScale = ContentScale.Crop)
                                    } else {
                                        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.DarkGray))
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = actor.name ?: "", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!actor.role.isNullOrBlank()) Text(text = actor.role, color = Color.Gray, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            repeat(4 - rowActors.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun EpisodeInfoSheet(ep: EpisodeItem, mediaItem: MediaItem, mainPosterImg: String, descBg: Color, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(descBg).pointerInput(Unit) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> if (dragAmount > 15f) onClose() } }.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(mediaItem.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 16.dp))
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.clickable { onClose() })
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    val targetImg = if (!ep.img.isNullOrBlank()) ep.img else mainPosterImg
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(targetImg).crossfade(true).build(), contentDescription = null, modifier = Modifier.width(140.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray), contentScale = ContentScale.Crop)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "${ep.epNo}. ${ep.title}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (!ep.plot.isNullOrBlank()) {
                    Text(text = ep.plot, color = Color(0xDDFFFFFF), fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}

@Composable
fun SelectionSheet(options: List<SheetOpt>, title: String, descBg: Color, onClose: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(descBg).pointerInput(Unit) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> if (dragAmount > 15f) onClose() } }.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.clickable { onClose() })
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
        
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 16.dp)) {
            items(options.chunked(3)) { rowOpts ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowOpts.forEach { opt ->
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (opt.isSelected) Color.White else Color.White.copy(alpha=0.1f)).clickable { opt.onClick() }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(opt.text, color = if (opt.isSelected) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    repeat(3 - rowOpts.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun DownloadSheet(vm: MetaDataViewModel, ctx: android.content.Context, mediaItem: MediaItem, dlEpisode: EpisodeItem, descBg: Color, onClose: () -> Unit, onDownloadStarted: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(descBg).pointerInput(Unit) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) { detectVerticalDragGestures { _, dragAmount -> if (dragAmount > 15f) onClose() } }.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Download", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.clickable { onClose() })
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))

        if (vm.isFetchingStreams) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CustomLoader() }
        } else if (!vm.streamServers.isNullOrEmpty()) {
            var selectedServer by remember { mutableStateOf<StreamServer?>(null) }
            var expandedServerName by remember { mutableStateOf<String?>(null) }
            var selectedQuality by remember { mutableStateOf<String?>(null) }
            val checkedAudios = remember { mutableStateListOf<String>() }
            val checkedSubs = remember { mutableStateListOf<String>() }
            
            val epTitleSafe = dlEpisode.title?.takeIf { it.isNotBlank() } ?: "Ep_${dlEpisode.epNo}"

            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                items(vm.streamServers!!) { server ->
                    val isStreamable = server.type.contains("m3u8", ignoreCase = true) || server.type.contains("dash", ignoreCase = true) || server.type.contains("mpd", ignoreCase = true)
                    
                    val isDirectDownloaded = !isStreamable && DownloadManager.isAlreadyDownloadedOrActive(
                        ctx, mediaItem.providerId, mediaItem.title, epTitleSafe, dlEpisode.id, server.name, ""
                    )
                    
                    val isDirectCheck = selectedServer?.name == server.name
                    val isExpanded = expandedServerName == server.name

                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha=0.1f))
                            .then(if (isDirectDownloaded) Modifier else Modifier.clickable {
                                if (isStreamable) {
                                    if (expandedServerName != server.name) {
                                        expandedServerName = server.name
                                        selectedServer = server
                                        vm.parseStreamManifest(server)
                                        selectedQuality = null
                                        checkedAudios.clear()
                                        checkedSubs.clear()
                                    } else {
                                        expandedServerName = null
                                        selectedServer = null
                                    }
                                } else {
                                    selectedServer = server
                                    expandedServerName = null
                                }
                            })
                            .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isDirectDownloaded) "${server.name} (Downloaded)" else "${server.name} (${server.type.uppercase()})", 
                                    color = if (isDirectDownloaded) Color.Gray else Color.White, 
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (server.qualities.isNotEmpty() || server.audios.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(server.qualities.joinToString(", "), color = Color.LightGray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(server.audios.joinToString(", "), color = Color.LightGray, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            if (isStreamable) {
                                Icon(painterResource(id = R.drawable.ic_arrow_down), null, tint = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Box(modifier = Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (isDirectCheck) Color.White else Color.Gray, CircleShape).background(if (isDirectCheck) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
                                    if (isDirectCheck) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha=0.2f)).padding(12.dp)) {
                                if (vm.isParsingManifest || vm.parsedManifest == null) {
                                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CustomLoader() }
                                } else {
                                    val manifest = vm.parsedManifest!!
                                    val qList = if (manifest.qualities.isNotEmpty()) manifest.qualities else server.qualities.distinct()
                                    val aList = if (manifest.audios.isNotEmpty()) manifest.audios else server.audios.distinct()
                                    val sList = if (manifest.subtitles.isNotEmpty()) manifest.subtitles else (server.embSubs + server.extSubs.map { it.label }).distinct()

                                    LaunchedEffect(qList, aList, sList) {
                                        if (qList.isNotEmpty() && selectedQuality == null) {
                                            val firstValid = qList.firstOrNull { q -> 
                                                !DownloadManager.isAlreadyDownloadedOrActive(ctx, mediaItem.providerId, mediaItem.title, epTitleSafe, dlEpisode.id, server.name, q)
                                            }
                                            selectedQuality = firstValid
                                        }
                                        if (aList.isNotEmpty() && checkedAudios.isEmpty()) checkedAudios.add(aList.first())
                                        if (sList.isNotEmpty() && checkedSubs.isEmpty()) checkedSubs.add(sList.first())
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Quality", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            qList.forEach { q ->
                                                val isQDownloaded = DownloadManager.isAlreadyDownloadedOrActive(ctx, mediaItem.providerId, mediaItem.title, epTitleSafe, dlEpisode.id, server.name, q)
                                                val isChecked = selectedQuality == q
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically, 
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                    .then(if (isQDownloaded) Modifier else Modifier.clickable { if (qList.size > 1) selectedQuality = q })
                                                ) {
                                                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).border(2.dp, if (isChecked) Color.White else Color.Gray, CircleShape).background(if (isChecked) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
                                                        if (isChecked) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(10.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(if (isQDownloaded) "$q (Downloaded)" else q, color = if (isQDownloaded) Color.Gray else Color.White, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Audio", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            aList.forEach { a ->
                                                val isChecked = checkedAudios.contains(a)
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp).clickable {
                                                    if (isChecked && checkedAudios.size > 1) checkedAudios.remove(a)
                                                    else if (!isChecked) checkedAudios.add(a)
                                                }) {
                                                    Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, if (isChecked) Color.White else Color.Gray, RoundedCornerShape(4.dp)).background(if (isChecked) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
                                                        if (isChecked) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(a, color = Color.White, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Subtitle", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            sList.forEach { s ->
                                                val isChecked = checkedSubs.contains(s)
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp).clickable { if (isChecked) checkedSubs.remove(s) else checkedSubs.add(s) }) {
                                                    Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, if (isChecked) Color.White else Color.Gray, RoundedCornerShape(4.dp)).background(if (isChecked) Color.White else Color.Transparent), contentAlignment = Alignment.Center) {
                                                        if (isChecked) Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(s, color = Color.White, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val isM3u8Dash = selectedServer?.type?.let { it.contains("m3u8", true) || it.contains("dash", true) || it.contains("mpd", true) } ?: false
            val isDownloadReady = selectedServer != null && (!isM3u8Dash || selectedQuality != null)

            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).navigationBarsPadding().height(50.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (isDownloadReady) Color.White.copy(alpha=0.15f) else Color.White.copy(alpha=0.05f))
                    .clickable(enabled = isDownloadReady) {
                        val extSubUrls = selectedServer!!.extSubs.filter { checkedSubs.contains(it.label) }.map { it.url }
                        
                        val epThumb = dlEpisode.img?.takeIf { it.isNotBlank() } ?: mediaItem.img
                        
                        DownloadManager.startDownload(
                            context = ctx,
                            masterUrl = selectedServer!!.url,
                            providerId = mediaItem.providerId,
                            seriesTitle = mediaItem.title,
                            episodeTitle = dlEpisode.title?.takeIf { it.isNotBlank() } ?: "Ep_${dlEpisode.epNo}",
                            episodeId = dlEpisode.id,
                            serverName = selectedServer!!.name,
                            headers = selectedServer!!.headers,
                            streamType = selectedServer!!.type,
                            qualityTag = selectedQuality ?: "",
                            audioLangs = checkedAudios.toList(),
                            subLangs = checkedSubs.toList(),
                            externalSubs = extSubUrls,
                            thumbUrl = epThumb
                        )
                        onDownloadStarted()
                        onClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Start Download", color = if (isDownloadReady) Color.White else Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No download links available.", color = Color.Gray) }
        }
    }
}
