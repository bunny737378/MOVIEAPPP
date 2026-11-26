package com.xtsream.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.network.CloudflareVerifier
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.xtsream.app.data.MediaCardItem
import com.xtsream.app.data.MetaEpisodeItem
import com.xtsream.app.data.MetaUiData
import com.xtsream.app.data.WatchlistStore
import com.xtsream.app.data.toMetaUiData
import com.xtsream.app.engine.ExtensionController
import com.xtsream.app.engine.abbreviateProviderName
import com.xtsream.app.stremio.StremioClient
import com.xtsream.app.stremio.StremioMainApiAdapter
import com.xtsream.app.stremio.StremioRepository
import com.xtsream.app.stremio.StremioStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
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

data class StreamServerItem(
    val id: String,
    val name: String,
    val source: String,
    val url: String,
    val qualityLabel: String,
    val audioTag: String? = null,
    val typeBadge: String = "STREAM",
    val extraInfo: String? = null
)

data class StreamSheetState(
    val activeKey: String? = null,
    val barLabel: String = "",
    val subtitle: String = "",
    val isExpanded: Boolean = false,
    val isMinimized: Boolean = false,
    val isLoading: Boolean = false,
    val servers: List<StreamServerItem> = emptyList(),
    val errorMessage: String? = null
)

class MetaViewModel : ViewModel() {
    companion object {
        private const val TAG = "MetaViewModel"
    }

    private val _uiState = MutableStateFlow(MetaUiState())
    val uiState: StateFlow<MetaUiState> = _uiState.asStateFlow()

    private val _streamState = MutableStateFlow(StreamSheetState())
    val streamState: StateFlow<StreamSheetState> = _streamState.asStateFlow()

    private var loadJob: Job? = null
    private var streamJob: Job? = null
    private var loadedUrl: String? = null
    private var loadedApiKey: String? = null
    private var lastRequestedItem: MediaCardItem? = null
    private var lastStreamTargetMeta: MetaUiData? = null
    private var lastStreamTargetEpisode: MetaEpisodeItem? = null
    private val requestCounter = AtomicLong(0L)
    private val streamRequestCounter = AtomicLong(0L)

    init {
        viewModelScope.launch {
            CloudflareVerifier.verificationCompleteCounter.drop(1).collect {
                val item = lastRequestedItem
                if (item != null && _uiState.value.data == null && !_uiState.value.isLoading) {
                    load(item, force = true)
                }
            }
        }
    }

