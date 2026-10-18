package com.xtsream.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xtsream.app.data.HomeData
import com.xtsream.app.data.MediaItem
import com.xtsream.app.manage.CacheManager
import com.xtsream.app.manage.ProviderManager
import com.xtsream.app.screens.*
import com.xtsream.app.screens.meta.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

val AppDarkBg = Color(0xFF101114)
val CategoryTextUnselected = Color(0x80FFFFFF)

object AppGlobalState {
    var activeListTab by mutableIntStateOf(0)
}

sealed class ScreenState {
    object Home : ScreenState()
    object GlobalSearch : ScreenState() 
    object ListPage : ScreenState()
    object Profile : ScreenState() 
    object Search : ScreenState() 
    data class SeeAll(val id: String, val title: String, val providerId: String) : ScreenState()
    data class GlobalSearchSeeAll(val query: String, val providerId: String, val providerName: String) : ScreenState()
    data class Meta(val item: MediaItem) : ScreenState()
}

class MainActivity : ComponentActivity() {
    private val homeData = HomeData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        checkAndRequestStoragePermission()
        
        DataFetch.init(this)
        ProviderManager.init(this)
        CacheManager.clearSession(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = File(filesDir, "MovieBox_CrashLogs.txt")
                crashFile.appendText("\n--- CRASH AT ${Date()} ---\n${throwable.stackTraceToString()}\n\n")
            } catch (e: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = AppDarkBg)) {
                MainAppUI(homeData)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) CacheManager.clearSession(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        CacheManager.clearSession(this)
    }

    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "Grant All Files Access for Local Muxing", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            val permissions = arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requestPermissions(permissions, 101)
        }
    }
}

@Composable
fun MainAppUI(homeData: HomeData) {
    val context = LocalContext.current
    
    var appState by remember { 
        mutableStateOf(
            if (ProviderManager.isListLoaded && ProviderManager.plugins.any { it.isInstalled }) "READY" else "SPLASH"
        )
    }

    if (appState == "SPLASH") {
        LaunchedEffect(Unit) {
            delay(2000)
            if (ProviderManager.isListLoaded && ProviderManager.plugins.any { it.isInstalled }) {
                appState = "READY"
            } else {
                appState = "SETUP"
            }
            
            launch(Dispatchers.IO) {
                ProviderManager.fetchPluginsList(context)
            }
        }
        SplashUI()
    } else if (appState == "SETUP") {
        SetupUI(onFinish = { appState = "READY" })
    } else {
        val screenStack = remember { mutableStateListOf<ScreenState>(ScreenState.Home) }
        val searchViewModel: SearchViewModel = viewModel()
        val stackSnapshot = screenStack.toList()
        val currentScreen = stackSnapshot.lastOrNull()

        LaunchedEffect(ProviderManager.currentProvider) {
            if (ProviderManager.currentProvider != null) {
                homeData.fetchCatalog()
            }
        }

        val isHomeActive = currentScreen is ScreenState.Home
        LaunchedEffect(isHomeActive) {
            if (isHomeActive) homeData.resumeCategory(context) else homeData.cancelAllOperations()
        }

        val navigateBack = {
            if (screenStack.size > 1) {
                val popped = screenStack.removeAt(screenStack.lastIndex) 
                if (popped is ScreenState.Search && screenStack.lastOrNull() is ScreenState.Home) {
                    searchViewModel.clearCache()
                }
            }
        }

        BackHandler(enabled = screenStack.size > 1) { navigateBack() }

        Box(modifier = Modifier.fillMaxSize().background(AppDarkBg)) {
            stackSnapshot.forEachIndexed { index, screen ->
                val isTopScreen = index == stackSnapshot.lastIndex
                Box(modifier = Modifier.fillMaxSize().then(if (isTopScreen) Modifier else Modifier.pointerInput(Unit) {})) {
                    when (screen) {
                        is ScreenState.Home -> HomeUI(
                            homeData = homeData,
                            onSeeAllClick = { id, title -> screenStack.add(ScreenState.SeeAll(id, title, ProviderManager.currentProvider?.id ?: "")) },
                            onMediaClick = { item -> screenStack.add(ScreenState.Meta(item)) },
                            onSearchClick = { screenStack.add(ScreenState.Search) }
                        )
                        is ScreenState.Search -> SearchUI(
                            onBack = { navigateBack() },
                            onMediaClick = { item -> screenStack.add(ScreenState.Meta(item)) }
                        )
                        is ScreenState.GlobalSearch -> HomeSearchUI(
                            onMediaClick = { item -> screenStack.add(ScreenState.Meta(item)) },
                            onSeeAllClick = { query, providerId, providerName -> screenStack.add(ScreenState.GlobalSearchSeeAll(query, providerId, providerName)) }
                        )
                        is ScreenState.GlobalSearchSeeAll -> GlobalSearchSeeAllUI(
                            query = screen.query, providerId = screen.providerId, providerName = screen.providerName,
                            onBack = { navigateBack() }, onMediaClick = { item -> screenStack.add(ScreenState.Meta(item)) }
                        )
                        is ScreenState.ListPage -> ListUI(
                            onProviderSelected = { provider -> 
                                ProviderManager.switchProvider(provider) {
                                    CacheManager.clearSession(context)
                                    homeData.cancelAllOperations()
                                    homeData.clearHomeState()
                                    screenStack.clear()
                                    screenStack.add(ScreenState.Home)
                                }
                            }
                        )
                        is ScreenState.Profile -> SettingsUI()
                        is ScreenState.SeeAll -> SeeAllScreen(
                            sectionId = screen.id, sectionTitle = screen.title, providerId = screen.providerId,
                            onBack = { navigateBack() }, homeData = homeData, onMediaClick = { item -> screenStack.add(ScreenState.Meta(item)) }
                        )
                        is ScreenState.Meta -> MetaUI(
                            mediaItem = screen.item, 
                            onBack = { navigateBack() }, 
                            onMediaClick = { newItem -> screenStack.add(ScreenState.Meta(newItem)) },
                            onNavigateToDownloads = {
                                AppGlobalState.activeListTab = 1
                                screenStack.clear()
                                screenStack.add(ScreenState.ListPage)
                            }
                        )
                    }
                }
            }

            if (currentScreen is ScreenState.Home || currentScreen is ScreenState.GlobalSearch || currentScreen is ScreenState.ListPage || currentScreen is ScreenState.Profile) {
                NavigationUI(
                    currentScreen = currentScreen,
                    onNavigate = { target -> if (currentScreen != target) { screenStack.clear(); screenStack.add(target) } },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
