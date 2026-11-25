package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.AuthToken
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.Date

class AniListApi : SyncAPI() {
    override var name = "AniList"
    override val idPrefix = "anilist"
    override val mainUrl = "https://anilist.co"
    override val syncIdName = SyncIdName.Anilist
    override val requiresLogin = false
    override val createAccountUrl = "$mainUrl/signup"
    override val redirectUrlIdentifier = "anilistlogin"
    override val hasOAuth2 = true

    override fun urlToId(url: String): String? =
        Regex("anilist\\.co/anime/(\\d+)").find(url)?.groupValues?.getOrNull(1)

    override suspend fun search(auth: AuthData?, query: String): List<SyncAPI.SyncSearchResult>? {
        val data = searchShows(query) ?: return null
        return data.data?.page?.media?.map {
            SyncAPI.SyncSearchResult(
                name = it.title.romaji ?: "",
                apiName = this.name,
                syncId = it.id.toString(),
                url = "$mainUrl/anime/${it.id}/",
                posterUrl = it.bannerImage
            )
        }
    }

    suspend fun getAllSeasons(id: Int): List<SeasonResponse?> {
        val seasons = mutableListOf<SeasonResponse?>()
        suspend fun getSeasonRecursive(curId: Int) {
            val season = runCatching { getSeason(curId) }.getOrNull() ?: return
            seasons.add(season)
            if (season.data.media.format?.startsWith("TV") == true) {
                season.data.media.relations?.edges?.forEach {
                    if (it.relationType == "SEQUEL" && it.node?.format?.startsWith("TV") == true) {
                        getSeasonRecursive(it.node.id)
                        return@forEach
                    }
                }
            }
        }
        getSeasonRecursive(id)
        return seasons.toList()
    }

    companion object {
        const val MAX_STALE = 60 * 10
        const val ANILIST_CACHED_LIST: String = "anilist_cached_list"

        private fun Int.toYear(): Date? = runCatching {
            Calendar.getInstance().apply { set(Calendar.YEAR, this@toYear) }.time
        }.getOrNull()

        enum class AniListStatusType(var value: Int, val stringRes: Int = 0) {
            Watching(0),
            Completed(1),
            Paused(2),
            Dropped(3),
            Planning(4),
            ReWatching(5),
            None(-1)
        }

        suspend fun searchShows(name: String): GetSearchRoot? {
            return runCatching {
                val query = """
                    query (${'$'}search: String, ${'$'}page: Int, ${'$'}type: MediaType) {
                        Page(page: ${'$'}page, perPage: 20) {
                            media(search: ${'$'}search, type: ${'$'}type) {
                                id idMal seasonYear averageScore meanScore bannerImage
                                title { romaji }
                                startDate { year month day }
                                nextAiringEpisode { episode timeUntilAiring }
                            }
                        }
                    }
                """.trimIndent()
                val body = mapOf(
                    "query" to query,
                    "variables" to Variables(name, 1, "ANIME")
                ).toJson()
                val resp = app.post(
                    "https://graphql.anilist.co/",
                    headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json"),
                    data = mapOf("query" to query, "variables" to Variables(name, 1, "ANIME").toJson())
                ).text
                parseJson<GetSearchRoot>(resp)
            }.getOrNull()
        }

        suspend fun getSeason(id: Int): SeasonResponse {
            val q = """
                query {
                    Media(id: $id, type: ANIME) {
                        id idMal format episodes duration averageScore isAdult description genres synonyms
                        title { romaji english native userPreferred }
                        coverImage { extraLarge large medium color }
                        nextAiringEpisode { episode timeUntilAiring }
                    }
                }
            """.trimIndent()
            val resp = app.post(
                "https://graphql.anilist.co/",
                data = mapOf("query" to q)
            ).text
            return parseJson(resp)
        }
    }

    @Serializable
    data class Variables(
        @JsonProperty("search") @SerialName("search") val search: String,
        @JsonProperty("page") @SerialName("page") val page: Int,
        @JsonProperty("type") @SerialName("type") val type: String,
    )

    @Serializable
    data class MediaRecommendation(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("title") @SerialName("title") val title: Title? = null,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int? = null,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: CoverImage? = null,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int? = null,
    )

    @Serializable
    data class FullAnilistList(
        @JsonProperty("data") @SerialName("data") val data: Data? = null,
    )

