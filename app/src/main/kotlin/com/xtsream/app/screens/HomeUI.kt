package com.xtsream.app.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.xtsream.app.AppDarkBg
import com.xtsream.app.data.HomeData
import com.xtsream.app.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun Modifier.skeletonEffect(): Modifier = this.background(Color(0xFF323540))

@Composable
fun CustomLoader(modifier: Modifier = Modifier) {
    var rotation by remember { mutableFloatStateOf(0f) }
    var currentPath by remember { mutableStateOf(Path()) }

    LaunchedEffect(Unit) {
        val shapes = listOf(
            Pair(7f, 3f), Pair(4f, 5f), Pair(2f, -6f),
            Pair(6f, 4f), Pair(3f, -5f), Pair(4f, -5f)
        )
        val pauseTime = 1500L
        val morphTime = 250L
        val durationPerShape = pauseTime + morphTime
        var lastTime = withFrameMillis { it }

        while (true) {
            val now = withFrameMillis { it }
            val delta = now - lastTime
            lastTime = now

            val cyclePhase = now % (durationPerShape * shapes.size)
            val currentIndex = (cyclePhase / durationPerShape).toInt()
            val nextIndex = (currentIndex + 1) % shapes.size
            val timeInCurrentCycle = cyclePhase % durationPerShape

            var ease = 0f
            var spinSpeed = 0.30f

            if (timeInCurrentCycle > pauseTime) {
                val morphProgress = (timeInCurrentCycle - pauseTime).toFloat() / morphTime
                ease = 0.5f - (cos(morphProgress * PI.toFloat()) / 2f)
                val boost = sin(morphProgress * PI.toFloat())
                spinSpeed += boost * 1.0f
            }

            rotation += spinSpeed * delta

            val conf1 = shapes[currentIndex]
            val conf2 = shapes[nextIndex]
            val path = Path()

            for (i in 0..360 step 3) {
                val rad = i * (PI.toFloat() / 180f)
                val r1 = 26f + (conf1.second * cos(conf1.first * rad))
                val r2 = 26f + (conf2.second * cos(conf2.first * rad))
                val r = (r1 * (1 - ease) + r2 * ease)

                val x = 50f + r * cos(rad)
                val y = 50f + r * sin(rad)

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            currentPath = path
        }
    }

    Canvas(modifier = modifier.size(50.dp).graphicsLayer { rotationZ = rotation }) {
        val scaleX = size.width / 100f
        val scaleY = size.height / 100f
        val scaledPath = Path().apply {
            addPath(currentPath)
            transform(Matrix().apply { scale(scaleX, scaleY) })
        }
        drawPath(path = scaledPath, color = Color(0xFFA8C7FA), style = Fill)
    }
}

@Composable
fun SwipeToBackBox(onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(AppDarkBg)) {
        content()
    }
}

