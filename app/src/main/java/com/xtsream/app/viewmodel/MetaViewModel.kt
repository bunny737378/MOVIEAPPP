package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.MetaUiData
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.data.toMetaUiData
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLHandshakeException

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
    private var loadedApiKey: String? = null
    private val requestCounter = AtomicLong(0L)

    fun load(item: MediaCardItem, force: Boolean = false) {
        val current = _uiState.value
        val sameTarget = loadedUrl == item.url && loadedApiKey == item.apiKey
        // Only skip if we already have valid loaded data for this exact title
        if (!force && sameTarget && current.data != null && !current.isLoading && current.errorMessage == null) {
            return
        }

        loadedUrl = item.url
        loadedApiKey = item.apiKey
        WatchlistStore.recordViewed(item)

        val myRequestId = requestCounter.incrementAndGet()
        loadJob?.cancel()
        _uiState.value = MetaUiState(isLoading = true, data = null, errorMessage = null)

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val entry = ExtensionController.resolveProvider(item.apiKey, item.apiName)
                val api = entry?.api ?: ExtensionController.resolveApi(item.apiKey, item.apiName)
                if (api == null) {
                    if (requestCounter.get() == myRequestId) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Provider \"${item.apiName}\" is not loaded. Check Settings → Providers."
                            )
                        }
                    }
                    return@launch
                }

                val response = withTimeoutOrNull(45_000L) {
                    api.load(item.url)
                }

                if (requestCounter.get() != myRequestId) return@launch

                if (response == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "${entry?.displayName ?: api.name} timed out or returned empty details. Tap Retry to try again."
                        )
                    }
                    return@launch
                }

                val resolvedKey = entry?.key ?: ExtensionController.providerKeyOf(api)
                val resolvedDisplay = entry?.displayName ?: api.name
                val uiData = response.toMetaUiData(
                    apiKey = resolvedKey,
                    sourceDisplayName = resolvedDisplay
                )
                if (requestCounter.get() == myRequestId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            data = uiData,
                            errorMessage = null
                        )
                    }
                }
            } catch (ce: CancellationException) {
                // Never treat coroutine cancellation as a UI error — rethrow cleanly!
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load metadata for ${item.url}", t)
                if (requestCounter.get() == myRequestId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = formatFriendlyError(t, item.apiName)
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        requestCounter.incrementAndGet()
        loadJob?.cancel()
        super.onCleared()
    }
}

internal fun formatFriendlyError(t: Throwable, sourceName: String): String {
    return when (t) {
        is SocketTimeoutException -> "Connection timed out while contacting $sourceName. Tap Retry."
        is UnknownHostException -> "Cannot reach $sourceName. Check your internet or try changing DoH DNS in Settings."
        is SSLHandshakeException -> "SSL error connecting to $sourceName. Try switching DNS in Settings."
        else -> {
            val raw = t.message?.trim().orEmpty()
            if (raw.isBlank() || raw.contains("Coroutine", ignoreCase = true) || raw.contains("cancelled", ignoreCase = true)) {
                "Connection interrupted while loading from $sourceName. Tap Retry."
            } else {
                raw
            }
        }
    }
}
