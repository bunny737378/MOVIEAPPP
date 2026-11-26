package com.xtsream.app.data

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType

data class MediaCardItem(
    val title: String,
    val url: String,
    val apiName: String,
    val apiKey: String = "",
    val posterUrl: String?,
    val posterHeaders: Map<String, String> = emptyMap(),
    val type: String = "",
    val shortType: String = "",
    val quality: String? = null,
    val score: String? = null,
    val year: Int? = null,
    val hasSub: Boolean = false,
    val hasDub: Boolean = false,
    val subEpisodes: Int? = null,
    val dubEpisodes: Int? = null,
    val episodeCount: Int? = null,
    val isHorizontal: Boolean = false
)

data class HomeSection(
    val id: String,
    val title: String,
    val items: List<MediaCardItem>,
    val isHorizontal: Boolean = false,
    val mainPageData: MainPageData? = null,
    val hasNext: Boolean = false
)

data class MetaActorItem(
    val name: String,
    val role: String?,
    val imageUrl: String?
)

data class MetaEpisodeItem(
    val number: Int?,
    val season: Int?,
    val title: String,
    val data: String,
    val posterUrl: String?,
    val description: String?,
    val score: String?,
    val runTime: Int?,
    val airDateMs: Long?,
    val hasSub: Boolean = false,
    val hasDub: Boolean = false,
    val subData: String? = null,
    val dubData: String? = null
)

data class MetaSeasonGroup(
    val seasonNumber: Int,
    val displayName: String,
    val episodes: List<MetaEpisodeItem>
)

data class MetaUiData(
    val title: String,
    val url: String,
    val apiName: String,
    val apiKey: String,
    val sourceDisplayName: String,
    val typeLabel: String,
    val isMovieLike: Boolean,
    val movieDataUrl: String? = null,
    val posterUrl: String?,
    val backgroundUrl: String?,
    val logoUrl: String?,
    val posterHeaders: Map<String, String>,
    val year: Int?,
    val score: String?,
    val durationMinutes: Int?,
    val contentRating: String?,
    val showStatus: String?,
    val plot: String,
    val tags: List<String>,
    val actors: List<MetaActorItem>,
    val recommendations: List<MediaCardItem>,
    val comingSoon: Boolean,
    val seasons: List<MetaSeasonGroup> = emptyList(),
    val hasAnySub: Boolean = false,
    val hasAnyDub: Boolean = false,
    val dubStatusMap: Map<String, List<MetaSeasonGroup>>,
    val defaultDubKey: String
)

fun SearchResponse.toMediaCardItem(
    isHorizontal: Boolean = false,
    apiKey: String = ""
): MediaCardItem {
    val formattedScore = runCatching { score?.toStringNull(0.1, 10, 1) }.getOrNull()
    var hasSub = false
    var hasDub = false
    var subEps: Int? = null
    var dubEps: Int? = null
    var epCount: Int? = null
    var yearVal: Int? = null

    when (this) {
        is AnimeSearchResponse -> {
            yearVal = year
            val statuses = dubStatus.orEmpty()
            hasSub = DubStatus.Subbed in statuses || (episodes[DubStatus.Subbed] ?: 0) > 0
            hasDub = DubStatus.Dubbed in statuses || (episodes[DubStatus.Dubbed] ?: 0) > 0
            subEps = episodes[DubStatus.Subbed]?.takeIf { it > 0 }
            dubEps = episodes[DubStatus.Dubbed]?.takeIf { it > 0 }
        }
        is TvSeriesSearchResponse -> {
            yearVal = year
            epCount = episodes?.takeIf { it > 0 }
        }
        is MovieSearchResponse -> {
            yearVal = year
        }
    }

    return MediaCardItem(
        title = name.trim(),
        url = url,
        apiName = apiName,
        apiKey = apiKey,
        posterUrl = posterUrl,
        posterHeaders = posterHeaders ?: emptyMap(),
        type = type?.let { formatTvType(it) } ?: "",
        shortType = type?.let { formatShortTvType(it) } ?: "",
        quality = quality?.name,
        score = formattedScore,
        year = yearVal,
        hasSub = hasSub,
        hasDub = hasDub,
        subEpisodes = subEps,
        dubEpisodes = dubEps,
        episodeCount = epCount,
        isHorizontal = isHorizontal
    )
}

