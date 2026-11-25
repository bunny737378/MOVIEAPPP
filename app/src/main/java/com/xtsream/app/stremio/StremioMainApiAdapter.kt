package com.xtsream.app.stremio

import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges any installed Stremio Addon into the unified MainAPI contract so Stremio addons
 * work seamlessly across Home (catalog rows + Hero + See All with skip pagination),
 * Search (both All-providers & single-provider, including catalog-only addons), and
 * Meta (episodes, seasons, cast, genres, rating).
 */
class StremioMainApiAdapter(
    val addon: StremioAddonRecord
) : MainAPI() {
    override var mainUrl: String = addon.baseUrl
    override var name: String = addon.manifest.name
    override var lang: String = "en"
    override val hasMainPage: Boolean = addon.manifest.hasBrowseableCatalogs()
    override val hasQuickSearch: Boolean = false

    override val supportedTypes: Set<TvType> = buildSet {
        val rawTypes = (addon.manifest.types + addon.manifest.catalogs.map { it.type }).map { it.lowercase() }
        if ("movie" in rawTypes) add(TvType.Movie)
        if ("series" in rawTypes) add(TvType.TvSeries)
        if ("anime" in rawTypes) add(TvType.Anime)
        if ("tv" in rawTypes || "channel" in rawTypes) add(TvType.Live)
        if (isEmpty()) {
            add(TvType.Movie)
            add(TvType.TvSeries)
        }
    }

    private val catalogSkipCache = ConcurrentHashMap<String, Int>()
    private val previewCache = ConcurrentHashMap<String, StremioMetaPreview>()

    init {
        sourcePlugin = "stremio::${addon.manifestUrl}"
    }

    private val browseCatalogs: List<StremioCatalogDef> = run {
        val direct = addon.manifest.catalogs.filter { !it.hasAnyRequiredExtra() }
        if (direct.isNotEmpty()) {
            direct.take(16)
        } else {
            // Support addons whose catalogs only require a selectable genre option
            addon.manifest.catalogs.filter { cat ->
                val reqs = cat.extra.filter { it.isRequired }
                reqs.isNotEmpty() && reqs.all { it.name.equals("genre", true) && it.options.isNotEmpty() }
            }.take(16)
        }
    }

    private val searchCatalogs: List<StremioCatalogDef> =
        addon.manifest.catalogs
            .filter { it.supportsSearch() }
            .sortedByDescending { it.isSearchOnly() }
            .take(16)

    override val mainPage: List<MainPageData> = browseCatalogs.mapIndexed { idx, cat ->
        MainPageData(
            name = formatCatalogTitle(cat),
            data = "$idx",
            horizontalImages = false
        )
    }

    private fun formatCatalogTitle(cat: StremioCatalogDef): String {
        val typeLabel = when (cat.type.lowercase()) {
            "movie" -> "Movies"
            "series" -> "Series"
            "anime" -> "Anime"
            "tv", "channel" -> "Live"
            else -> cat.type.replaceFirstChar { it.uppercase() }
        }
        val cleanName = cat.name.trim()
        return when {
            cleanName.isBlank() -> typeLabel
            cleanName.contains(typeLabel, ignoreCase = true) ||
                cleanName.contains(cat.type, ignoreCase = true) -> cleanName
            else -> "$cleanName • $typeLabel"
        }
    }

    private fun mapTvType(rawType: String): TvType = when (rawType.lowercase()) {
        "movie" -> TvType.Movie
        "series" -> TvType.TvSeries
        "anime" -> TvType.Anime
        "tv", "channel" -> TvType.Live
        else -> TvType.Others
    }

    private fun buildItemUrl(type: String, id: String): String =
        "stremio://${addon.manifest.id}/${type}/${id}"

    private fun parseItemUrl(url: String): Pair<String, String>? {
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

    private fun StremioMetaPreview.toSearchResponse(): SearchResponse {
        val itemUrl = buildItemUrl(type, id)
        previewCache[itemUrl] = this
        val tvType = mapTvType(type)
        val parsedScore = imdbRating?.toDoubleOrNull()?.let { Score.from10(it) }
        val parsedYear = extractStartYear(releaseInfo)
        // CRITICAL: fix = false so MainAPI.fixUrl() NEVER prepends mainUrl in front of stremio://...
        return when (tvType) {
            TvType.Anime -> newAnimeSearchResponse(name = name, url = itemUrl, type = TvType.Anime, fix = false) {
                posterUrl = poster
                score = parsedScore
                year = parsedYear
            }
            TvType.TvSeries -> newTvSeriesSearchResponse(name = name, url = itemUrl, type = TvType.TvSeries, fix = false) {
                posterUrl = poster
                score = parsedScore
                year = parsedYear
            }
            else -> newMovieSearchResponse(name = name, url = itemUrl, type = tvType, fix = false) {
                posterUrl = poster
                score = parsedScore
                year = parsedYear
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val catIdx = request.data.toIntOrNull() ?: return null
        val catalog = browseCatalogs.getOrNull(catIdx) ?: return null

        val cacheKey = "${catalog.type}::${catalog.id}"
        if (page > 1 && !catalog.supportsSkip()) {
            return newHomePageResponse(HomePageList(request.name, emptyList()), hasNext = false)
        }

        val defaultRequiredGenre = catalog.extra
            .firstOrNull { it.name.equals("genre", true) && it.isRequired }
            ?.options?.firstOrNull()

        val skip = if (page <= 1) 0 else (catalogSkipCache[cacheKey] ?: ((page - 1) * 20))
        val metas = StremioClient.fetchCatalog(
            addon = addon,
            catalog = catalog,
            skip = skip,
            genre = defaultRequiredGenre
        )
        if (page <= 1) {
            catalogSkipCache[cacheKey] = metas.size
        } else {
            catalogSkipCache[cacheKey] = skip + metas.size
        }

        val responses = metas.map { it.toSearchResponse() }.distinctBy { it.url }
        val hasNext = catalog.supportsSkip() && responses.isNotEmpty()
        return newHomePageResponse(
            list = HomePageList(name = request.name, list = responses, isHorizontalImages = false),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return newSearchResponseList(emptyList(), hasNext = false)

        // 1. Primary path: addon declares searchable catalogs (`extra` or `extraSupported` contains "search")
        if (searchCatalogs.isNotEmpty()) {
            if (page > 1 && searchCatalogs.none { it.supportsSkip() }) {
                return newSearchResponseList(emptyList(), hasNext = false)
            }
            val skip = if (page <= 1) 0 else (page - 1) * 20
            val allResults = coroutineScope {
                searchCatalogs.map { catalog ->
                    async {
                        if (page > 1 && !catalog.supportsSkip()) return@async emptyList()
                        runCatching {
                            StremioClient.fetchCatalog(
                                addon = addon,
                                catalog = catalog,
                                skip = skip,
                                search = trimmed
                            ).map { it.toSearchResponse() }
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }.distinctBy { it.url }

            val hasNext = searchCatalogs.any { it.supportsSkip() } && allResults.size >= 15
            return newSearchResponseList(allResults, hasNext = hasNext)
        }

        // 2. Fallback for catalog-only addons (e.g., Streaming Catalogs, TopLists, MDBList) that do NOT declare `search` extra:
        //    Only run on page 1; first try server-side search on primary catalogs, then filter browseable catalogs locally.
        if (page > 1 || browseCatalogs.isEmpty()) {
            return newSearchResponseList(emptyList(), hasNext = false)
        }

        val probedServer = coroutineScope {
            browseCatalogs.take(3).map { catalog ->
                async {
                    runCatching {
                        StremioClient.fetchCatalog(
                            addon = addon,
                            catalog = catalog,
                            skip = 0,
                            search = trimmed
                        ).map { it.toSearchResponse() }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.distinctBy { it.url }

        if (probedServer.isNotEmpty()) {
            return newSearchResponseList(probedServer, hasNext = false)
        }

        // Local catalog title/genre filter across browseable catalogs so catalog-only addons still return relevant matches
        val queryTokens = trimmed.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val localMatched = coroutineScope {
            browseCatalogs.take(6).map { catalog ->
                async {
                    val defaultGenre = catalog.extra
                        .firstOrNull { it.name.equals("genre", true) && it.isRequired }
                        ?.options?.firstOrNull()
                    runCatching {
                        StremioClient.fetchCatalog(addon = addon, catalog = catalog, skip = 0, genre = defaultGenre)
                            .filter { meta ->
                                val hay = "${meta.name} ${meta.description.orEmpty()} ${meta.genres.joinToString(" ")}".lowercase()
                                queryTokens.all { tok -> hay.contains(tok) }
                            }
                            .map { it.toSearchResponse() }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.distinctBy { it.url }

        return newSearchResponseList(localMatched, hasNext = false)
    }

    override suspend fun load(url: String): LoadResponse? {
        val (type, id) = parseItemUrl(url) ?: return null
        val canonicalUrl = buildItemUrl(type, id)
        val preview = previewCache[url] ?: previewCache[canonicalUrl]

        val meta = StremioClient.fetchMeta(
            preferredAddon = addon,
            allEnabledAddons = StremioRepository.getEnabledAddons(),
            type = type,
            id = id
        )

        val title = meta?.name ?: preview?.name ?: id
        val poster = meta?.poster ?: preview?.poster
        val backdrop = meta?.background ?: preview?.background ?: poster
        val plotText = meta?.description ?: preview?.description
        val yearVal = extractStartYear(meta?.releaseInfo ?: meta?.released ?: preview?.releaseInfo)
        val scoreVal = (meta?.imdbRating ?: preview?.imdbRating)?.toDoubleOrNull()?.let { Score.from10(it) }
        val durationMin = parseRuntimeMinutes(meta?.runtime)
        val tagsList = meta?.genres?.ifEmpty { preview?.genres.orEmpty() } ?: preview?.genres.orEmpty()
        val actorList = meta?.cast.orEmpty().map { ActorData(Actor(it)) }

        val tvType = mapTvType(meta?.type ?: type)
        val videos = meta?.videos.orEmpty()

        return if (videos.isNotEmpty() || tvType == TvType.TvSeries || tvType == TvType.Anime) {
            val episodes = videos.mapIndexed { idx, v ->
                newEpisode(data = v.id) {
                    name = v.title
                    season = v.season ?: 1
                    episode = v.episode ?: (idx + 1)
                    posterUrl = v.thumbnail ?: backdrop
                    description = v.overview
                }
            }
            newTvSeriesLoadResponse(
                name = title,
                url = canonicalUrl,
                type = if (tvType == TvType.Anime) TvType.Anime else TvType.TvSeries,
                episodes = episodes
            ) {
                this.url = canonicalUrl
                posterUrl = poster
                backgroundPosterUrl = backdrop
                logoUrl = meta?.logo ?: preview?.logo
                plot = plotText
                year = yearVal
                score = scoreVal
                duration = durationMin
                tags = tagsList.takeIf { it.isNotEmpty() }
                actors = actorList.takeIf { it.isNotEmpty() }
            }
        } else {
            newMovieLoadResponse(
                name = title,
                url = canonicalUrl,
                type = tvType,
                dataUrl = id
            ) {
                this.url = canonicalUrl
                posterUrl = poster
                backgroundPosterUrl = backdrop
                logoUrl = meta?.logo ?: preview?.logo
                plot = plotText
                year = yearVal
                score = scoreVal
                duration = durationMin
                tags = tagsList.takeIf { it.isNotEmpty() }
                actors = actorList.takeIf { it.isNotEmpty() }
            }
        }
    }

    private fun extractStartYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        return Regex("\\b(19\\d{2}|20\\d{2})\\b").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun parseRuntimeMinutes(runtime: String?): Int? {
        if (runtime.isNullOrBlank()) return null
        val lower = runtime.lowercase()
        val hours = Regex("(\\d+)\\s*h").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val mins = Regex("(\\d+)\\s*m").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (hours > 0 || mins != null) return hours * 60 + (mins ?: 0)
        return Regex("(\\d+)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