    fun load(item: MediaCardItem, force: Boolean = false) {
        val current = _uiState.value
        val sameTarget = loadedUrl == item.url && loadedApiKey == item.apiKey
        if (!force && sameTarget && current.data != null && !current.isLoading && current.errorMessage == null) {
            return
        }

        lastRequestedItem = item
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
                                errorMessage = "Provider \"${abbreviateProviderName(item.apiName)}\" is not loaded. Check Providers tab."
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
                    val shortName = entry?.shortDisplayName ?: abbreviateProviderName(api.name)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "$shortName timed out or returned empty details. Tap Retry to try again."
                        )
                    }
                    return@launch
                }

                val resolvedKey = entry?.key ?: ExtensionController.providerKeyOf(api)
                val resolvedShortDisplay = entry?.shortDisplayName ?: abbreviateProviderName(api.name)
                val uiData = response.toMetaUiData(
                    apiKey = resolvedKey,
                    sourceDisplayName = resolvedShortDisplay
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
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to load metadata for ${item.url}", t)
                if (requestCounter.get() == myRequestId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = formatFriendlyError(t, abbreviateProviderName(item.apiName))
                        )
                    }
                }
            }
        }
    }

    fun openStreamsForEpisode(meta: MetaUiData, episode: MetaEpisodeItem, forceReload: Boolean = false) {
        val epKey = "ep::${meta.apiKey}::${episode.season}::${episode.number}::${episode.data}"
        val current = _streamState.value
        if (!forceReload && current.activeKey == epKey && (current.isLoading || current.servers.isNotEmpty())) {
            // Re-open the exact same loaded/loading episode panel without re-fetching!
            _streamState.update { it.copy(isExpanded = true, isMinimized = false) }
            return
        }

        lastStreamTargetMeta = meta
        lastStreamTargetEpisode = episode
        val barLabel = formatEpisodeBarLabel(episode)
        startStreamFetch(
            key = epKey,
            barLabel = barLabel,
            subtitle = episode.title,
            meta = meta,
            episode = episode
        )
    }

    fun openStreamsForMovie(meta: MetaUiData, forceReload: Boolean = false) {
        val movieKey = "movie::${meta.apiKey}::${meta.movieDataUrl ?: meta.url}"
        val current = _streamState.value
        if (!forceReload && current.activeKey == movieKey && (current.isLoading || current.servers.isNotEmpty())) {
            _streamState.update { it.copy(isExpanded = true, isMinimized = false) }
            return
        }

        lastStreamTargetMeta = meta
        lastStreamTargetEpisode = null
        startStreamFetch(
            key = movieKey,
            barLabel = "Movie",
            subtitle = meta.title,
            meta = meta,
            episode = null
        )
    }

    fun minimizeStreams() {
        _streamState.update {
            if (it.activeKey != null) {
                it.copy(isExpanded = false, isMinimized = true)
            } else {
                it
            }
        }
    }

    fun expandStreams() {
        _streamState.update {
            if (it.activeKey != null) {
                it.copy(isExpanded = true, isMinimized = false)
            } else {
                it
            }
        }
    }

    fun retryStreams() {
        val meta = lastStreamTargetMeta ?: return
        val ep = lastStreamTargetEpisode
        if (ep != null) {
            openStreamsForEpisode(meta, ep, forceReload = true)
        } else {
            openStreamsForMovie(meta, forceReload = true)
        }
    }

    private fun startStreamFetch(
        key: String,
        barLabel: String,
        subtitle: String,
        meta: MetaUiData,
        episode: MetaEpisodeItem?
    ) {
        val myStreamId = streamRequestCounter.incrementAndGet()
        streamJob?.cancel()

        _streamState.value = StreamSheetState(
            activeKey = key,
            barLabel = barLabel,
            subtitle = subtitle,
            isExpanded = true,
            isMinimized = false,
            isLoading = true,
            servers = emptyList(),
            errorMessage = null
        )

        streamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val api = ExtensionController.resolveApi(meta.apiKey, meta.apiName)
                if (api == null) {
                    if (streamRequestCounter.get() == myStreamId) {
                        _streamState.update {
                            it.copy(isLoading = false, errorMessage = "Provider is not loaded.")
                        }
                    }
                    return@launch
                }

                if (api is StremioMainApiAdapter) {
                    loadStremioStreamsRealtime(
                        adapter = api,
                        meta = meta,
                        episode = episode,
                        myStreamId = myStreamId
                    )
                } else {
                    loadCloudStreamLinksRealtime(
                        api = api,
                        meta = meta,
                        episode = episode,
                        myStreamId = myStreamId
                    )
                }

                if (streamRequestCounter.get() == myStreamId) {
                    _streamState.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = if (current.servers.isEmpty()) {
                                "No servers found for $barLabel."
                            } else null
                        )
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Stream loading failed for $barLabel", t)
                if (streamRequestCounter.get() == myStreamId) {
                    _streamState.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = if (current.servers.isEmpty()) {
                                formatFriendlyError(t, meta.sourceDisplayName)
                            } else null
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadStremioStreamsRealtime(
        adapter: StremioMainApiAdapter,
        meta: MetaUiData,
        episode: MetaEpisodeItem?,
        myStreamId: Long
    ) {
        val parsed = parseStremioUrl(meta.url)
        val stremioType = parsed?.first ?: if (meta.isMovieLike) "movie" else "series"
        val fallbackItemId = parsed?.second ?: meta.movieDataUrl.orEmpty()
        val videoId = episode?.data?.takeIf { it.isNotBlank() } ?: fallbackItemId
        if (videoId.isBlank()) return

        val enabledAddons = StremioRepository.getEnabledAddons()
        StremioClient.fetchStreamsRealtime(
            preferredAddon = adapter.addon,
            allEnabledAddons = enabledAddons,
            type = stremioType,
            videoId = videoId,
            onAddonStreams = { _, batch ->
                if (streamRequestCounter.get() != myStreamId) return@fetchStreamsRealtime
                val mapped = batch.mapNotNull { it.toStreamServerItem() }
                if (mapped.isNotEmpty()) {
                    appendServersLive(myStreamId, mapped)
                }
            }
        )
    }

    private suspend fun loadCloudStreamLinksRealtime(
        api: com.lagradost.cloudstream3.MainAPI,
        meta: MetaUiData,
        episode: MetaEpisodeItem?,
        myStreamId: Long
    ) {
        val targets: List<Pair<String, String?>> = if (episode != null) {
            val subUrl = episode.subData?.trim()?.takeIf { it.isNotBlank() }
            val dubUrl = episode.dubData?.trim()?.takeIf { it.isNotBlank() }
            when {
                subUrl != null && dubUrl != null && subUrl != dubUrl ->
                    listOf(subUrl to "SUB", dubUrl to "DUB")
                subUrl != null ->
                    listOf(subUrl to if (episode.hasSub) "SUB" else null)
                dubUrl != null ->
                    listOf(dubUrl to if (episode.hasDub) "DUB" else null)
                else ->
                    listOf(episode.data to when {
                        episode.hasSub && !episode.hasDub -> "SUB"
                        episode.hasDub && !episode.hasSub -> "DUB"
                        else -> null
                    })
            }
        } else {
            val movieData = meta.movieDataUrl?.takeIf { it.isNotBlank() } ?: meta.url
            listOf(movieData to null)
        }

        coroutineScope {
            targets.map { (targetData, audioTag) ->
                async(Dispatchers.IO) {
                    if (targetData.isBlank()) return@async
                    withTimeoutOrNull(45_000L) {
                        runCatching {
                            api.loadLinks(
                                data = targetData,
                                isCasting = false,
                                subtitleCallback = {},
                                callback = { link ->
                                    if (streamRequestCounter.get() == myStreamId) {
                                        val item = link.toStreamServerItem(audioTag)
                                        if (item != null) {
                                            appendServersLive(myStreamId, listOf(item))
                                        }
                                    }
                                }
                            )
                        }.onFailure { err ->
                            if (err is CancellationException) throw err
                            Log.w(TAG, "loadLinks error for $targetData", err)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun appendServersLive(myStreamId: Long, incoming: List<StreamServerItem>) {
        if (incoming.isEmpty() || streamRequestCounter.get() != myStreamId) return
        _streamState.update { current ->
            val existingKeys = current.servers.map { "${it.url}::${it.name}::${it.audioTag}" }.toHashSet()
            val fresh = incoming.filter { item ->
                existingKeys.add("${item.url}::${item.name}::${item.audioTag}")
            }
            if (fresh.isEmpty()) current
            else current.copy(servers = current.servers + fresh, errorMessage = null)
        }
    }

    private fun ExtractorLink.toStreamServerItem(audioTag: String?): StreamServerItem? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null
        val qLabel = Qualities.getStringByInt(quality).ifBlank {
            extractQualityFromText("$name $source") ?: ""
        }
        val typeLabel = when (type) {
            ExtractorLinkType.M3U8 -> "HLS"
            ExtractorLinkType.DASH -> "DASH"
            ExtractorLinkType.TORRENT, ExtractorLinkType.MAGNET -> "TORRENT"
            ExtractorLinkType.VIDEO -> "MP4"
            else -> if (cleanUrl.contains(".m3u8", ignoreCase = true)) "HLS" else "VIDEO"
        }
        val cleanName = name.trim().ifBlank { source.trim().ifBlank { "Server" } }
        val cleanSource = source.trim().ifBlank { cleanName }
        val hostInfo = runCatching { android.net.Uri.parse(cleanUrl).host }.getOrNull()
            ?.removePrefix("www.")
        val extra = if (!cleanSource.equals(cleanName, ignoreCase = true)) {
            listOfNotNull(cleanSource, hostInfo).joinToString(" • ")
        } else {
            hostInfo
        }
        return StreamServerItem(
            id = "${cleanUrl}_${cleanName}_${audioTag.orEmpty()}",
            name = cleanName,
            source = cleanSource,
            url = cleanUrl,
            qualityLabel = qLabel,
            audioTag = audioTag,
            typeBadge = typeLabel,
            extraInfo = extra
        )
    }

    private fun StremioStream.toStreamServerItem(): StreamServerItem? {
        val link = resolvedLink.trim()
        if (link.isBlank()) return null
        val rawTitle = (title ?: description ?: filename).orEmpty().trim()
        val rawName = name?.trim().orEmpty()
        val combinedText = "$rawName $rawTitle"
        val qLabel = extractQualityFromText(combinedText).orEmpty()
        val typeBadge = when {
            !infoHash.isNullOrBlank() -> "TORRENT"
            link.contains(".m3u8", ignoreCase = true) -> "HLS"
            link.contains(".mpd", ignoreCase = true) -> "DASH"
            !externalUrl.isNullOrBlank() -> "WEB"
            else -> "DIRECT"
        }
        val displayTitle = rawName.replace('\n', ' ').trim().ifBlank { addonName }
        val extra = rawTitle.replace("\n", " • ").trim().takeIf { it.isNotBlank() && it != displayTitle }
        return StreamServerItem(
            id = "${addonName}_${link}_${fileIdx ?: 0}",
            name = displayTitle,
            source = abbreviateProviderName(addonName),
            url = link,
            qualityLabel = qLabel,
            audioTag = null,
            typeBadge = typeBadge,
            extraInfo = extra
        )
    }

    private fun extractQualityFromText(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("2160p") || Regex("\\b4k\\b").containsMatchIn(lower) || lower.contains("uhd") -> "4K"
            lower.contains("1440p") -> "1440p"
            lower.contains("1080p") || lower.contains("fhd") -> "1080p"
            lower.contains("720p") -> "720p"
            lower.contains("480p") -> "480p"
            lower.contains("360p") -> "360p"
            else -> null
        }
    }

    private fun parseStremioUrl(url: String): Pair<String, String>? {
        val schemeIdx = url.indexOf("stremio://", ignoreCase = true)
        if (schemeIdx < 0) return null
        val remainder = url.substring(schemeIdx + "stremio://".length)
        val firstSlash = remainder.indexOf('/')
        if (firstSlash < 0) return null
        val afterAddon = remainder.substring(firstSlash + 1)
        val secondSlash = afterAddon.indexOf('/')
        if (secondSlash < 0) return null
        val type = afterAddon.substring(0, secondSlash).trim()
        val id = afterAddon.substring(secondSlash + 1).trim()
        if (type.isBlank() || id.isBlank()) return null
        return type to id
    }

    private fun formatEpisodeBarLabel(episode: MetaEpisodeItem): String {
        val seasonPart = episode.season?.takeIf { it > 0 }?.let { "S${it.toString().padStart(2, '0')}" }
        val epPart = episode.number?.takeIf { it >= 0 }?.let { "EP${it.toString().padStart(2, '0')}" }
        return listOfNotNull(seasonPart, epPart).joinToString(" • ").ifBlank { "Episode" }
    }

    override fun onCleared() {
        requestCounter.incrementAndGet()
        streamRequestCounter.incrementAndGet()
        loadJob?.cancel()
        streamJob?.cancel()
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