@Composable
fun HomeUI(
    homeData: HomeData, 
    onSeeAllClick: (String, String) -> Unit, 
    onMediaClick: (MediaItem) -> Unit,
    onSearchClick: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(homeData.categoriesList) {
        if (homeData.selectedCategory.isEmpty() && homeData.categoriesList.isNotEmpty()) {
            scope.launch(Dispatchers.IO) { homeData.selectCategory(homeData.categoriesList.first(), context) }
        }
    }

    val isNearBottom by remember {
        derivedStateOf {
            val maxScroll = scrollState.maxValue
            maxScroll > 0 && scrollState.value >= (maxScroll - 600)
        }
    }

    LaunchedEffect(isNearBottom) {
        if (isNearBottom && !homeData.isPaginationLoading && !homeData.allSubcategoriesLoaded) {
            homeData.loadNextSubcategoryBatch(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppDarkBg)) {
        Column(modifier = Modifier.fillMaxSize().background(AppDarkBg).verticalScroll(scrollState).padding(bottom = 100.dp)) {
            
            if (homeData.isInitialBatchLoading || !homeData.catalogLoaded) {
                Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(AppDarkBg), contentAlignment = Alignment.Center) {
                    CustomLoader()
                }
            } else {
                if (!homeData.sliderItems.isNullOrEmpty()) {
                    HeroSlider(sliderItems = homeData.sliderItems, extractedColors = homeData.extractedColors, onMediaClick = onMediaClick)
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Spacer(modifier = Modifier.statusBarsPadding().height(70.dp))
                }

                homeData.posterSections.forEach { section ->
                    Column(modifier = Modifier.fillMaxWidth().background(AppDarkBg)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = section.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(text = "See all", color = Color(0xFFBEF3D9), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { 
                                homeData.cancelAllOperations()
                                onSeeAllClick(section.id, section.name) 
                            }.padding(4.dp))
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 15.dp)) {
                            val rowItems = section.items
                            val count = if (rowItems.isNullOrEmpty()) 5 else rowItems.size + 1
                            items(count) { i ->
                                if (rowItems != null && i == rowItems.size) {
                                    if (rowItems.isNotEmpty()) {
                                        Box(modifier = Modifier.width(110.dp).clickable { 
                                            homeData.cancelAllOperations()
                                            onSeeAllClick(section.id, section.name) 
                                        }) {
                                            MediaPosterCard(rowItems[0], Modifier.fillMaxWidth(), onClick = { 
                                                homeData.cancelAllOperations()
                                                onSeeAllClick(section.id, section.name) 
                                            })
                                            Box(modifier = Modifier.matchParentSize().background(Color(0xE6101114)), contentAlignment = Alignment.Center) {
                                                Text("more ➔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    val item = rowItems?.getOrNull(i)
                                    MediaPosterCard(item, Modifier.width(110.dp), onClick = { 
                                        if (item != null) {
                                            homeData.cancelAllOperations()
                                            onMediaClick(item)
                                        }
                                    })
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (!homeData.allSubcategoriesLoaded || homeData.isPaginationLoading) {
                    Box(modifier = Modifier.fillMaxWidth().background(AppDarkBg).padding(24.dp), contentAlignment = Alignment.Center) {
                        CustomLoader()
                    }
                }
            }
        }

        val topBarAlpha = (scrollState.value / 250f).coerceIn(0f, 1f)
        TopBar(
            categories = homeData.categoriesList,
            selectedCategory = homeData.selectedCategory,
            onCategorySelected = { cat -> 
                scope.launch(Dispatchers.IO) { homeData.selectCategory(cat, context) } 
            },
            onSearchClick = {
                homeData.cancelAllOperations()
                onSearchClick()
            },
            modifier = Modifier.align(Alignment.TopCenter).background(AppDarkBg.copy(alpha = topBarAlpha))
        )
    }
}

@Composable
fun TopBar(
    categories: List<String>, 
    selectedCategory: String, 
    onCategorySelected: (String) -> Unit, 
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().statusBarsPadding().padding(top = 0.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(30.dp).background(Color.White, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("S", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(Color(0x1FFFFFFF), RoundedCornerShape(5.dp))
                    .clickable { onSearchClick() }
                    .padding(horizontal = 12.dp), 
                contentAlignment = Alignment.CenterStart
            ) {
                Text("Search movies, shows...", color = Color(0x61FFFFFF), fontSize = 13.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(categories) { category ->
                val isSelected = selectedCategory == category
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    modifier = Modifier.clickable { onCategorySelected(category) }.padding(vertical = 4.dp)
                ) {
                    Text(text = category, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isSelected) Color.White else Color(0xFFC0C6CD))
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.width(20.dp).height(2.dp).background(if (isSelected) Color.White else Color.Transparent))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroSlider(sliderItems: List<MediaItem>?, extractedColors: List<Color>, onMediaClick: (MediaItem) -> Unit) {
    val actualSlideCount = if (sliderItems.isNullOrEmpty()) 4 else sliderItems.size
    val virtualSlideCount = 40000
    val initialPage = (virtualSlideCount / 2) - ((virtualSlideCount / 2) % actualSlideCount)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { virtualSlideCount })

    LaunchedEffect(pagerState, sliderItems) {
        if (!sliderItems.isNullOrEmpty()) {
            while (true) {
                delay(3000)
                pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(600))
            }
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().wrapContentHeight().background(AppDarkBg)) { virtualPage ->
        val actualPage = if (actualSlideCount > 0) virtualPage % actualSlideCount else 0
        val pickedColor = if (extractedColors.size > actualPage) extractedColors[actualPage] else AppDarkBg
        val imgUrl = sliderItems?.getOrNull(actualPage)?.img ?: ""
        val titleText = sliderItems?.getOrNull(actualPage)?.title ?: ""
        var isLoaded by remember { mutableStateOf(false) }
        val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(imgUrl).crossfade(true).build(), onState = { state -> isLoaded = state is AsyncImagePainter.State.Success })

        Column(modifier = Modifier.fillMaxWidth().background(AppDarkBg)) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(pickedColor))

            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable { sliderItems?.getOrNull(actualPage)?.let { onMediaClick(it) } }) {
                if (sliderItems != null) {
                    Image(painter = painter, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (!isLoaded) Modifier.skeletonEffect() else Modifier))
                } else {
                    Box(modifier = Modifier.fillMaxSize().skeletonEffect())
                }
                
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.20f).align(Alignment.TopCenter).background(Brush.verticalGradient(colors = listOf(pickedColor, Color.Transparent))))
                
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.30f).align(Alignment.BottomCenter).background(Brush.verticalGradient(colors = listOf(Color.Transparent, AppDarkBg))))

                if (sliderItems != null && titleText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 14.dp, bottom = 10.dp)
                            .background(Color(0xB3101114), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = titleText, 
                            color = Color.White, 
                            fontSize = 15.sp, 
                            fontWeight = FontWeight.Bold, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis, 
                            modifier = Modifier.widthIn(max = 260.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MediaPosterCard(item: MediaItem?, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    var isLoaded by remember { mutableStateOf(false) }
    val painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(item?.img ?: "").crossfade(true).build(), onState = { state -> isLoaded = state is AsyncImagePainter.State.Success })
    Column(modifier = modifier.clickable(enabled = item != null) { onClick() }) {
        Card(shape = RoundedCornerShape(3.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF323540)), modifier = Modifier.fillMaxWidth().aspectRatio(230f / 325f)) {
            if (item != null) {
                Image(painter = painter, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().then(if (!isLoaded) Modifier.skeletonEffect() else Modifier))
            } else {
                Box(modifier = Modifier.fillMaxSize().skeletonEffect())
            }
        }
        if (item != null && item.title.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = item.title, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        } else if (item == null) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(14.dp).skeletonEffect())
        }
    }
}

@Composable
fun HomeFakeSkeleton() {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp).background(AppDarkBg), contentAlignment = Alignment.Center) {
        CustomLoader()
    }
}
