package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.toMediaCardItem
import com.xtsream.app.engine.ExtensionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

sealed class SeeAllSource {
    data class HomeCategory(
        val apiKey: String,
        val apiName: String,
        val displayName: String,
        val title: String,
        val mainPageData: MainPageData,
        val initialItems: List<MediaCardItem>,
        val hasNext: Boolean
    ) : SeeAllSource()

    data class SearchProvider(
        val apiKey: String,
        val apiName: String,
        val displayName: String,
        val query: String,
        val initialItems: List<MediaCardItem>,
        val hasNext: Boolean
    ) : SeeAllSource()
}

data class SeeAllUiState(
    val title: String = "",
    val subtitle: String = "",
    val items: List<MediaCardItem> = emptyList(),
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val isGridView: Boolean = true,
    val nextPage: Int = 2,
    val errorMessage: String? = null
)

class SeeAllViewModel : ViewModel() {
    companion object {
        private const val TAG = "SeeAllViewModel"
    }

    private val _uiState = MutableStateFlow(SeeAllUiState())
    val uiState: StateFlow<SeeAllUiState> = _uiState.asStateFlow()

    private var source: SeeAllSource? = null
    private var initializedKey: String? = null
    private var pageJob: Job? = null
    private val pageRequestCounter = AtomicLong(0L)

    fun init(newSource: SeeAllSource) {
        val key = when (newSource) {
            is SeeAllSource.HomeCategory -> "home_${newSource.apiKey}_${newSource.title}"
            is SeeAllSource.SearchProvider -> "search_${newSource.apiKey}_${newSource.query}"
        }
        if (initializedKey == key) return
        initializedKey = key
        source = newSource

        when (newSource) {
            is SeeAllSource.HomeCategory -> {
                _uiState.value = SeeAllUiState(
                    title = newSource.title,
                    subtitle = newSource.displayName,
                    items = newSource.initialItems,
                    hasMore = newSource.hasNext,
                    nextPage = 2
                )
            }
            is SeeAllSource.SearchProvider -> {
                _uiState.value = SeeAllUiState(
                    title = newSource.displayName,
                    subtitle = "Results for \"${newSource.query}\"",
                    items = newSource.initialItems,
                    hasMore = newSource.hasNext,
                    nextPage = 2
                )
            }
        }
    }

    fun toggleLayoutMode() {
        _uiState.update { it.copy(isGridView = !it.isGridView) }
    }

    fun loadMore() {
        val current = _uiState.value
        val src = source ?: return
        if (current.isLoadingMore || !current.hasMore) return

        val myRequestId = pageRequestCounter.incrementAndGet()
        _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
        val page = current.nextPage

        pageJob?.cancel()
        pageJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                when (src) {
                    is SeeAllSource.HomeCategory -> {
                        val api = ExtensionController.resolveApi(src.apiKey, src.apiName)
                        if (api == null) {
                            if (pageRequestCounter.get() == myRequestId) {
                                _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                            }
                            return@launch
                        }
                        val req = MainPageRequest(
                            name = src.mainPageData.name,
                            data = src.mainPageData.data,
                            horizontalImages = src.mainPageData.horizontalImages
                        )
                        val resp = withTimeoutOrNull(35_000L) { api.getMainPage(page, req) }
                        if (pageRequestCounter.get() != myRequestId) return@launch

                        val newItems = resp?.items?.flatMap { hl ->
                            hl.list.map {
                                it.toMediaCardItem(
                                    isHorizontal = hl.isHorizontalImages,
                                    apiKey = src.apiKey
                                )
                            }
                        }.orEmpty()

                        _uiState.update { st ->
                            val merged = (st.items + newItems).distinctBy { it.url }
                            st.copy(
                                isLoadingMore = false,
                                items = merged,
                                nextPage = page + 1,
                                hasMore = resp?.hasNext == true && newItems.isNotEmpty()
                            )
                        }
                    }

                    is SeeAllSource.SearchProvider -> {
                        val api = ExtensionController.resolveApi(src.apiKey, src.apiName)
                        if (api == null) {
                            if (pageRequestCounter.get() == myRequestId) {
                                _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                            }
                            return@launch
                        }
                        val resp = withTimeoutOrNull(35_000L) { api.search(src.query, page) }
                        if (pageRequestCounter.get() != myRequestId) return@launch

                        val newItems = resp?.items?.map { it.toMediaCardItem(apiKey = src.apiKey) }.orEmpty()

                        _uiState.update { st ->
                            val merged = (st.items + newItems).distinctBy { it.url }
                            st.copy(
                                isLoadingMore = false,
                                items = merged,
                                nextPage = page + 1,
                                hasMore = resp?.hasNext == true && newItems.isNotEmpty()
                            )
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Pagination failed", t)
                if (pageRequestCounter.get() == myRequestId) {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMore = false,
                            errorMessage = formatFriendlyError(t, _uiState.value.subtitle.ifBlank { "provider" })
                        )
                    }
                }
            }
        }
    }
}
