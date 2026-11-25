package com.xtsream.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream3.CommonActivity
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.ui.components.AppBottomBar
import com.xtsream.app.ui.components.AppToastHost
import com.xtsream.app.ui.components.CloudflareVerifyDialog
import com.xtsream.app.ui.components.MainTab
import com.xtsream.app.ui.screens.HomeScreen
import com.xtsream.app.ui.screens.MetaScreen
import com.xtsream.app.ui.screens.ProvidersScreen
import com.xtsream.app.ui.screens.SearchScreen
import com.xtsream.app.ui.screens.SeeAllScreen
import com.xtsream.app.ui.screens.SettingsScreen
import com.xtsream.app.ui.screens.WatchlistScreen
import com.xtsream.app.ui.theme.AppColors
import com.xtsream.app.ui.theme.MovieAppTheme
import com.xtsream.app.viewmodel.HomeViewModel
import com.xtsream.app.viewmodel.SearchViewModel
import com.xtsream.app.viewmodel.SeeAllSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

private sealed class AppOverlay(val id: Long = System.nanoTime()) {
    val isClosing = mutableStateOf(false)

    class SeeAll(val source: SeeAllSource) : AppOverlay()
    class Meta(val item: MediaCardItem, val originBounds: Rect? = null) : AppOverlay()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        runCatching { CommonActivity.setActivityInstance(this) }
        runCatching { WatchlistStore.init(applicationContext) }

        setContent {
            MovieAppTheme {
                LaunchedEffect(Unit) {
                    runCatching { ExtensionController.initialize(applicationContext) }
                }
                MovieAppRoot()
            }
        }
    }

    override fun onDestroy() {
        if (CommonActivity.activity === this) {
            CommonActivity.setActivityInstance(null)
        }
        super.onDestroy()
    }
}

