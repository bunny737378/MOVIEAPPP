package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.MetaUiData
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.data.toMetaUiData
import com.xtsream.app.engine.ExtensionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MetaUiState(
    val isLoading: Boolean = true,
    val data: MetaUiData? = null,
    val errorMessage: String? = null
)

class MetaViewModel : ViewModel() {
    companion object {
        private const val TAG = "MetaViewModel"
    }

    private val _uiState = MutableStateFlow(MetaUiState())
    val uiState: StateFlow<MetaUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedUrl: String? = null

    fun load(item: MediaCardItem, force: Boolean = false) {
        if (!force && loadedUrl == item.url && _uiState.value.data != null) return
        loadedUrl = item.url
        WatchlistStore.recordViewed(item)

        loadJob?.cancel()
        _uiState.value = MetaUiState(isLoading = true, data = null, errorMessage = null)

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = ExtensionController.getApiByName(item.apiName)
                    ?: ExtensionController.activeApi.value
                    ?: run {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Provider \"${item.apiName}\" is not loaded. Reinstall it in Settings → Providers."
                            )
                        }
                        return@launch
                    }

                val response = withTimeoutOrNull(30_000L) {
                    api.load(item.url)
                }

                if (response == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "${api.name} returned empty details for this title."
                        )
                    }
                    return@launch
                }

                val uiData = response.toMetaUiData()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        data = uiData,
                        errorMessage = null
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load metadata for ${item.url}", t)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.message ?: "Unable to load details from ${item.apiName}."
                    )
                }
            }
        }
    }
}
