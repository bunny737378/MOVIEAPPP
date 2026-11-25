package com.xtsream.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lagradost.cloudstream3.CommonActivity
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.ui.components.AppBottomBar
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

private sealed interface AppOverlay {
    data object Providers : AppOverlay
    data class SeeAll(val source: SeeAllSource) : AppOverlay
    data class Meta(val item: MediaCardItem) : AppOverlay
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
    val overlayStack = remember { mutableStateListOf<AppOverlay>() }

    fun openMeta(item: MediaCardItem) {
        overlayStack.add(AppOverlay.Meta(item))
    }

    fun openProviders() {
        if (overlayStack.lastOrNull() != AppOverlay.Providers) {
            overlayStack.add(AppOverlay.Providers)
        }
    }

    fun openSeeAll(source: SeeAllSource) {
        overlayStack.add(AppOverlay.SeeAll(source))
    }

    BackHandler(enabled = overlayStack.isNotEmpty() || selectedTab != MainTab.Home) {
        if (overlayStack.isNotEmpty()) {
            overlayStack.removeAt(overlayStack.lastIndex)
        } else if (selectedTab != MainTab.Home) {
            selectedTab = MainTab.Home
        }
    }

    val topOverlay = overlayStack.lastOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        if (topOverlay == null) {
            Scaffold(
                containerColor = AppColors.Background,
                bottomBar = {
                    AppBottomBar(
                        selectedTab = selectedTab,
                        onSelectTab = { selectedTab = it }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    when (selectedTab) {
                        MainTab.Home -> HomeScreen(
                            homeViewModel = homeViewModel,
                            onMediaClick = ::openMeta,
                            onSeeAllSection = { section ->
                                val apiName = ExtensionController.activeApi.value?.name ?: return@HomeScreen
                                val mpData = section.mainPageData ?: return@HomeScreen
                                openSeeAll(
                                    SeeAllSource.HomeCategory(
                                        apiName = apiName,
                                        title = section.title,
                                        mainPageData = mpData,
                                        initialItems = section.items,
                                        hasNext = section.hasNext
                                    )
                                )
                            },
                            onOpenProviders = ::openProviders
                        )

                        MainTab.Search -> SearchScreen(
                            searchViewModel = searchViewModel,
                            onMediaClick = ::openMeta,
                            onSeeAllGroup = { group, query ->
                                openSeeAll(
                                    SeeAllSource.SearchProvider(
                                        apiName = group.apiName,
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
                    }
                }
            }
        } else {
            when (topOverlay) {
                is AppOverlay.Providers -> ProvidersScreen(
                    onBack = {
                        if (overlayStack.isNotEmpty()) overlayStack.removeAt(overlayStack.lastIndex)
                    }
                )

                is AppOverlay.SeeAll -> SeeAllScreen(
                    source = topOverlay.source,
                    onBack = {
                        if (overlayStack.isNotEmpty()) overlayStack.removeAt(overlayStack.lastIndex)
                    },
                    onMediaClick = ::openMeta
                )

                is AppOverlay.Meta -> MetaScreen(
                    item = topOverlay.item,
                    onBack = {
                        if (overlayStack.isNotEmpty()) overlayStack.removeAt(overlayStack.lastIndex)
                    },
                    onMediaClick = ::openMeta
                )
            }
        }
    }
}
