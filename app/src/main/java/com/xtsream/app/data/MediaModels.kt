package com.xtsream.app.data

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType

data class MediaCardItem(
    val title: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
    val posterHeaders: Map<String, String> = emptyMap(),
    val type: String = "",
    val quality: String? = null,
    val score: String? = null,
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
    val airDateMs: Long?
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
    val typeLabel: String,
    val isMovieLike: Boolean,
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
    val dubStatusMap: Map<String, List<MetaSeasonGroup>>,
    val defaultDubKey: String
)

fun SearchResponse.toMediaCardItem(isHorizontal: Boolean = false): MediaCardItem {
    val formattedScore = runCatching { score?.toStringNull(0.1, 10, 1) }.getOrNull()
    return MediaCardItem(
        title = name.trim(),
        url = url,
        apiName = apiName,
        posterUrl = posterUrl,
        posterHeaders = posterHeaders ?: emptyMap(),
        type = type?.name ?: "",
        quality = quality?.name,
        score = formattedScore,
        isHorizontal = isHorizontal
    )
}

fun LoadResponse.toMetaUiData(): MetaUiData {
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

    val recs = recommendations.orEmpty().map { it.toMediaCardItem() }
    val dubMap = linkedMapOf<String, List<MetaSeasonGroup>>()
    var showStatusLabel: String? = null

    when (this) {
        is TvSeriesLoadResponse -> {
            showStatusLabel = showStatus?.name
            val seasonNameMap = seasonNames.orEmpty().associate { it.season to (it.name ?: "Season ${it.displaySeason ?: it.season}") }
            val grouped = groupEpisodesIntoSeasons(episodes, seasonNameMap)
            if (grouped.isNotEmpty()) {
                dubMap["Default"] = grouped
            }
        }
        is AnimeLoadResponse -> {
            showStatusLabel = showStatus?.name
            val seasonNameMap = seasonNames.orEmpty().associate { it.season to (it.name ?: "Season ${it.displaySeason ?: it.season}") }
            episodes.forEach { (dubStatus, epList) ->
                if (epList.isNotEmpty()) {
                    val label = when (dubStatus) {
                        DubStatus.Subbed -> "Sub"
                        DubStatus.Dubbed -> "Dub"
                        DubStatus.None -> "Episodes"
                    }
                    val grouped = groupEpisodesIntoSeasons(epList, seasonNameMap)
                    if (grouped.isNotEmpty()) {
                        dubMap[label] = grouped
                    }
                }
            }
        }
    }

    val isMovieLike = dubMap.isEmpty() && (type == TvType.Movie || type == TvType.AnimeMovie || type == TvType.OVA || type == TvType.Documentary || type == TvType.Live)

    return MetaUiData(
        title = name.trim(),
        url = url,
        apiName = apiName,
        typeLabel = formatTvType(type),
        isMovieLike = isMovieLike,
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
        dubStatusMap = dubMap,
        defaultDubKey = dubMap.keys.firstOrNull() ?: "Default"
    )
}

private fun groupEpisodesIntoSeasons(
    episodes: List<Episode>,
    seasonNameMap: Map<Int, String>
): List<MetaSeasonGroup> {
    if (episodes.isEmpty()) return emptyList()
    val grouped = episodes.groupBy { it.season ?: 1 }
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

fun formatTvType(type: TvType): String = when (type) {
    TvType.TvSeries -> "Series"
    TvType.Movie -> "Movie"
    TvType.Anime -> "Anime"
    TvType.AnimeMovie -> "Anime Movie"
    TvType.AsianDrama -> "Asian Drama"
    TvType.Cartoon -> "Cartoon"
    TvType.Documentary -> "Documentary"
    TvType.Live -> "Live"
    TvType.OVA -> "OVA"
    else -> type.name
}