    @Serializable
    data class CompletedAt(
        @JsonProperty("year") @SerialName("year") val year: Int? = null,
        @JsonProperty("month") @SerialName("month") val month: Int? = null,
        @JsonProperty("day") @SerialName("day") val day: Int? = null,
    )

    @Serializable
    data class StartedAt(
        @JsonProperty("year") @SerialName("year") val year: Int? = null,
        @JsonProperty("month") @SerialName("month") val month: Int? = null,
        @JsonProperty("day") @SerialName("day") val day: Int? = null,
    )

    @Serializable
    data class Title(
        @JsonProperty("english") @SerialName("english") val english: String? = null,
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String? = null,
    )

    @Serializable
    data class CoverImage(
        @JsonProperty("medium") @SerialName("medium") val medium: String? = null,
        @JsonProperty("large") @SerialName("large") val large: String? = null,
        @JsonProperty("extraLarge") @SerialName("extraLarge") val extraLarge: String? = null,
    )

    @Serializable
    data class Media(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int? = null,
        @JsonProperty("season") @SerialName("season") val season: String? = null,
        @JsonProperty("seasonYear") @SerialName("seasonYear") val seasonYear: Int? = null,
        @JsonProperty("format") @SerialName("format") val format: String? = null,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: Title,
        @JsonProperty("description") @SerialName("description") val description: String? = null,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: CoverImage,
        @JsonProperty("synonyms") @SerialName("synonyms") val synonyms: List<String> = emptyList(),
        @JsonProperty("nextAiringEpisode") @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode? = null,
    )

    @Serializable
    data class Entries(
        @JsonProperty("status") @SerialName("status") val status: String? = null,
        @JsonProperty("completedAt") @SerialName("completedAt") val completedAt: CompletedAt? = null,
        @JsonProperty("startedAt") @SerialName("startedAt") val startedAt: StartedAt? = null,
        @JsonProperty("updatedAt") @SerialName("updatedAt") val updatedAt: Int? = null,
        @JsonProperty("progress") @SerialName("progress") val progress: Int = 0,
        @JsonProperty("score") @SerialName("score") val score: Int = 0,
        @JsonProperty("private") @SerialName("private") val private: Boolean = false,
        @JsonProperty("media") @SerialName("media") val media: Media,
    ) {
        fun toLibraryItem(): SyncAPI.LibraryItem {
            return SyncAPI.LibraryItem(
                this.media.title.english ?: this.media.title.romaji ?: this.media.synonyms.firstOrNull() ?: "",
                "https://anilist.co/anime/${this.media.id}/",
                this.media.id.toString(),
                this.progress,
                this.media.episodes,
                Score.from100(this.score),
                this.updatedAt?.toLong(),
                "AniList",
                TvType.Anime,
                this.media.coverImage.extraLarge ?: this.media.coverImage.large ?: this.media.coverImage.medium,
                null,
                null,
                this.media.seasonYear?.toYear(),
                null,
                plot = this.media.description,
            )
        }
    }

    @Serializable
    data class Lists(
        @JsonProperty("status") @SerialName("status") val status: String? = null,
        @JsonProperty("entries") @SerialName("entries") val entries: List<Entries> = emptyList(),
    )

    @Serializable
    data class MediaListCollection(
        @JsonProperty("lists") @SerialName("lists") val lists: List<Lists> = emptyList(),
    )

    @Serializable
    data class Data(
        @JsonProperty("MediaListCollection") @SerialName("MediaListCollection") val mediaListCollection: MediaListCollection,
    )

    @Serializable
    data class MediaListItemRoot(
        @JsonProperty("data") @SerialName("data") val data: MediaListItem? = null,
    )

    @Serializable
    data class MediaListItem(
        @JsonProperty("SaveMediaListEntry") @SerialName("SaveMediaListEntry") val saveMediaListEntry: MediaListId? = null,
    )

    @Serializable
    data class MediaListId(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
    )

    @Serializable
    data class GraphQLError(
        @JsonProperty("message") @SerialName("message") val message: String? = null,
        @JsonProperty("status") @SerialName("status") val status: Int? = null,
    )

    @Serializable
    data class GraphQLErrorRoot(
        @JsonProperty("errors") @SerialName("errors") val errors: List<GraphQLError>? = null,
    )

    @Serializable
    data class SeasonResponse(
        @JsonProperty("data") @SerialName("data") val data: SeasonData,
    )