fun LoadResponse.toMetaUiData(
    apiKey: String = "",
    sourceDisplayName: String = apiName
): MetaUiData {
    val formattedScore = runCatching { score?.toStringNull(0.1, 10, 1) }.getOrNull()
    val actorItems = actors.orEmpty().mapNotNull { actorData ->
        val actorName = actorData.actor.name.trim()
        if (actorName.isBlank()) return@mapNotNull null
        MetaActorItem(
            name = actorName,
            role = actorData.roleString?.takeIf { it.isNotBlank() }
                ?: actorData.role?.name,
            imageUrl = actorData.actor.image
        )
    }

    val recs = recommendations.orEmpty().map { it.toMediaCardItem(apiKey = apiKey) }.distinctBy { it.url }
    val dubMap = linkedMapOf<String, List<MetaSeasonGroup>>()
    var unifiedSeasons: List<MetaSeasonGroup> = emptyList()
    var showStatusLabel: String? = null
    var movieDataUrl: String? = null

    when (this) {
        is TvSeriesLoadResponse -> {
            showStatusLabel = showStatus?.name
            val seasonNameMap = seasonNames.orEmpty().associate {
                it.season to (it.name ?: "Season ${it.displaySeason ?: it.season}")
            }
            val grouped = groupEpisodesIntoSeasons(episodes, seasonNameMap)
            unifiedSeasons = grouped
            if (grouped.isNotEmpty()) {
                dubMap["Default"] = grouped
            }
        }
        is AnimeLoadResponse -> {
            showStatusLabel = showStatus?.name
            val seasonNameMap = seasonNames.orEmpty().associate {
                it.season to (it.name ?: "Season ${it.displaySeason ?: it.season}")
            }
            // Merge Sub & Dub into ONE unified season list so episodes show white Sub/Dub icon badges instead of a top dropdown!
            val merged = mergeAnimeEpisodesIntoSeasons(episodes, seasonNameMap)
            unifiedSeasons = merged
            if (merged.isNotEmpty()) {
                dubMap["Default"] = merged
            }
        }
        is MovieLoadResponse -> {
            movieDataUrl = dataUrl.takeIf { it.isNotBlank() }
        }
        is LiveStreamLoadResponse -> {
            movieDataUrl = dataUrl.takeIf { it.isNotBlank() }
        }
    }

    val hasAnySub = unifiedSeasons.any { s -> s.episodes.any { it.hasSub } }
    val hasAnyDub = unifiedSeasons.any { s -> s.episodes.any { it.hasDub } }

    val isMovieLike = unifiedSeasons.isEmpty() && (
        type == TvType.Movie ||
            type == TvType.AnimeMovie ||
            type == TvType.OVA ||
            type == TvType.Documentary ||
            type == TvType.Live ||
            !movieDataUrl.isNullOrBlank()
        )

    return MetaUiData(
        title = name.trim(),
        url = url,
        apiName = apiName,
        apiKey = apiKey,
        sourceDisplayName = sourceDisplayName,
        typeLabel = formatTvType(type),
        isMovieLike = isMovieLike,
        movieDataUrl = movieDataUrl,
        posterUrl = posterUrl,
        backgroundUrl = backgroundPosterUrl?.takeIf { it.isNotBlank() } ?: posterUrl,
        logoUrl = logoUrl?.takeIf { it.isNotBlank() },
        posterHeaders = posterHeaders ?: emptyMap(),
        year = year,
        score = formattedScore,
        durationMinutes = duration,
        contentRating = contentRating?.takeIf { it.isNotBlank() },
        showStatus = showStatusLabel,
        plot = plot?.trim().orEmpty(),
        tags = tags.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        actors = actorItems,
        recommendations = recs,
        comingSoon = comingSoon,
        seasons = unifiedSeasons,
        hasAnySub = hasAnySub,
        hasAnyDub = hasAnyDub,
        dubStatusMap = dubMap,
        defaultDubKey = dubMap.keys.firstOrNull() ?: "Default"
    )
}

/**
 * Deduplicates episodes strictly by normalized data ID (and same season+episode number)
 * so duplicate episodes from extensions NEVER appear twice.
 */
