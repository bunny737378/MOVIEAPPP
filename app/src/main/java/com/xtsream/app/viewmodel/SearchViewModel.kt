package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.data.toMediaCardItem
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.engine.ProviderEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class SearchDubFilter(val label: String) {
    All("Any Audio"),
    Sub("Subbed"),
    Dub("Dubbed"),
    Both("Sub & Dub")
}

data class SearchFilters(
    val titleContains: String = "",
    val selectedType: String? = null, // null = All types
    val minRating: Double? = null,    // null = Any rating (e.g., 6.0, 7.0, 8.0, 9.0)
    val dubFilter: SearchDubFilter = SearchDubFilter.All
) {
    val activeCount: Int
        get() = (if (titleContains.isNotBlank()) 1 else 0) +
            (if (selectedType != null) 1 else 0) +
            (if (minRating != null) 1 else 0) +
            (if (dubFilter != SearchDubFilter.All) 1 else 0)

    val isActive: Boolean get() = activeCount > 0

    fun matches(item: MediaCardItem): Boolean {
        if (titleContains.isNotBlank()) {
            if (!item.title.contains(titleContains.trim(), ignoreCase = true)) return false
        }
        if (selectedType != null) {
            val matchType = item.type.equals(selectedType, ignoreCase = true) ||
                item.shortType.equals(selectedType, ignoreCase = true)
            if (!matchType) return false
        }
        if (minRating != null) {
            val parsed = item.score?.toDoubleOrNull() ?: return false
            if (parsed < minRating) return false
        }
        when (dubFilter) {
            SearchDubFilter.All -> Unit
            SearchDubFilter.Sub -> if (!item.hasSub) return false
            SearchDubFilter.Dub -> if (!item.hasDub) return false
            SearchDubFilter.Both -> if (!(item.hasSub && item.hasDub)) return false
        }
        return true
    }
}

data class ProviderSearchGroup(
    val providerKey: String,
    val displayName: String,
    val apiName: String,
    val items: List<MediaCardItem>,
    val hasMore: Boolean = false
)