    @Serializable
    data class SeasonData(
        @JsonProperty("Media") @SerialName("Media") val media: SeasonMedia,
    )

    @Serializable
    data class RecommendedMedia(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: MediaTitle? = null,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: MediaCoverImage? = null,
    )

    @Serializable
    data class CharacterMedia(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: MediaTitle? = null,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: MediaCoverImage? = null,
    )

    @Serializable
    data class SeasonMedia(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: MediaTitle? = null,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int? = null,
        @JsonProperty("format") @SerialName("format") val format: String? = null,
        @JsonProperty("nextAiringEpisode") @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode? = null,
        @JsonProperty("relations") @SerialName("relations") val relations: SeasonEdges? = null,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: MediaCoverImage? = null,
        @JsonProperty("duration") @SerialName("duration") val duration: Int? = null,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int? = null,
        @JsonProperty("genres") @SerialName("genres") val genres: List<String>? = null,
        @JsonProperty("synonyms") @SerialName("synonyms") val synonyms: List<String>? = null,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int? = null,
        @JsonProperty("isAdult") @SerialName("isAdult") val isAdult: Boolean? = null,
        @JsonProperty("trailer") @SerialName("trailer") val trailer: MediaTrailer? = null,
        @JsonProperty("description") @SerialName("description") val description: String? = null,
        @JsonProperty("characters") @SerialName("characters") val characters: CharacterConnection? = null,
        @JsonProperty("recommendations") @SerialName("recommendations") val recommendations: RecommendationConnection? = null,
    )

    @Serializable
    data class RecommendationConnection(
        @JsonProperty("edges") @SerialName("edges") val edges: List<RecommendationEdge> = emptyList(),
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<Recommendation> = emptyList(),
    )

    @Serializable
    data class RecommendationEdge(
        @JsonProperty("node") @SerialName("node") val node: Recommendation,
    )

    @Serializable
    data class Recommendation(
        @JsonProperty("mediaRecommendation") @SerialName("mediaRecommendation") val mediaRecommendation: RecommendedMedia? = null,
    )

    @Serializable
    data class CharacterName(
        @JsonProperty("name") @SerialName("name") val first: String? = null,
        @JsonProperty("middle") @SerialName("middle") val middle: String? = null,
        @JsonProperty("last") @SerialName("last") val last: String? = null,
        @JsonProperty("full") @SerialName("full") val full: String? = null,
        @JsonProperty("native") @SerialName("native") val native: String? = null,
        @JsonProperty("alternative") @SerialName("alternative") val alternative: List<String>? = null,
        @JsonProperty("alternativeSpoiler") @SerialName("alternativeSpoiler") val alternativeSpoiler: List<String>? = null,
        @JsonProperty("userPreferred") @SerialName("userPreferred") val userPreferred: String? = null,
    )

    @Serializable
    data class CharacterImage(
        @JsonProperty("large") @SerialName("large") val large: String? = null,
        @JsonProperty("medium") @SerialName("medium") val medium: String? = null,
    )

    @Serializable
    data class Character(
        @JsonProperty("name") @SerialName("name") val name: CharacterName? = null,
        @JsonProperty("age") @SerialName("age") val age: String? = null,
        @JsonProperty("image") @SerialName("image") val image: CharacterImage? = null,
    )

    @Serializable
    data class CharacterEdge(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("role") @SerialName("role") val role: String? = null,
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("voiceActors") @SerialName("voiceActors") val voiceActors: List<Staff>? = null,
        @JsonProperty("favouriteOrder") @SerialName("favouriteOrder") val favouriteOrder: Int? = null,
        @JsonProperty("media") @SerialName("media") val media: List<CharacterMedia>? = null,
        @JsonProperty("node") @SerialName("node") val node: Character? = null,
    )

    @Serializable
    data class StaffImage(
        @JsonProperty("large") @SerialName("large") val large: String? = null,
        @JsonProperty("medium") @SerialName("medium") val medium: String? = null,
    )

    @Serializable
    data class StaffName(
        @JsonProperty("name") @SerialName("name") val first: String? = null,
        @JsonProperty("middle") @SerialName("middle") val middle: String? = null,
        @JsonProperty("last") @SerialName("last") val last: String? = null,
        @JsonProperty("full") @SerialName("full") val full: String? = null,
        @JsonProperty("native") @SerialName("native") val native: String? = null,
        @JsonProperty("alternative") @SerialName("alternative") val alternative: List<String>? = null,
        @JsonProperty("userPreferred") @SerialName("userPreferred") val userPreferred: String? = null,
    )

