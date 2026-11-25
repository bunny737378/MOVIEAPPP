package com.xtsream.app.data

import com.lagradost.cloudstream3.app
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TmdbSuggestion(
    val title: String,
    val mediaType: String, // "movie" or "tv"
    val year: String? = null
)

/**
 * Lightweight TMDB /search/multi suggestion client (Hikari-style)
 * used exclusively when the user searches with "All" providers selected.
 */
object TmdbSuggestionsApi {
    private val apiKeys = listOf(
        "68e094699525b18a70bab2f86b1fa706",
        "439c478a771f35c05022f9feabcca01c"
    )
    private val cache = ConcurrentHashMap<String, List<TmdbSuggestion>>()

    suspend fun suggest(rawQuery: String, limit: Int = 8): List<TmdbSuggestion> {
        val query = rawQuery.trim()
        if (query.length < 2) return emptyList()

        val cacheKey = query.lowercase()
        cache[cacheKey]?.let { return it.take(limit) }

        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrNull() ?: return emptyList()

        for (key in apiKeys) {
            val result = runCatching {
                val url = "https://api.themoviedb.org/3/search/multi?api_key=$key&query=$encoded&include_adult=false&page=1"
                val text = app.get(
                    url = url,
                    timeout = 8L,
                    cacheTime = 15,
                    cacheUnit = TimeUnit.MINUTES
                ).text
                parseSuggestions(text, limit)
            }.getOrNull()

            if (result != null) {
                if (cache.size > 120) cache.clear()
                cache[cacheKey] = result
                return result
            }
        }
        return emptyList()
    }

    private fun parseSuggestions(jsonText: String, limit: Int): List<TmdbSuggestion> {
        val root = JSONObject(jsonText)
        val arr = root.optJSONArray("results") ?: return emptyList()

        data class Scored(val item: TmdbSuggestion, val popularity: Double)

        val seen = HashSet<String>()
        val collected = mutableListOf<Scored>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val mediaType = obj.optString("media_type", "")
            if (mediaType != "movie" && mediaType != "tv") continue

            val title = when (mediaType) {
                "movie" -> obj.optString("title", "").ifBlank { obj.optString("original_title", "") }
                else -> obj.optString("name", "").ifBlank { obj.optString("original_name", "") }
            }.trim()

            if (title.isBlank()) continue
            val dedupKey = "${title.lowercase()}|$mediaType"
            if (!seen.add(dedupKey)) continue

            val rawDate = when (mediaType) {
                "movie" -> obj.optString("release_date", "")
                else -> obj.optString("first_air_date", "")
            }
            val year = rawDate.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }
            val pop = obj.optDouble("popularity", 0.0)

            collected.add(
                Scored(
                    item = TmdbSuggestion(title = title, mediaType = mediaType, year = year),
                    popularity = pop
                )
            )
        }

        return collected
            .sortedByDescending { it.popularity }
            .take(limit)
            .map { it.item }
    }
}
