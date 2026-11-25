package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.xtsream.app.data.HomeSection
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.toMediaCardItem
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.engine.ProviderEntry
import kotlinx.coroutines.CancellationException
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
import java.util.concurrent.atomic.AtomicLong

data class HomeUiState(
    val providerKey: String? = null,
    val providerDisplayName: String? = null,
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
    private var loadedProviderKey: String? = null
    private val requestCounter = AtomicLong(0L)

    init {
        viewModelScope.launch {
            ExtensionController.activeProvider.collect { entry ->
                if (entry == null) {
                    requestCounter.incrementAndGet()
                    loadJob?.cancel()
                    loadedProviderKey = null
                    _uiState.value = HomeUiState()
                } else if (entry.key != loadedProviderKey) {
                    loadHomePage(entry, isRefresh = false)
                } else if (_uiState.value.providerDisplayName != entry.displayName) {
                    _uiState.update { it.copy(providerDisplayName = entry.displayName) }
                }
            }
        }
    }

    fun refresh() {
        val entry = ExtensionController.activeProvider.value ?: return
        loadHomePage(entry, isRefresh = true)
    }

    fun retry() {
        val entry = ExtensionController.activeProvider.value ?: return
        loadHomePage(entry, isRefresh = false)
    }

    private fun loadHomePage(entry: ProviderEntry, isRefresh: Boolean) {
        val api = entry.api
        val myRequestId = requestCounter.incrementAndGet()
        loadJob?.cancel()
        loadedProviderKey = entry.key

        _uiState.update {
            it.copy(
                providerKey = entry.key,
                providerDisplayName = entry.displayName,
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
                    if (requestCounter.get() == myRequestId) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                errorMessage = "${entry.displayName} does not expose a home feed. Try searching instead."
                            )
                        }
                    }
                    return@launch
                }

                val pages: List<MainPageData> = api.mainPage.ifEmpty {
                    listOf(MainPageData(name = "Featured", data = "", horizontalImages = false))
                }

                val fetchedSections = mutableListOf<HomeSection>()
                var lastError: String? = null

                val batches = pages.chunked(3)
                for (batch in batches) {
                    if (requestCounter.get() != myRequestId) return@launch
                    val batchResults = coroutineScope {
                        batch.map { pageData ->
                            async {
                                try {
                                    val req = MainPageRequest(
                                        name = pageData.name,
                                        data = pageData.data,
                                        horizontalImages = pageData.horizontalImages
                                    )
                                    val response = withTimeoutOrNull(45_000L) {
                                        api.getMainPage(1, req)
                                    }
                                    if (response != null) {
                                        response.items.mapNotNull { homeList ->
                                            val items = homeList.list.map {
                                                it.toMediaCardItem(
                                                    isHorizontal = homeList.isHorizontalImages || pageData.horizontalImages,
                                                    apiKey = entry.key
                                                )
                                            }
                                            if (items.isEmpty()) null else HomeSection(
                                                id = "${entry.key}_${pageData.name}_${homeList.name}",
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
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Page load failed for ${entry.displayName}/${pageData.name}", t)
                                    lastError = formatFriendlyError(t, entry.displayName)
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()
                    }

                    if (requestCounter.get() != myRequestId) return@launch

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

                if (requestCounter.get() != myRequestId) return@launch

                val finalSections = fetchedSections.distinctBy { it.title }
                val finalFeatured = finalSections.firstOrNull()?.items?.take(7).orEmpty()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        featuredItems = finalFeatured,
                        sections = finalSections,
                        errorMessage = if (finalSections.isEmpty()) {
                            lastError ?: "No titles returned by ${entry.displayName}. Tap Retry or switch providers."
                        } else null
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Home load error for ${entry.displayName}", t)
                if (requestCounter.get() == myRequestId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = formatFriendlyError(t, entry.displayName)
                        )
                    }
                }
            }
        }
    }
}