    @Serializable
    data class Staff(
        @JsonProperty("image") @SerialName("image") val image: StaffImage? = null,
        @JsonProperty("name") @SerialName("name") val name: StaffName? = null,
        @JsonProperty("age") @SerialName("age") val age: Int? = null,
    )

    @Serializable
    data class CharacterConnection(
        @JsonProperty("edges") @SerialName("edges") val edges: List<CharacterEdge>? = null,
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<Character>? = null,
    )

    @Serializable
    data class MediaTrailer(
        @JsonProperty("id") @SerialName("id") val id: String? = null,
        @JsonProperty("site") @SerialName("site") val site: String? = null,
        @JsonProperty("thumbnail") @SerialName("thumbnail") val thumbnail: String? = null,
    )

    @Serializable
    data class MediaCoverImage(
        @JsonProperty("extraLarge") @SerialName("extraLarge") val extraLarge: String? = null,
        @JsonProperty("large") @SerialName("large") val large: String? = null,
        @JsonProperty("medium") @SerialName("medium") val medium: String? = null,
        @JsonProperty("color") @SerialName("color") val color: String? = null,
    )

    @Serializable
    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") @SerialName("episode") val episode: Int? = null,
        @JsonProperty("timeUntilAiring") @SerialName("timeUntilAiring") val timeUntilAiring: Int? = null,
    )

    @Serializable
    data class SeasonEdges(
        @JsonProperty("edges") @SerialName("edges") val edges: List<SeasonEdge>? = null,
    )

    @Serializable
    data class SeasonEdge(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("relationType") @SerialName("relationType") val relationType: String? = null,
        @JsonProperty("node") @SerialName("node") val node: SeasonNode? = null,
    )

    @Serializable
    data class AniListFavoritesMediaConnection(
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<LikeNode> = emptyList(),
    )

    @Serializable
    data class AniListFavourites(
        @JsonProperty("anime") @SerialName("anime") val anime: AniListFavoritesMediaConnection,
    )

    @Serializable
    data class MediaTitle(
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String? = null,
        @JsonProperty("english") @SerialName("english") val english: String? = null,
        @JsonProperty("native") @SerialName("native") val native: String? = null,
        @JsonProperty("userPreferred") @SerialName("userPreferred") val userPreferred: String? = null,
    )

    @Serializable
    data class SeasonNode(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("format") @SerialName("format") val format: String? = null,
        @JsonProperty("title") @SerialName("title") val title: Title? = null,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int? = null,
        @JsonProperty("coverImage") @SerialName("coverImage") val coverImage: CoverImage? = null,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int? = null,
    )

    @Serializable
    data class AniListAvatar(
        @JsonProperty("large") @SerialName("large") val large: String? = null,
    )

    @Serializable
    data class AniListViewer(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("avatar") @SerialName("avatar") val avatar: AniListAvatar? = null,
        @JsonProperty("favourites") @SerialName("favourites") val favourites: AniListFavourites? = null,
    )

    @Serializable
    data class AniListData(
        @JsonProperty("Viewer") @SerialName("Viewer") val viewer: AniListViewer? = null,
    )

    @Serializable
    data class AniListRoot(
        @JsonProperty("data") @SerialName("data") val data: AniListData? = null,
    )

    @Serializable
    data class AniListUser(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("picture") @SerialName("picture") val picture: String? = null,
    )

    @Serializable
    data class LikeNode(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
    )

    @Serializable
    data class LikePageInfo(
        @JsonProperty("total") @SerialName("total") val total: Int? = null,
        @JsonProperty("currentPage") @SerialName("currentPage") val currentPage: Int? = null,
        @JsonProperty("lastPage") @SerialName("lastPage") val lastPage: Int? = null,
        @JsonProperty("perPage") @SerialName("perPage") val perPage: Int? = null,
        @JsonProperty("hasNextPage") @SerialName("hasNextPage") val hasNextPage: Boolean? = null,
    )

    @Serializable
    data class LikeAnime(
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<LikeNode>? = null,
        @JsonProperty("pageInfo") @SerialName("pageInfo") val pageInfo: LikePageInfo? = null,
    )

    @Serializable
    data class LikeFavourites(
        @JsonProperty("anime") @SerialName("anime") val anime: LikeAnime? = null,
    )

    @Serializable
    data class LikeViewer(
        @JsonProperty("favourites") @SerialName("favourites") val favourites: LikeFavourites? = null,
    )

    @Serializable
    data class LikeData(
        @JsonProperty("Viewer") @SerialName("Viewer") val viewer: LikeViewer? = null,
    )

    @Serializable
    data class LikeRoot(
        @JsonProperty("data") @SerialName("data") val data: LikeData? = null,
    )

    @Serializable
    data class AniListTitleHolder(
        @JsonProperty("title") @SerialName("title") val title: Title? = null,
        @JsonProperty("isFavourite") @SerialName("isFavourite") val isFavourite: Boolean? = null,
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("progress") @SerialName("progress") val progress: Int? = null,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int? = null,
        @JsonProperty("score") @SerialName("score") val score: Int? = null,
        @JsonProperty("type") @SerialName("type") val type: AniListStatusType? = null,
    )

    @Serializable
    data class GetDataMediaListEntry(
        @JsonProperty("progress") @SerialName("progress") val progress: Int? = null,
        @JsonProperty("status") @SerialName("status") val status: String? = null,
        @JsonProperty("score") @SerialName("score") val score: Int? = null,
    )

    @Serializable
    data class Nodes(
        @JsonProperty("id") @SerialName("id") val id: Int? = null,
        @JsonProperty("mediaRecommendation") @SerialName("mediaRecommendation") val mediaRecommendation: MediaRecommendation? = null,
    )

    @Serializable
    data class GetDataMedia(
        @JsonProperty("isFavourite") @SerialName("isFavourite") val isFavourite: Boolean? = null,
        @JsonProperty("episodes") @SerialName("episodes") val episodes: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: Title? = null,
        @JsonProperty("mediaListEntry") @SerialName("mediaListEntry") val mediaListEntry: GetDataMediaListEntry? = null,
    )

    @Serializable
    data class Recommendations(
        @JsonProperty("nodes") @SerialName("nodes") val nodes: List<Nodes>? = null,
    )

    @Serializable
    data class GetDataData(
        @JsonProperty("Media") @SerialName("Media") val media: GetDataMedia? = null,
    )

    @Serializable
    data class GetDataRoot(
        @JsonProperty("data") @SerialName("data") val data: GetDataData? = null,
    )

    @Serializable
    data class GetSearchTitle(
        @JsonProperty("romaji") @SerialName("romaji") val romaji: String? = null,
    )

    @Serializable
    data class TrailerObject(
        @JsonProperty("id") @SerialName("id") val id: String? = null,
        @JsonProperty("thumbnail") @SerialName("thumbnail") val thumbnail: String? = null,
        @JsonProperty("site") @SerialName("site") val site: String? = null,
    )

    @Serializable
    data class GetSearchMedia(
        @JsonProperty("id") @SerialName("id") val id: Int,
        @JsonProperty("idMal") @SerialName("idMal") val idMal: Int? = null,
        @JsonProperty("seasonYear") @SerialName("seasonYear") val seasonYear: Int? = null,
        @JsonProperty("title") @SerialName("title") val title: GetSearchTitle,
        @JsonProperty("startDate") @SerialName("startDate") val startDate: StartedAt? = null,
        @JsonProperty("averageScore") @SerialName("averageScore") val averageScore: Int? = null,
        @JsonProperty("meanScore") @SerialName("meanScore") val meanScore: Int? = null,
        @JsonProperty("bannerImage") @SerialName("bannerImage") val bannerImage: String? = null,
        @JsonProperty("trailer") @SerialName("trailer") val trailer: TrailerObject? = null,
        @JsonProperty("nextAiringEpisode") @SerialName("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode? = null,
        @JsonProperty("recommendations") @SerialName("recommendations") val recommendations: Recommendations? = null,
        @JsonProperty("relations") @SerialName("relations") val relations: SeasonEdges? = null,
    )

    @Serializable
    data class GetSearchPage(
        @JsonProperty("Page") @SerialName("Page") val page: GetSearchData? = null,
    )

    @Serializable
    data class GetSearchData(
        @JsonProperty("media") @SerialName("media") val media: List<GetSearchMedia>? = null,
    )

    @Serializable
    data class GetSearchRoot(
        @JsonProperty("data") @SerialName("data") val data: GetSearchPage? = null,
    )
}
