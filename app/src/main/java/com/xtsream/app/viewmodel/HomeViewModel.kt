package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.xtsream.app.data.HomeSection
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.toMediaCardItem
import com.xtsream.app.engine.ExtensionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class HomeUiState(
    val providerName: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val featuredItems: List<MediaCardItem> = emptyList(),
    val sections: List<HomeSection> = emptyList(),
    val errorMessage: String? = null
)

class HomeViewModel : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedProviderName: String? = null

    init {
        viewModelScope.launch {
            ExtensionController.activeApi.collect { api ->
                if (api == null) {
                    loadJob?.cancel()
                    loadedProviderName = null
                    _uiState.value = HomeUiState()
                } else if (api.name != loadedProviderName) {
                    loadHomePage(api, isRefresh = false)
                }
            }
        }
    }

    fun refresh() {
        val api = ExtensionController.activeApi.value ?: return
        loadHomePage(api, isRefresh = true)
    }

    fun retry() {
        val api = ExtensionController.activeApi.value ?: return
        loadHomePage(api, isRefresh = false)
    }

    private fun loadHomePage(api: MainAPI, isRefresh: Boolean) {
        loadJob?.cancel()
        loadedProviderName = api.name

        _uiState.update {
            it.copy(
                providerName = api.name,
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                errorMessage = null,
                featuredItems = if (isRefresh) it.featuredItems else emptyList(),
                sections = if (isRefresh) it.sections else emptyList()
            )
        }

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!api.hasMainPage) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "${api.name} does not expose a home feed. Try searching instead."
                        )
                    }
                    return@launch
                }

                val pages: List<MainPageData> = api.mainPage.ifEmpty {
                    listOf(MainPageData(name = "Featured", data = "", horizontalImages = false))
                }

                val fetchedSections = mutableListOf<HomeSection>()
                var lastError: String? = null

                // Fetch pages in parallel batches of 4 so home loads fast while avoiding overwhelming sites
                val batches = pages.chunked(4)
                for (batch in batches) {
                    val batchResults = coroutineScope {
                        batch.map { pageData ->
                            async {
                                try {
                                    val req = MainPageRequest(
                                        name = pageData.name,
                                        data = pageData.data,
                                        horizontalImages = pageData.horizontalImages
                                    )
                                    val response = withTimeoutOrNull(30_000L) {
                                        api.getMainPage(1, req)
                                    }
                                    if (response != null) {
                                        response.items.mapNotNull { homeList ->
                                            val items = homeList.list.map {
                                                it.toMediaCardItem(isHorizontal = homeList.isHorizontalImages || pageData.horizontalImages)
                                            }
                                            if (items.isEmpty()) null else HomeSection(
                                                id = "${api.name}_${pageData.name}_${homeList.name}",
                                                title = homeList.name.ifBlank { pageData.name.ifBlank { "Featured" } },
                                                items = items,
                                                isHorizontal = homeList.isHorizontalImages || pageData.horizontalImages,
                                                mainPageData = pageData,
                                                hasNext = response.hasNext
                                            )
                                        }
                                    } else {
                                        emptyList()
                                    }
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Page load failed for ${api.name}/${pageData.name}", t)
                                    lastError = t.message
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()
                    }

                    if (batchResults.isNotEmpty()) {
                        fetchedSections.addAll(batchResults)
                        val deduped = fetchedSections.distinctBy { it.title }
                        val featured = deduped.firstOrNull()?.items?.take(7).orEmpty()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                featuredItems = featured,
                                sections = deduped,
                                errorMessage = null
                            )
                        }
                    }
                }

                val finalSections = fetchedSections.distinctBy { it.title }
                val finalFeatured = finalSections.firstOrNull()?.items?.take(7).orEmpty()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        featuredItems = finalFeatured,
                        sections = finalSections,
                        errorMessage = if (finalSections.isEmpty()) {
                            lastError ?: "No titles returned by ${api.name}. Pull to refresh or switch providers."
                        } else null
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Home load error for ${api.name}", t)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = t.message ?: "Failed to load ${api.name}"
                    )
                }
            }
        }
    }
}