data class SearchUiState(
    val query: String = "",
    val selectedProviderKey: String? = null, // null = All Installed Providers
    val filters: SearchFilters = SearchFilters(),
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
    private val searchRequestCounter = AtomicLong(0L)
    private val loadMoreCounter = AtomicLong(0L)

    fun focusProviderScope(providerKey: String?) {
        _uiState.update { it.copy(selectedProviderKey = providerKey) }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        if (newQuery.isBlank()) {
            quickJob?.cancel()
            searchRequestCounter.incrementAndGet()
            searchJob?.cancel()
            _uiState.update {
                it.copy(
                    isSearching = false,
                    quickSuggestions = emptyList(),
                    hasSearched = false,
                    globalGroups = emptyList(),
                    singleProviderItems = emptyList(),
                    errorMessage = null
                )
            }
            return
        }

        val targetEntry = resolveSingleTargetEntry() ?: ExtensionController.activeProvider.value
        val targetApi = targetEntry?.api
        if (targetEntry != null && targetApi != null && targetApi.hasQuickSearch) {
            quickJob?.cancel()
            quickJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    delay(350)
                    val res = withTimeoutOrNull(12_000L) { targetApi.quickSearch(newQuery.trim()) }
                    val mapped = res.orEmpty().take(8).map { it.toMediaCardItem(apiKey = targetEntry.key) }
                    _uiState.update { it.copy(quickSuggestions = mapped) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun selectProviderFilter(providerKey: String?) {
        if (_uiState.value.selectedProviderKey == providerKey) return
        _uiState.update { it.copy(selectedProviderKey = providerKey) }
        if (_uiState.value.query.isNotBlank() && _uiState.value.hasSearched) {
            executeSearch()
        }
    }

    fun updateTitleFilter(text: String) {
        _uiState.update { it.copy(filters = it.filters.copy(titleContains = text)) }
    }

    fun updateTypeFilter(type: String?) {
        _uiState.update { it.copy(filters = it.filters.copy(selectedType = type)) }
    }

    fun updateRatingFilter(minRating: Double?) {
        _uiState.update { it.copy(filters = it.filters.copy(minRating = minRating)) }
    }

    fun updateDubFilter(dubFilter: SearchDubFilter) {
        _uiState.update { it.copy(filters = it.filters.copy(dubFilter = dubFilter)) }
    }

    fun resetFilters() {
        _uiState.update { it.copy(filters = SearchFilters()) }
    }

    fun toggleLayoutMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun clearQuery() {
        searchRequestCounter.incrementAndGet()
        loadMoreCounter.incrementAndGet()
        searchJob?.cancel()
        quickJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                isSearching = false,
                isLoadingMore = false,
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
        val myRequestId = searchRequestCounter.incrementAndGet()
        loadMoreCounter.incrementAndGet()
        quickJob?.cancel()
        searchJob?.cancel()
        loadMoreJob?.cancel()

        val providers = ExtensionController.loadedProviders.value
        if (providers.isEmpty()) {
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

        val singleEntry = resolveSingleTargetEntry()
        _uiState.update {
            it.copy(
                query = q,
                hasSearched = true,
                isSearching = true,
                isLoadingMore = false,
                quickSuggestions = emptyList(),
                globalGroups = emptyList(),
                singleProviderItems = emptyList(),
                nextPage = 2,
                hasMoreSingle = false,
                errorMessage = null
            )
        }

        if (singleEntry != null) {
            searchSingleProvider(singleEntry, q, myRequestId)
        } else {
            searchAllProviders(providers, q, myRequestId)
        }
    }

    private fun resolveSingleTargetEntry(): ProviderEntry? {
        val key = _uiState.value.selectedProviderKey ?: return null
        return ExtensionController.getProviderByKey(key)
    }

    private fun searchSingleProvider(entry: ProviderEntry, query: String, myRequestId: Long) {
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val respList = withTimeoutOrNull(45_000L) {
                    entry.api.search(query, 1)
                }
                if (searchRequestCounter.get() != myRequestId) return@launch

                val items = respList?.items?.map { it.toMediaCardItem(apiKey = entry.key) }.orEmpty()
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        singleProviderItems = items,
                        nextPage = 2,
                        hasMoreSingle = respList?.hasNext == true && items.isNotEmpty(),
                        errorMessage = if (items.isEmpty()) "No matches in ${entry.displayName} for \"$query\"." else null
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Search failed for ${entry.displayName}", t)
                if (searchRequestCounter.get() == myRequestId) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            errorMessage = formatFriendlyError(t, entry.displayName)
                        )
                    }
                }
            }
        }
    }

    fun loadMoreSingleProvider() {
        val state = _uiState.value
        val entry = resolveSingleTargetEntry() ?: return
        if (state.isLoadingMore || !state.hasMoreSingle || state.query.isBlank()) return

        val myLoadMoreId = loadMoreCounter.incrementAndGet()
        _uiState.update { it.copy(isLoadingMore = true) }
        val pageToFetch = state.nextPage

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val respList = withTimeoutOrNull(35_000L) {
                    entry.api.search(state.query.trim(), pageToFetch)
                }
                if (loadMoreCounter.get() != myLoadMoreId) return@launch

                val moreItems = respList?.items?.map { it.toMediaCardItem(apiKey = entry.key) }.orEmpty()
                _uiState.update { current ->
                    val merged = (current.singleProviderItems + moreItems).distinctBy { it.url }
                    current.copy(
                        isLoadingMore = false,
                        singleProviderItems = merged,
                        nextPage = pageToFetch + 1,
                        hasMoreSingle = respList?.hasNext == true && moreItems.isNotEmpty()
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Load more failed for ${entry.displayName}", t)
                if (loadMoreCounter.get() == myLoadMoreId) {
                    _uiState.update { it.copy(isLoadingMore = false, hasMoreSingle = false) }
                }
            }
        }
    }

    private fun searchAllProviders(providers: List<ProviderEntry>, query: String, myRequestId: Long) {
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val remaining = AtomicInteger(providers.size)
            providers.forEach { entry ->
                launch(Dispatchers.IO) {
                    try {
                        val respList = withTimeoutOrNull(35_000L) {
                            entry.api.search(query, 1)
                        }
                        if (searchRequestCounter.get() != myRequestId) return@launch
                        val items = respList?.items?.map { it.toMediaCardItem(apiKey = entry.key) }.orEmpty()
                        if (items.isNotEmpty()) {
                            _uiState.update { current ->
                                val updated = (current.globalGroups + ProviderSearchGroup(
                                    providerKey = entry.key,
                                    displayName = entry.displayName,
                                    apiName = entry.api.name,
                                    items = items,
                                    hasMore = respList?.hasNext == true
                                )).distinctBy { it.providerKey }
                                current.copy(globalGroups = updated)
                            }
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        Log.w(TAG, "Global search error on ${entry.displayName}", t)
                    } finally {
                        if (searchRequestCounter.get() == myRequestId && remaining.decrementAndGet() <= 0) {
                            _uiState.update { current ->
                                current.copy(
                                    isSearching = false,
                                    errorMessage = if (current.globalGroups.isEmpty()) {
                                        "No results found across installed providers for \"$query\"."
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