private fun deduplicateRawEpisodes(episodes: List<Episode>): List<Episode> {
    if (episodes.size <= 1) return episodes

    // Step 1: Deduplicate identical episode data/IDs, keeping the richer metadata copy
    val byId = linkedMapOf<String, Episode>()
    for (ep in episodes) {
        val key = ep.data.trim().ifBlank { "noid_${ep.season}_${ep.episode}_${ep.name}" }
        val existing = byId[key]
        if (existing == null || episodeRichnessScore(ep) > episodeRichnessScore(existing)) {
            byId[key] = ep
        }
    }

    // Step 2: Deduplicate identical explicit (season, episode) numbers within the same list
    val result = mutableListOf<Episode>()
    val seenSeasonEpIndex = mutableMapOf<Pair<Int, Int>, Int>()
    for (ep in byId.values) {
        val epNum = ep.episode
        if (epNum != null && epNum > 0) {
            val pair = (ep.season ?: 1) to epNum
            val existingIdx = seenSeasonEpIndex[pair]
            if (existingIdx == null) {
                seenSeasonEpIndex[pair] = result.size
                result.add(ep)
            } else if (episodeRichnessScore(ep) > episodeRichnessScore(result[existingIdx])) {
                result[existingIdx] = ep
            }
        } else {
            result.add(ep)
        }
    }
    return result
}

private fun episodeRichnessScore(ep: Episode): Int {
    var score = 0
    if (!ep.posterUrl.isNullOrBlank()) score += 3
    if (!ep.description.isNullOrBlank()) score += 2
    if (!ep.name.isNullOrBlank() && !ep.name!!.startsWith("Episode", ignoreCase = true)) score += 2
    if (ep.runTime != null && ep.runTime!! > 0) score += 1
    if (ep.date != null && ep.date!! > 0L) score += 1
    return score
}

private fun groupEpisodesIntoSeasons(
    episodes: List<Episode>,
    seasonNameMap: Map<Int, String>
): List<MetaSeasonGroup> {
    val cleanEps = deduplicateRawEpisodes(episodes)
    if (cleanEps.isEmpty()) return emptyList()
    val grouped = cleanEps.groupBy { it.season ?: 1 }
    return grouped.entries
        .sortedBy { it.key }
        .map { (seasonNo, epList) ->
            val sortedEps = epList.sortedWith(compareBy<Episode> { it.episode ?: Int.MAX_VALUE })
                .mapIndexed { idx, ep ->
                    val epNum = ep.episode ?: (idx + 1)
                    val epTitle = ep.name?.trim()?.takeIf { it.isNotBlank() } ?: "Episode $epNum"
                    MetaEpisodeItem(
                        number = epNum,
                        season = ep.season ?: seasonNo,
                        title = epTitle,
                        data = ep.data,
                        posterUrl = ep.posterUrl?.takeIf { it.isNotBlank() },
                        description = ep.description?.trim()?.takeIf { it.isNotBlank() },
                        score = runCatching { ep.score?.toStringNull(0.1, 10, 1) }.getOrNull(),
                        runTime = ep.runTime,
                        airDateMs = ep.date
                    )
                }
            MetaSeasonGroup(
                seasonNumber = seasonNo,
                displayName = seasonNameMap[seasonNo] ?: "Season $seasonNo",
                episodes = sortedEps
            )
        }
}

/**
 * Merges AnimeLoadResponse Subbed / Dubbed / None tracks into ONE unified list per season,
 * deduplicating duplicate episode IDs and attaching per-episode hasSub/hasDub + subData/dubData.
 */
