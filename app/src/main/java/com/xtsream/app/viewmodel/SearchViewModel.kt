package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.MainAPI
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.data.toMediaCardItem
import com.xtsream.app.engine.ExtensionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class ProviderSearchGroup(
    val apiName: String,
    val items: List<MediaCardItem>,
    val hasMore: Boolean = false
)

data class SearchUiState(
    val query: String = "",
    val selectedProviderFilter: String? = null, // null = All Installed Providers
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasSearched: Boolean = false,
    val isGridView: Boolean = true,
    val quickSuggestions: List<MediaCardItem> = emptyList(),
    val globalGroups: List<ProviderSearchGroup> = emptyList(),
    val singleProviderItems: List<MediaCardItem> = emptyList(),
    val nextPage: Int = 2,
    val hasMoreSingle: Boolean = false,
    val errorMessage: String? = null
)

class SearchViewModel : ViewModel() {
    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var quickJob: Job? = null
    private var loadMoreJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        if (newQuery.isBlank()) {
            quickJob?.cancel()
            _uiState.update {
                it.copy(
                    quickSuggestions = emptyList(),
                    hasSearched = false,
                    globalGroups = emptyList(),
                    singleProviderItems = emptyList(),
                    errorMessage = null
                )
            }
            return
        }

        // Quick search on active or filtered provider if supported
        val targetApi = resolveSingleTargetApi() ?: ExtensionController.activeApi.value
        if (targetApi != null && targetApi.hasQuickSearch) {
            quickJob?.cancel()
            quickJob = viewModelScope.launch(Dispatchers.IO) {
                delay(350)
                runCatching {
                    val res = withTimeoutOrNull(10_000L) { targetApi.quickSearch(newQuery.trim()) }
                    val mapped = res.orEmpty().take(8).map { it.toMediaCardItem() }
                    _uiState.update { it.copy(quickSuggestions = mapped) }
                }
            }
        }
    }

    fun selectProviderFilter(apiName: String?) {
        if (_uiState.value.selectedProviderFilter == apiName) return
        _uiState.update { it.copy(selectedProviderFilter = apiName) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.hasSearched) {
            executeSearch()
        }
    }

    fun toggleLayoutMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun clearQuery() {
        searchJob?.cancel()
        quickJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                isSearching = false,
                hasSearched = false,
                quickSuggestions = emptyList(),
                globalGroups = emptyList(),
                singleProviderItems = emptyList(),
                errorMessage = null
            )
        }
    }

    fun executeSearch(explicitQuery: String? = null) {
        val q = (explicitQuery ?: _uiState.value.query).trim()
        if (q.isBlank()) return

        WatchlistStore.addRecentSearch(q)
        quickJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()

        val apis = ExtensionController.loadedApis.value
        if (apis.isEmpty()) {
            _uiState.update {
                it.copy(
                    query = q,
                    hasSearched = true,
                    isSearching = false,
                    errorMessage = "No providers installed. Add a CloudStream repository in Settings → Providers."
                )
            }
            return
        }

        val singleApi = resolveSingleTargetApi()
        _uiState.update {
            it.copy(
                query = q,
                hasSearched = true,
                isSearching = true,
                quickSuggestions = emptyList(),
                globalGroups = emptyList(),
                singleProviderItems = emptyList(),
                nextPage = 2,
                hasMoreSingle = false,
                errorMessage = null
            )
        }

        if (singleApi != null) {
            searchSingleProvider(singleApi, q)
        } else {
            searchAllProviders(apis, q)
        }
    }

    private fun resolveSingleTargetApi(): MainAPI? {
        val filter = _uiState.value.selectedProviderFilter ?: return null
        return ExtensionController.getApiByName(filter)
    }

    private fun searchSingleProvider(api: MainAPI, query: String) {
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val respList = withTimeoutOrNull(25_000L) {
                    api.search(query, 1)
                }
                val items = respList?.items?.map { it.toMediaCardItem() }.orEmpty()
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        singleProviderItems = items,
                        nextPage = 2,
                        hasMoreSingle = respList?.hasNext == true && items.isNotEmpty(),
                        errorMessage = if (items.isEmpty()) "No matches in ${api.name} for \"$query\"." else null
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Search failed for ${api.name}", t)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = t.message ?: "Search failed on ${api.name}"
                    )
                }
            }
        }
    }

    fun loadMoreSingleProvider() {
        val state = _uiState.value
        val api = resolveSingleTargetApi() ?: return
        if (state.isLoadingMore || !state.hasMoreSingle || state.query.isBlank()) return

        _uiState.update { it.copy(isLoadingMore = true) }
        val pageToFetch = state.nextPage

        loadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val respList = withTimeoutOrNull(25_000L) {
                    api.search(state.query.trim(), pageToFetch)
                }
                val moreItems = respList?.items?.map { it.toMediaCardItem() }.orEmpty()
                _uiState.update { current ->
                    val merged = (current.singleProviderItems + moreItems).distinctBy { it.url }
                    current.copy(
                        isLoadingMore = false,
                        singleProviderItems = merged,
                        nextPage = pageToFetch + 1,
                        hasMoreSingle = respList?.hasNext == true && moreItems.isNotEmpty()
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Load more failed for ${api.name}", t)
                _uiState.update { it.copy(isLoadingMore = false, hasMoreSingle = false) }
            }
        }
    }

    private fun searchAllProviders(apis: List<MainAPI>, query: String) {
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val remaining = java.util.concurrent.atomic.AtomicInteger(apis.size)
            apis.forEach { api ->
                launch(Dispatchers.IO) {
                    try {
                        val respList = withTimeoutOrNull(22_000L) {
                            api.search(query, 1)
                        }
                        val items = respList?.items?.map { it.toMediaCardItem() }.orEmpty()
                        if (items.isNotEmpty()) {
                            _uiState.update { current ->
                                val updated = (current.globalGroups + ProviderSearchGroup(
                                    apiName = api.name,
                                    items = items,
                                    hasMore = respList?.hasNext == true
                                )).distinctBy { it.apiName }
                                current.copy(globalGroups = updated)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Global search error on ${api.name}", t)
                    } finally {
                        if (remaining.decrementAndGet() <= 0) {
                            _uiState.update { current ->
                                current.copy(
                                    isSearching = false,
                                    errorMessage = if (current.globalGroups.isEmpty()) {
                                        "No results found across ${apis.size} provider(s) for \"$query\"."
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
