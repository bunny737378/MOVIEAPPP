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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color as ComposeColor
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
    class Providers : AppOverlay()
    class SeeAll(val source: SeeAllSource) : AppOverlay()
    class Meta(val item: MediaCardItem) : AppOverlay()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        CommonActivity.setActivityInstance(this)
        WatchlistStore.init(applicationContext)

        setContent {
            MovieAppTheme {
                LaunchedEffect(Unit) {
                    ExtensionController.initialize(applicationContext)
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

    fun openMeta(item: MediaCardItem) {
        overlayStack.add(AppOverlay.Meta(item))
    }

    fun openProviders() {
        if (overlayStack.lastOrNull() !is AppOverlay.Providers) {
            overlayStack.add(AppOverlay.Providers())
        }
    }

    fun openSeeAll(source: SeeAllSource) {
        overlayStack.add(AppOverlay.SeeAll(source))
    }

    fun popOverlay() {
        if (overlayStack.isNotEmpty()) {
            overlayStack.removeAt(overlayStack.lastIndex)
        }
    }

    BackHandler(enabled = overlayStack.isEmpty() && selectedTab != MainTab.Home) {
        selectedTab = MainTab.Home
    }

    // Real Android Predictive Back Gesture state for sub-page overlays
    val backProgress = remember { Animatable(0f) }
    var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = overlayStack.isNotEmpty()) { progressFlow: Flow<BackEventCompat> ->
        try {
            progressFlow.collect { backEvent ->
                backSwipeEdge = backEvent.swipeEdge
                backProgress.snapTo(backEvent.progress.coerceIn(0f, 1f))
            }
            popOverlay()
            backProgress.snapTo(0f)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
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
                    onOpenProviders = ::openProviders,
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
                            onOpenProviders = ::openProviders
                        )

                        MainTab.Library -> WatchlistScreen(
                            onMediaClick = ::openMeta
                        )

                        MainTab.Settings -> SettingsScreen(
                            onOpenProviders = ::openProviders
                        )

                        MainTab.Home -> Unit
                    }
                }
                AppBottomBar(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }
        }

        // Render previous overlay (if depth >= 2) so Predictive Back reveals the immediate parent overlay
        val stackSize = overlayStack.size
        if (stackSize >= 2) {
            val previousOverlay = overlayStack[stackSize - 2]
            key(previousOverlay.id) {
                Box(modifier = Modifier.fillMaxSize()) {
                    RenderOverlayScreen(
                        overlay = previousOverlay,
                        onBack = ::popOverlay,
                        onMediaClick = ::openMeta
                    )
                }
            }
        }

        // Top active sub-page with soft dark shadow on the previous page underneath throughout open & back-swipe
        val topOverlay = overlayStack.lastOrNull()
        if (topOverlay != null) {
            val progress = backProgress.value
            val density = LocalDensity.current

            // Previous-page shadow scrim: stays softly dark while sub-page is open/swiped, disappears cleanly on close
            val scrimAlpha = (0.50f * (1f - progress * 0.55f)).coerceIn(0f, 0.50f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.Black.copy(alpha = scrimAlpha))
            )

            key(topOverlay.id) {
                val enterProgress = remember(topOverlay.id) { Animatable(0f) }
                LaunchedEffect(topOverlay.id) {
                    backProgress.snapTo(0f)
                    enterProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )
                }

                val e = enterProgress.value
                val enterScale = 0.94f + (0.06f * e)
                val enterTranslateY = with(density) { (36.dp * (1f - e)).toPx() }
                val enterCornerDp = (22f * (1f - e)).dp

                val backScale = 1f - (0.14f * progress)
                val backTranslateX = with(density) {
                    val shift = (44.dp * progress).toPx()
                    if (backSwipeEdge == BackEventCompat.EDGE_RIGHT) -shift else shift
                }
                val backCornerDp = (28f * progress).dp

                val combinedScale = enterScale * backScale
                val cornerRadius = if (progress > 0f) backCornerDp else enterCornerDp
                val shape = RoundedCornerShape(cornerRadius)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = combinedScale
                            scaleY = combinedScale
                            translationX = backTranslateX
                            translationY = enterTranslateY
                            alpha = (0.35f + 0.65f * e).coerceIn(0f, 1f)
                        }
                        .shadow(
                            elevation = if (progress > 0f || e < 1f) 22.dp else 0.dp,
                            shape = shape,
                            clip = false
                        )
                        .clip(shape)
                        .background(AppColors.Background)
                ) {
                    RenderOverlayScreen(
                        overlay = topOverlay,
                        onBack = ::popOverlay,
                        onMediaClick = ::openMeta
                    )
                }
            }
        }

        // Interactive Cloudflare Turnstile fallback (only appears if headless bypass encounters a hard CAPTCHA)
        CloudflareVerifyDialog()

        // Global Top Overlay Toast Notification Popup
        AppToastHost(
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun RenderOverlayScreen(
    overlay: AppOverlay,
    onBack: () -> Unit,
    onMediaClick: (MediaCardItem) -> Unit
) {
    when (overlay) {
        is AppOverlay.Providers -> ProvidersScreen(onBack = onBack)
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