private fun mergeAnimeEpisodesIntoSeasons(
    episodesMap: Map<DubStatus, List<Episode>>,
    seasonNameMap: Map<Int, String>
): List<MetaSeasonGroup> {
    val subList = deduplicateRawEpisodes(episodesMap[DubStatus.Subbed].orEmpty())
    val dubList = deduplicateRawEpisodes(episodesMap[DubStatus.Dubbed].orEmpty())
    val noneList = deduplicateRawEpisodes(episodesMap[DubStatus.None].orEmpty())

    if (subList.isEmpty() && dubList.isEmpty() && noneList.isEmpty()) return emptyList()

    // If only DubStatus.None exists, behave like standard episodes (no sub/dub badges)
    if (subList.isEmpty() && dubList.isEmpty()) {
        return groupEpisodesIntoSeasons(noneList, seasonNameMap)
    }

    data class AccEpisode(
        val seasonNo: Int,
        val epNum: Int,
        var title: String,
        var primaryData: String,
        var posterUrl: String?,
        var description: String?,
        var score: String?,
        var runTime: Int?,
        var airDateMs: Long?,
        var hasSub: Boolean = false,
        var hasDub: Boolean = false,
        var subData: String? = null,
        var dubData: String? = null
    )

    val mergedByKey = linkedMapOf<Pair<Int, Int>, AccEpisode>()

    fun absorb(list: List<Episode>, isSub: Boolean, isDub: Boolean) {
        val groupedBySeason = list.groupBy { it.season ?: 1 }
        groupedBySeason.entries.sortedBy { it.key }.forEach { (seasonNo, rawSeasonEps) ->
            val sorted = rawSeasonEps.sortedWith(compareBy<Episode> { it.episode ?: Int.MAX_VALUE })
            sorted.forEachIndexed { idx, ep ->
                val epNum = ep.episode ?: (idx + 1)
                val key = seasonNo to epNum
                val epTitle = ep.name?.trim()?.takeIf { it.isNotBlank() } ?: "Episode $epNum"
                val epPoster = ep.posterUrl?.takeIf { it.isNotBlank() }
                val epDesc = ep.description?.trim()?.takeIf { it.isNotBlank() }
                val epScore = runCatching { ep.score?.toStringNull(0.1, 10, 1) }.getOrNull()

                val existing = mergedByKey[key]
                if (existing == null) {
                    mergedByKey[key] = AccEpisode(
                        seasonNo = seasonNo,
                        epNum = epNum,
                        title = epTitle,
                        primaryData = ep.data,
                        posterUrl = epPoster,
                        description = epDesc,
                        score = epScore,
                        runTime = ep.runTime,
                        airDateMs = ep.date,
                        hasSub = isSub,
                        hasDub = isDub,
                        subData = if (isSub) ep.data else null,
                        dubData = if (isDub) ep.data else null
                    )
                } else {
                    if (isSub) {
                        existing.hasSub = true
                        if (existing.subData.isNullOrBlank()) existing.subData = ep.data
                    }
                    if (isDub) {
                        existing.hasDub = true
                        if (existing.dubData.isNullOrBlank()) existing.dubData = ep.data
                    }
                    if (existing.posterUrl.isNullOrBlank() && epPoster != null) existing.posterUrl = epPoster
                    if (existing.description.isNullOrBlank() && epDesc != null) existing.description = epDesc
                    if (existing.score.isNullOrBlank() && epScore != null) existing.score = epScore
                    if ((existing.runTime == null || existing.runTime == 0) && ep.runTime != null) existing.runTime = ep.runTime
                    if ((existing.airDateMs == null || existing.airDateMs == 0L) && ep.date != null) existing.airDateMs = ep.date
                    if (existing.title.startsWith("Episode", ignoreCase = true) && !epTitle.startsWith("Episode", ignoreCase = true)) {
                        existing.title = epTitle
                    }
                }
            }
        }
    }

    absorb(subList, isSub = true, isDub = false)
    absorb(dubList, isSub = false, isDub = true)
    absorb(noneList, isSub = false, isDub = false)

    return mergedByKey.values
        .groupBy { it.seasonNo }
        .entries
        .sortedBy { it.key }
        .map { (seasonNo, accList) ->
            val items = accList.sortedBy { it.epNum }.map { acc ->
                MetaEpisodeItem(
                    number = acc.epNum,
                    season = acc.seasonNo,
                    title = acc.title,
                    data = acc.subData ?: acc.dubData ?: acc.primaryData,
                    posterUrl = acc.posterUrl,
                    description = acc.description,
                    score = acc.score,
                    runTime = acc.runTime,
                    airDateMs = acc.airDateMs,
                    hasSub = acc.hasSub,
                    hasDub = acc.hasDub,
                    subData = acc.subData,
                    dubData = acc.dubData
                )
            }
            MetaSeasonGroup(
                seasonNumber = seasonNo,
                displayName = seasonNameMap[seasonNo] ?: "Season $seasonNo",
                episodes = items
            )
        }
}

fun formatTvType(type: TvType): String = when (type) {
    TvType.TvSeries -> "Series"
    TvType.Movie -> "Movie"
    TvType.Anime -> "Anime"
    TvType.AnimeMovie -> "Anime Movie"
    TvType.AsianDrama -> "Drama"
    TvType.Cartoon -> "Cartoon"
    TvType.Documentary -> "Doc"
    TvType.Live -> "Live"
    TvType.OVA -> "OVA"
    else -> type.name
}

fun formatShortTvType(type: TvType): String = when (type) {
    TvType.TvSeries -> "SERIES"
    TvType.Movie -> "MOVIE"
    TvType.Anime -> "ANIME"
    TvType.AnimeMovie -> "MOVIE"
    TvType.AsianDrama -> "DRAMA"
    TvType.Cartoon -> "TOON"
    TvType.Documentary -> "DOC"
    TvType.Live -> "LIVE"
    TvType.OVA -> "OVA"
    else -> type.name.uppercase().take(6)
}