@Composable
private fun MovieAppRoot() {
    val homeViewModel: HomeViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()

    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var homeBarsVisible by remember { mutableStateOf(true) }
    val overlayStack = remember { mutableStateListOf<AppOverlay>() }

    LaunchedEffect(selectedTab) {
        if (selectedTab != MainTab.Home) {
            homeBarsVisible = true
        }
    }

    fun openMeta(item: MediaCardItem, originBounds: Rect? = null) {
        overlayStack.add(AppOverlay.Meta(item, originBounds))
    }

    fun openProvidersTab() {
        overlayStack.clear()
        selectedTab = MainTab.Providers
    }

    fun openSeeAll(source: SeeAllSource) {
        overlayStack.add(AppOverlay.SeeAll(source))
    }

    fun requestCloseTopOverlay() {
        val top = overlayStack.lastOrNull() ?: return
        if (!top.isClosing.value) {
            top.isClosing.value = true
        }
    }

    fun removeOverlay(overlay: AppOverlay) {
        overlayStack.remove(overlay)
    }

    BackHandler(enabled = overlayStack.isEmpty() && selectedTab != MainTab.Home) {
        selectedTab = MainTab.Home
    }

    // Real Android Predictive Back Gesture state for the top active overlay
    val backProgress = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = overlayStack.isNotEmpty()) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { backEvent ->
                backSwipeEdge = backEvent.swipeEdge
                backProgress.snapTo(backEvent.progress.coerceIn(0f, 1f))
            }
            // Trigger smooth shrink-back / exit animation on top overlay; underlying sub-pages stay completely still!
            requestCloseTopOverlay()
        } catch (_: CancellationException) {
            backProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        val rootWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val rootHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Main Tabs Layer — Home uses overlaid auto-hiding BottomBar; other tabs dock cleanly above BottomBar
        if (selectedTab == MainTab.Home) {
            Box(modifier = Modifier.fillMaxSize()) {
                HomeScreen(
                    homeViewModel = homeViewModel,
                    barsVisible = homeBarsVisible,
                    onBarsVisibilityChange = { homeBarsVisible = it },
                    onMediaClick = ::openMeta,
                    onSeeAllSection = { section ->
                        val provider = ExtensionController.activeProvider.value ?: return@HomeScreen
                        val mpData = section.mainPageData ?: return@HomeScreen
                        openSeeAll(
                            SeeAllSource.HomeCategory(
                                apiKey = provider.key,
                                apiName = provider.api.name,
                                displayName = provider.displayName,
                                title = section.title,
                                mainPageData = mpData,
                                initialItems = section.items,
                                hasNext = section.hasNext
                            )
                        )
                    },
                    onOpenProviders = ::openProvidersTab,
                    onSearchActiveProvider = { entry ->
                        searchViewModel.focusProviderScope(entry.key)
                        selectedTab = MainTab.Search
                    }
                )

                AnimatedVisibility(
                    visible = homeBarsVisible,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)) + fadeIn(tween(180)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)) + fadeOut(tween(160)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    AppBottomBar(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it }
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        MainTab.Search -> SearchScreen(
                            searchViewModel = searchViewModel,
                            onMediaClick = ::openMeta,
                            onSeeAllGroup = { group, query ->
                                openSeeAll(
                                    SeeAllSource.SearchProvider(
                                        apiKey = group.providerKey,
                                        apiName = group.apiName,
                                        displayName = group.displayName,
                                        query = query,
                                        initialItems = group.items,
                                        hasNext = group.hasMore
                                    )
                                )
                            },
                            onOpenProviders = ::openProvidersTab
                        )

                        MainTab.Library -> WatchlistScreen(
                            onMediaClick = ::openMeta
                        )

                        MainTab.Providers -> ProvidersScreen(onBack = null)

                        MainTab.Settings -> SettingsScreen()

                        MainTab.Home -> Unit
                    }
                }
                AppBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
        }

        // Single stable keyed loop for ALL active overlays:
        // Previously opened sub-pages stay at progress=1f in their EXACT composable slot,
        // so popping a child sub-page NEVER replays the parent sub-page's open animation!
        overlayStack.forEachIndexed { index, overlay ->
            val isTop = index == overlayStack.lastIndex
            key(overlay.id) {
                OverlayAnimatedLayer(
                    overlay = overlay,
                    isTop = isTop,
                    rootWidthPx = rootWidthPx,
                    rootHeightPx = rootHeightPx,
                    predictiveBackProgress = if (isTop) backProgress.value else 0f,
                    backSwipeEdge = backSwipeEdge,
                    onResetBackProgress = { backProgress.snapTo(0f) },
                    onRequestClose = { requestCloseTopOverlay() },
                    onRemoved = { removeOverlay(overlay) },
                    onMediaClick = ::openMeta
                )
            }
        }

        // Two-stage Cloudflare verification (Confirmation Popup -> Floating Resizable/Draggable Mini-Window)
        CloudflareVerifyDialog()

        // Global Top Overlay Toast Notification Popup
        AppToastHost(
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun OverlayAnimatedLayer(
    overlay: AppOverlay,
    isTop: Boolean,
    rootWidthPx: Float,
    rootHeightPx: Float,
    predictiveBackProgress: Float,
    backSwipeEdge: Int,
    onResetBackProgress: suspend () -> Unit,
    onRequestClose: () -> Unit,
    onRemoved: () -> Unit,
    onMediaClick: (MediaCardItem, Rect?) -> Unit
) {
    val density = LocalDensity.current
    val animProgress = remember(overlay.id) { Animatable(0f) }
    val closing by overlay.isClosing
    val originBounds = (overlay as? AppOverlay.Meta)?.originBounds

    // Open animation: runs ONCE per overlay instance when pushed
    LaunchedEffect(overlay.id) {
        onResetBackProgress()
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (originBounds != null) 310 else 250,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Close animation: smoothly shrinks back into origin card (or reverses slide) and removes only this overlay
    LaunchedEffect(closing) {
        if (closing) {
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = if (originBounds != null) 260 else 210,
                    easing = FastOutSlowInEasing
                )
            )
            onResetBackProgress()
            onRemoved()
        }
    }

    val p = animProgress.value
    val bp = if (isTop && !closing) predictiveBackProgress else 0f

    // Dim scrim behind this overlay (only visible for the top overlay to avoid stacking dark scrims)
    if (isTop) {
        val scrimAlpha = (0.50f * p * (1f - bp * 0.55f)).coerceIn(0f, 0.50f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor.Black.copy(alpha = scrimAlpha))
        )
    }

    val hasValidCardOrigin = originBounds != null &&
        originBounds.width > 16f &&
        originBounds.height > 16f &&
        originBounds.right > 0f &&
        originBounds.bottom > 0f &&
        originBounds.left < rootWidthPx &&
        originBounds.top < rootHeightPx

    val effectiveProgress = (p * (1f - bp * 0.35f)).coerceIn(0f, 1f)

    val cornerRadius = if (hasValidCardOrigin) {
        val cardCorner = 14f
        if (bp > 0f) (24f * bp).dp else (cardCorner * (1f - effectiveProgress)).dp
    } else {
        if (bp > 0f) (28f * bp).dp else (22f * (1f - p)).dp
    }
    val shape = RoundedCornerShape(cornerRadius)

    val layerModifier = if (hasValidCardOrigin && originBounds != null) {
        val startScaleX = (originBounds.width / rootWidthPx).coerceIn(0.08f, 1f)
        val startScaleY = (originBounds.height / rootHeightPx).coerceIn(0.08f, 1f)
        val curScaleX = startScaleX + (1f - startScaleX) * effectiveProgress
        val curScaleY = startScaleY + (1f - startScaleY) * effectiveProgress
        val baseTx = originBounds.left * (1f - effectiveProgress)
        val baseTy = originBounds.top * (1f - effectiveProgress)
        val swipeShiftX = with(density) {
            val shift = (28.dp * bp).toPx()
            if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -shift else shift
        }

        Modifier
            .fillMaxSize()
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = curScaleX
                scaleY = curScaleY
                translationX = baseTx + swipeShiftX
                translationY = baseTy
                alpha = (0.35f + 0.65f * (p * 1.6f).coerceIn(0f, 1f)).coerceIn(0f, 1f)
            }
    } else {
        val enterScale = 0.94f + (0.06f * p)
        val enterTranslateY = with(density) { (36.dp * (1f - p)).toPx() }
        val backScale = 1f - (0.14f * bp)
        val backTranslateX = with(density) {
            val shift = (44.dp * bp).toPx()
            if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -shift else shift
        }
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = enterScale * backScale
                scaleY = enterScale * backScale
                translationX = backTranslateX
                translationY = enterTranslateY
                alpha = (0.35f + 0.65f * p).coerceIn(0f, 1f)
            }
    }

    Box(
        modifier = layerModifier
            .shadow(
                elevation = if (bp > 0f || p < 0.99f) 22.dp else 0.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(AppColors.Background)
    ) {
        RenderOverlayScreen(
            overlay = overlay,
            onBack = onRequestClose,
            onMediaClick = onMediaClick
        )
    }
}

@Composable
private fun RenderOverlayScreen(
    overlay: AppOverlay,
    onBack: () -> Unit,
    onMediaClick: (MediaCardItem, Rect?) -> Unit
) {
    when (overlay) {
        is AppOverlay.SeeAll -> SeeAllScreen(
            source = overlay.source,
            onBack = onBack,
            onMediaClick = onMediaClick
        )
        is AppOverlay.Meta -> MetaScreen(
            item = overlay.item,
            onBack = onBack,
            onMediaClick = onMediaClick
        )
    }
}
