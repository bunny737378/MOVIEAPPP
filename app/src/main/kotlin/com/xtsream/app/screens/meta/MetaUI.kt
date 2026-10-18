@file:OptIn(ExperimentalFoundationApi::class)

package com.xtsream.app.screens.meta

import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.xtsream.app.AppDarkBg
import com.xtsream.app.AppGlobalState
import com.xtsream.app.R
import com.xtsream.app.data.EpisodeItem
import com.xtsream.app.data.MediaItem
import com.xtsream.app.data.MetaDataViewModel
import com.xtsream.app.screens.MediaPosterCard
import com.xtsream.app.screens.SwipeToBackBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

@Composable
fun MetaUI(mediaItem: MediaItem, onBack: () -> Unit, onMediaClick: (MediaItem) -> Unit, onNavigateToDownloads: () -> Unit) {
    SwipeToBackBox(onBack = onBack) {
        val vm: MetaDataViewModel = viewModel(key = mediaItem.id)
        val ctx = LocalContext.current
        val activity = ctx as ComponentActivity
        val scope = rememberCoroutineScope()

        LaunchedEffect(mediaItem) { scope.launch(Dispatchers.IO) { vm.fetchAllData(mediaItem, ctx) } }

        val data = vm.metaData
        var showDescBox by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableIntStateOf(0) }
        
        var activeEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
        var showEpisodeInfo by remember { mutableStateOf(false) }
        
        val si = vm.seasonInfo
        var mainIdx by remember(si) { mutableIntStateOf(0) }
        var subIdx by remember(si, mainIdx) { mutableIntStateOf(0) }
        var rangeIdx by remember(vm.currentEpisodes) { mutableIntStateOf(0) }
        var activeSheet by remember { mutableStateOf(SheetType.NONE) }

        var showDownloadBox by remember { mutableStateOf(false) }
        var dlEpisode by remember { mutableStateOf<EpisodeItem?>(null) }
        var showSnackbar by remember { mutableStateOf(false) }

        val insets = WindowInsets.statusBars.asPaddingValues()
        val statusBarHeight = insets.calculateTopPadding()
        val config = LocalConfiguration.current
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val playerHeight = (config.screenWidthDp.dp * (9f / 16f))
        val totalTopPadding = playerHeight + statusBarHeight
        val descBg = if (vm.dominantColor == Color(0xFF262830)) Color(0xFF1D1D1D) else Color(vm.dominantColor.red * 0.15f, vm.dominantColor.green * 0.15f, vm.dominantColor.blue * 0.15f, 1f)

        val onEpisodeSelect: (EpisodeItem) -> Unit = { ep ->
            if (activeEpisode?.id != ep.id) {
                scope.launch { activeEpisode = null; vm.selectedEpisode = null; delay(150); activeEpisode = ep; vm.selectedEpisode = ep }
            }
        }
        val onDownloadClick: (EpisodeItem) -> Unit = { ep -> dlEpisode = ep; showDownloadBox = true; vm.fetchStreamsForDownload(ep.id) }
        val onLongPress: (EpisodeItem) -> Unit = { ep -> activeEpisode = ep; showEpisodeInfo = true }

        LaunchedEffect(isLandscape) {
            val wic = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (isLandscape) {
                wic.hide(WindowInsetsCompat.Type.systemBars())
                wic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activity.window.attributes = activity.window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
            } else {
                wic.show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activity.window.attributes = activity.window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT }
            }
        }

        BackHandler(enabled = vm.selectedEpisode != null || showDescBox || showDownloadBox || activeSheet != SheetType.NONE || showEpisodeInfo) {
            when {
                showDownloadBox -> showDownloadBox = false
                showDescBox -> showDescBox = false
                showEpisodeInfo -> showEpisodeInfo = false
                activeSheet != SheetType.NONE -> activeSheet = SheetType.NONE
                else -> { vm.selectedEpisode = null; activeEpisode = null }
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(AppDarkBg)) {
            if (!isLandscape) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(top = totalTopPadding)) {
                    if (vm.isLoadingMeta && data == null) item { MetaSkeleton() }
                    else if (data != null) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(mediaItem.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().clickable { showDescBox = true }, verticalAlignment = Alignment.Bottom) {
                                    Text(data.basic.plot, color = Color(0xDDFFFFFF), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(painterResource(id = R.drawable.ic_arrow_down), null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        stickyHeader {
                            Column(modifier = Modifier.fillMaxWidth().background(AppDarkBg)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Box(modifier = Modifier.weight(1f).clickable { selectedTab = 0 }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("Season", color = if (selectedTab == 0) Color.White else Color.Gray, fontWeight = FontWeight.Bold) }
                                    Box(modifier = Modifier.weight(1f).clickable { selectedTab = 1 }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) { Text("More Like This", color = if (selectedTab == 1) Color.White else Color.Gray, fontWeight = FontWeight.Bold) }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x33FFFFFF)))
                            }
                        }

                        if (selectedTab == 0) {
                            item { SeasonsSection(vm, mediaItem.img, onEpisodeSelect, onDownloadClick, onLongPress, mainIdx, subIdx, rangeIdx) { activeSheet = it } }
                        } else {
                            item {
                                Column(modifier = Modifier.padding(top = 16.dp, bottom = 40.dp, start = 16.dp, end = 16.dp)) {
                                    data.recommend.chunked(3).forEach { rowItems ->
                                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            rowItems.forEach { recItem -> Box(modifier = Modifier.weight(1f)) { MediaPosterCard(recItem, Modifier.fillMaxWidth()) { onMediaClick(recItem) } } }
                                            repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showDescBox && data != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.fillMaxSize().padding(top = totalTopPadding)) {
                    DescriptionSheet(data!!, mediaItem, descBg) { showDescBox = false }
                }

                AnimatedVisibility(visible = showEpisodeInfo && activeEpisode != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.fillMaxSize().padding(top = totalTopPadding)) {
                    EpisodeInfoSheet(activeEpisode!!, mediaItem, mediaItem.img, descBg) { showEpisodeInfo = false }
                }

                AnimatedVisibility(visible = activeSheet != SheetType.NONE, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.fillMaxSize().padding(top = totalTopPadding)) {
                    val options = mutableListOf<SheetOpt>()
                    val sheetTitle = when (activeSheet) {
                        SheetType.SEASON -> "Seasons"
                        SheetType.SUB_SEASON -> "Sub Season"
                        SheetType.RANGE -> "Range"
                        else -> ""
                    }
                    when (activeSheet) {
                        SheetType.SEASON -> si?.seasons?.forEachIndexed { i, s -> options.add(SheetOpt(s.name, i == mainIdx) { mainIdx = i; subIdx = 0; rangeIdx = 0; activeSheet = SheetType.NONE; s.parts.firstOrNull()?.let { vm.fetchEpisodesForSeasonPart(it.id, false) } }) }
                        SheetType.SUB_SEASON -> si?.seasons?.getOrNull(mainIdx)?.parts?.forEachIndexed { i, s -> options.add(SheetOpt(s.name, i == subIdx) { subIdx = i; rangeIdx = 0; activeSheet = SheetType.NONE; vm.fetchEpisodesForSeasonPart(s.id, true) }) }
                        SheetType.RANGE -> {
                            val eps = vm.currentEpisodes ?: si?.initialEpisodes ?: emptyList()
                            eps.chunked(50).indices.forEach { i -> options.add(SheetOpt("${(i * 50) + 1} - ${min((i + 1) * 50, eps.size)}", i == rangeIdx) { rangeIdx = i; activeSheet = SheetType.NONE }) }
                        }
                        else -> {}
                    }
                    SelectionSheet(options, sheetTitle, descBg) { activeSheet = SheetType.NONE }
                }

                AnimatedVisibility(visible = showDownloadBox && dlEpisode != null, enter = slideInVertically { it }, exit = slideOutVertically { it }, modifier = Modifier.fillMaxSize().padding(top = totalTopPadding)) {
                    DownloadSheet(vm, ctx, mediaItem, dlEpisode!!, descBg, onClose = { showDownloadBox = false }, onDownloadStarted = { showSnackbar = true })
                }
                
                // Bottom Snackbar for Downloads
                AnimatedVisibility(
                    visible = showSnackbar, 
                    enter = slideInVertically(initialOffsetY = { it }), 
                    exit = slideOutVertically(targetOffsetY = { it }),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).navigationBarsPadding()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2A2A)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Download started...", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Text("SEE ALL", color = Color(0xFFBEF3D9), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                                showSnackbar = false
                                AppGlobalState.activeListTab = 1
                                onNavigateToDownloads()
                            }.padding(4.dp))
                        }
                    }
                    LaunchedEffect(showSnackbar) {
                        delay(4000)
                        showSnackbar = false
                    }
                }
            }

            Box(modifier = if (isLandscape) Modifier.fillMaxSize().background(Color.Black) else Modifier.fillMaxWidth().height(totalTopPadding).align(Alignment.TopCenter).background(Color.Black)) {
                val imgTarget = if (!vm.selectedEpisode?.img.isNullOrBlank()) vm.selectedEpisode!!.img else if (!data?.basic?.bg.isNullOrBlank()) data!!.basic.bg else mediaItem.img
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(imgTarget).crossfade(false).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(AppDarkBg.copy(alpha = 0.9f), Color.Transparent))))
                Box(modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, AppDarkBg))))
                if (!isLandscape) Box(modifier = Modifier.statusBarsPadding()) { Icon(painterResource(id = R.drawable.ic_arrow_down), "Back", tint = Color.White, modifier = Modifier.padding(12.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).clickable { onBack() }.padding(8.dp)) }
            }
        }
    }
}
