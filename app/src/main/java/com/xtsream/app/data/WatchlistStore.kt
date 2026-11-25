package com.xtsream.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object WatchlistStore {
    private const val PREFS_NAME = "movieappp_library_prefs"
    private const val KEY_WATCHLIST = "watchlist_items"
    private const val KEY_RECENT_VIEWED = "recent_viewed_items"
    private const val KEY_RECENT_SEARCHES = "recent_searches"

    private var prefs: SharedPreferences? = null

    private val _watchlist = MutableStateFlow<List<MediaCardItem>>(emptyList())
    val watchlist: StateFlow<List<MediaCardItem>> = _watchlist.asStateFlow()

    private val _recentlyViewed = MutableStateFlow<List<MediaCardItem>>(emptyList())
    val recentlyViewed: StateFlow<List<MediaCardItem>> = _recentlyViewed.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _watchlist.value = loadMediaList(KEY_WATCHLIST)
        _recentlyViewed.value = loadMediaList(KEY_RECENT_VIEWED)
        _recentSearches.value = loadStringList(KEY_RECENT_SEARCHES)
    }

    fun isInWatchlist(url: String): Boolean = _watchlist.value.any { it.url == url }

    fun toggleWatchlist(item: MediaCardItem): Boolean {
        val current = _watchlist.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.url == item.url }
        val added: Boolean
        if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            added = false
        } else {
            current.add(0, item)
            added = true
        }
        _watchlist.value = current
        saveMediaList(KEY_WATCHLIST, current)
        return added
    }

    fun removeFromWatchlist(url: String) {
        val updated = _watchlist.value.filterNot { it.url == url }
        _watchlist.value = updated
        saveMediaList(KEY_WATCHLIST, updated)
    }

    fun recordViewed(item: MediaCardItem) {
        val current = _recentlyViewed.value.filterNot { it.url == item.url }.toMutableList()
        current.add(0, item)
        val trimmed = current.take(30)
        _recentlyViewed.value = trimmed
        saveMediaList(KEY_RECENT_VIEWED, trimmed)
    }

    fun clearRecentlyViewed() {
        _recentlyViewed.value = emptyList()
        saveMediaList(KEY_RECENT_VIEWED, emptyList())
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _recentSearches.value.filterNot { it.equals(trimmed, ignoreCase = true) }.toMutableList()
        current.add(0, trimmed)
        val updated = current.take(15)
        _recentSearches.value = updated
        saveStringList(KEY_RECENT_SEARCHES, updated)
    }

    fun removeRecentSearch(query: String) {
        val updated = _recentSearches.value.filterNot { it == query }
        _recentSearches.value = updated
        saveStringList(KEY_RECENT_SEARCHES, updated)
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        saveStringList(KEY_RECENT_SEARCHES, emptyList())
    }

    private fun saveMediaList(key: String, list: List<MediaCardItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("title", item.title)
                put("url", item.url)
                put("apiName", item.apiName)
                put("apiKey", item.apiKey)
                put("posterUrl", item.posterUrl ?: "")
                put("type", item.type)
                put("shortType", item.shortType)
                put("quality", item.quality ?: "")
                put("score", item.score ?: "")
                put("year", item.year ?: 0)
                put("hasSub", item.hasSub)
                put("hasDub", item.hasDub)
                put("subEpisodes", item.subEpisodes ?: 0)
                put("dubEpisodes", item.dubEpisodes ?: 0)
                put("episodeCount", item.episodeCount ?: 0)
                val hObj = JSONObject()
                item.posterHeaders.forEach { (k, v) -> hObj.put(k, v) }
                put("headers", hObj)
            }
            arr.put(obj)
        }
        prefs?.edit()?.putString(key, arr.toString())?.apply()
    }

    private fun loadMediaList(key: String): List<MediaCardItem> {
        val raw = prefs?.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val headers = mutableMapOf<String, String>()
                    obj.optJSONObject("headers")?.let { hObj ->
                        val keys = hObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            headers[k] = hObj.optString(k, "")
                        }
                    }
                    add(
                        MediaCardItem(
                            title = obj.optString("title", ""),
                            url = obj.optString("url", ""),
                            apiName = obj.optString("apiName", ""),
                            apiKey = obj.optString("apiKey", ""),
                            posterUrl = obj.optString("posterUrl", "").takeIf { it.isNotBlank() },
                            posterHeaders = headers,
                            type = obj.optString("type", ""),
                            shortType = obj.optString("shortType", ""),
                            quality = obj.optString("quality", "").takeIf { it.isNotBlank() },
                            score = obj.optString("score", "").takeIf { it.isNotBlank() },
                            year = obj.optInt("year", 0).takeIf { it > 0 },
                            hasSub = obj.optBoolean("hasSub", false),
                            hasDub = obj.optBoolean("hasDub", false),
                            subEpisodes = obj.optInt("subEpisodes", 0).takeIf { it > 0 },
                            dubEpisodes = obj.optInt("dubEpisodes", 0).takeIf { it > 0 },
                            episodeCount = obj.optInt("episodeCount", 0).takeIf { it > 0 }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveStringList(key: String, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs?.edit()?.putString(key, arr.toString())?.apply()
    }

    private fun loadStringList(key: String): List<String> {
        val raw = prefs?.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "")
                    if (s.isNotBlank()) add(s)
                }
            }
        }.getOrDefault(emptyList())
    }
}
