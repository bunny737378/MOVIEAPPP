package com.xtsream.app.stremio

import android.util.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Official Stremio Addon Protocol HTTP client.
 * Follows https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md:
 * - Transport URL normalization (`stremio://`, bare host, `/configure`, `/manifest.json`)
 * - Preserves query string params on transport URLs for token/config-query addons
 * - Merges both modern `extra` and legacy `extraSupported`/`extraRequired` catalog fields
 * - Catalog requests with optional extra args (`search`, `genre`, `skip`)
 * - Meta resolution routed by `types` + `idPrefixes` across installed addons + Cinemeta/Kitsu fallbacks
 * - Addon catalog discovery (`/addon_catalog/{type}/{id}.json`)
 */
object StremioClient {
    private const val TAG = "StremioClient"

    private val requestHeaders = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    )

    fun normalizeManifestUrl(rawInput: String): String? {
        var cleaned = rawInput.trim()
        if (cleaned.isBlank()) return null

        // Handle Stremio Web install links like https://web.stremio.com/#/addons?addon=<encoded>
        val webAddonMatch = Regex("[?&]addon=([^&#]+)", RegexOption.IGNORE_CASE).find(cleaned)
        if (webAddonMatch != null && (cleaned.contains("stremio.com", true) || cleaned.contains("strem.io", true))) {
            val decoded = runCatching {
                java.net.URLDecoder.decode(webAddonMatch.groupValues[1], "UTF-8")
            }.getOrNull()
            if (!decoded.isNullOrBlank()) {
                cleaned = decoded.trim()
            }
        }

        if (cleaned.startsWith("stremio://", ignoreCase = true)) {
            cleaned = "https://" + cleaned.substring("stremio://".length)
        } else if (!cleaned.startsWith("http://", ignoreCase = true) &&
            !cleaned.startsWith("https://", ignoreCase = true)
        ) {
            cleaned = "https://$cleaned"
        }

        val queryIdx = cleaned.indexOf('?')
        val pathPart = if (queryIdx >= 0) cleaned.substring(0, queryIdx) else cleaned
        val queryPart = if (queryIdx >= 0) cleaned.substring(queryIdx) else ""

        val trimmedPath = pathPart.trimEnd('/')
        val manifestPath = when {
            trimmedPath.endsWith("/manifest.json", ignoreCase = true) -> trimmedPath
            trimmedPath.endsWith("/configure", ignoreCase = true) ->
                trimmedPath.removeSuffix("/configure").trimEnd('/') + "/manifest.json"
            else -> "$trimmedPath/manifest.json"
        }
        return manifestPath + queryPart
    }

    fun baseUrlFromManifestUrl(manifestUrl: String): String {
        val withoutQuery = manifestUrl.substringBefore('?')
        return withoutQuery.removeSuffix("/manifest.json").trimEnd('/')
    }

    fun configureUrlFromRawInput(rawInput: String): String? {
        val manifestUrl = normalizeManifestUrl(rawInput) ?: return null
        val base = baseUrlFromManifestUrl(manifestUrl)
        val qIdx = manifestUrl.indexOf('?')
        val q = if (qIdx >= 0) manifestUrl.substring(qIdx) else ""
        return "$base/configure$q"
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun buildExtraPath(extraArgs: LinkedHashMap<String, String>): String {
        if (extraArgs.isEmpty()) return ""
        return extraArgs.entries.joinToString("&") { (k, v) ->
            "${encodeSegment(k)}=${encodeSegment(v)}"
        }
    }

    suspend fun fetchManifest(rawInput: String): Result<StremioAddonRecord> = withContext(Dispatchers.IO) {
        try {
            val manifestUrl = normalizeManifestUrl(rawInput)
                ?: return@withContext Result.failure(IllegalArgumentException("Enter a valid Stremio addon URL"))
            val text = app.get(
                url = manifestUrl,
                headers = requestHeaders,
                cacheTime = 0,
                cacheUnit = TimeUnit.MINUTES
            ).text.trim()

            if (!text.startsWith("{")) {
                return@withContext Result.failure(IllegalStateException("URL did not return a valid Stremio manifest.json"))
            }

            val json = JSONObject(text)
            val manifest = parseManifest(json)
                ?: return@withContext Result.failure(IllegalStateException("Invalid Stremio manifest (missing id or name)"))

            Result.success(
                StremioAddonRecord(
                    manifestUrl = manifestUrl,
                    baseUrl = baseUrlFromManifestUrl(manifestUrl),
                    manifest = manifest,
                    enabled = true,
                    addedAt = System.currentTimeMillis()
                )
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed fetching Stremio manifest for $rawInput", t)
            Result.failure(t)
        }
    }

    suspend fun fetchCatalog(
        addon: StremioAddonRecord,
        catalog: StremioCatalogDef,
        skip: Int = 0,
        search: String? = null,
        genre: String? = null
    ): List<StremioMetaPreview> = withContext(Dispatchers.IO) {
        val extra = linkedMapOf<String, String>()
        if (!search.isNullOrBlank()) extra["search"] = search.trim()
        if (!genre.isNullOrBlank()) extra["genre"] = genre.trim()
        if (skip > 0) extra["skip"] = skip.toString()

        val encodedType = encodeSegment(catalog.type)
        val encodedId = encodeSegment(catalog.id)
        val extraPath = buildExtraPath(extra)
        val qSuffix = addon.querySuffix

        val url = if (extraPath.isBlank()) {
            "${addon.baseUrl}/catalog/$encodedType/$encodedId.json$qSuffix"
        } else {
            "${addon.baseUrl}/catalog/$encodedType/$encodedId/$extraPath.json$qSuffix"
        }

        val text = runCatching {
            app.get(
                url = url,
                headers = requestHeaders,
                cacheTime = if (!search.isNullOrBlank()) 2 else 5,
                cacheUnit = TimeUnit.MINUTES
            ).text.trim()
        }.getOrNull() ?: return@withContext emptyList()

        if (!text.startsWith("{")) return@withContext emptyList()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext emptyList()
        val metas = root.optJSONArray("metas") ?: return@withContext emptyList()
        buildList(metas.length()) {
            for (i in 0 until metas.length()) {
                val obj = metas.optJSONObject(i) ?: continue
                parseMetaPreview(obj, defaultType = catalog.type)?.let { add(it) }
            }
        }
    }

    suspend fun fetchMeta(
        preferredAddon: StremioAddonRecord?,
        allEnabledAddons: List<StremioAddonRecord>,
        type: String,
        id: String
    ): StremioMeta? = withContext(Dispatchers.IO) {
        val candidates = buildList {
            if (preferredAddon != null && preferredAddon.manifest.matchesResource("meta", type, id)) {
                add(preferredAddon)
            }
            allEnabledAddons.forEach { addon ->
                if (addon.manifestUrl != preferredAddon?.manifestUrl &&
                    addon.manifest.matchesResource("meta", type, id)
                ) {
                    add(addon)
                }
            }
            // Fallback: try preferredAddon even if idPrefixes/types weren't explicitly listed
            if (preferredAddon != null && none { it.manifestUrl == preferredAddon.manifestUrl } &&
                preferredAddon.manifest.hasResource("meta")
            ) {
                add(preferredAddon)
            }
            // Also try any other enabled meta addon with matching type (even if idPrefixes omitted)
            allEnabledAddons.forEach { addon ->
                if (none { it.manifestUrl == addon.manifestUrl } && addon.manifest.hasResource("meta")) {
                    val mTypes = addon.manifest.types
                    if (mTypes.isEmpty() || mTypes.any { it.equals(type, ignoreCase = true) }) {
                        add(addon)
                    }
                }
            }
        }

        val encodedType = encodeSegment(type)
        val encodedId = encodeSegment(id)

        for (addon in candidates) {
            val url = "${addon.baseUrl}/meta/$encodedType/$encodedId.json${addon.querySuffix}"
            val parsed = fetchMetaFromUrl(url, defaultType = type)
            if (parsed != null) return@withContext parsed
        }

        // Public Cinemeta fallback for IMDb IDs (tt...) — handles catalog-only addons cleanly!
        val imdbId = extractImdbId(id)
        if (imdbId != null) {
            val cinemetaTypes = when (type.lowercase()) {
                "movie" -> listOf("movie")
                "series" -> listOf("series")
                else -> listOf("series", "movie")
            }
            for (cType in cinemetaTypes) {
                val cinemetaUrl = "https://v3-cinemeta.strem.io/meta/$cType/${encodeSegment(imdbId)}.json"
                val parsed = fetchMetaFromUrl(cinemetaUrl, defaultType = type)
                if (parsed != null) return@withContext parsed
            }
        }

        // Public Kitsu fallback for kitsu:* IDs when user installed a Kitsu catalog-only addon
        if (id.startsWith("kitsu:", ignoreCase = true)) {
            val kitsuUrl = "https://anime-kitsu.strem.fun/meta/anime/$encodedId.json"
            val parsed = fetchMetaFromUrl(kitsuUrl, defaultType = type)
            if (parsed != null) return@withContext parsed
        }

        null
    }

    private suspend fun fetchMetaFromUrl(url: String, defaultType: String): StremioMeta? {
        return runCatching {
            val text = app.get(
                url = url,
                headers = requestHeaders,
                cacheTime = 10,
                cacheUnit = TimeUnit.MINUTES
            ).text.trim()
            if (!text.startsWith("{")) return@runCatching null
            val root = JSONObject(text)
            val metaObj = root.optJSONObject("meta") ?: return@runCatching null
            parseMeta(metaObj, defaultType = defaultType)
        }.getOrNull()
    }

    private fun extractImdbId(rawId: String): String? {
        val trimmed = rawId.trim()
        if (trimmed.startsWith("tt", ignoreCase = true) && trimmed.length >= 4) {
            return trimmed.substringBefore(':')
        }
        return Regex("\\b(tt\\d{5,})\\b", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.getOrNull(1)
    }

    suspend fun fetchAddonCatalog(
        addon: StremioAddonRecord,
        catalog: StremioCatalogDef
    ): List<StremioAddonRecord> = withContext(Dispatchers.IO) {
        val url = "${addon.baseUrl}/addon_catalog/${encodeSegment(catalog.type)}/${encodeSegment(catalog.id)}.json${addon.querySuffix}"
        val text = runCatching {
            app.get(
                url = url,
                headers = requestHeaders,
                cacheTime = 10,
                cacheUnit = TimeUnit.MINUTES
            ).text.trim()
        }.getOrNull() ?: return@withContext emptyList()

        if (!text.startsWith("{")) return@withContext emptyList()
        val arr = JSONObject(text).optJSONArray("addons") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val entry = arr.optJSONObject(i) ?: continue
                val transportUrl = entry.optString("transportUrl", "").trim()
                val manifestObj = entry.optJSONObject("manifest") ?: continue
                val manifest = parseManifest(manifestObj) ?: continue
                val normalized = normalizeManifestUrl(transportUrl) ?: continue
                add(
                    StremioAddonRecord(
                        manifestUrl = normalized,
                        baseUrl = baseUrlFromManifestUrl(normalized),
                        manifest = manifest
                    )
                )
            }
        }
    }

    fun parseManifest(json: JSONObject): StremioManifest? {
        val id = json.optString("id", "").trim()
        val name = json.optString("name", "").trim()
        if (id.isBlank() || name.isBlank()) return null

        val version = json.optString("version", "1.0.0").trim().ifBlank { "1.0.0" }
        val description = json.optString("description", "").trim()
        val logo = json.optString("logo", "").trim().takeIf { it.isNotBlank() && it != "null" }
            ?: json.optString("icon", "").trim().takeIf { it.isNotBlank() && it != "null" }
        val background = json.optString("background", "").trim().takeIf { it.isNotBlank() && it != "null" }

        val types = json.optJSONArray("types").toStringList()
        val idPrefixes = json.optJSONArray("idPrefixes")?.toStringList()?.takeIf { it.isNotEmpty() }

        val resourcesArr = json.optJSONArray("resources")
        val resources = buildList {
            if (resourcesArr != null) {
                for (i in 0 until resourcesArr.length()) {
                    val item = resourcesArr.opt(i)
                    when (item) {
                        is String -> if (item.isNotBlank()) {
                            add(StremioResourceSpec(name = item.trim()))
                        }
                        is JSONObject -> {
                            val rName = item.optString("name", "").trim()
                            if (rName.isNotBlank()) {
                                add(
                                    StremioResourceSpec(
                                        name = rName,
                                        types = item.optJSONArray("types").toStringList(),
                                        idPrefixes = item.optJSONArray("idPrefixes")?.toStringList()?.takeIf { it.isNotEmpty() }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        val catalogs = parseCatalogArray(json.optJSONArray("catalogs"))
        val addonCatalogs = parseCatalogArray(json.optJSONArray("addonCatalogs"))

        val behaviorHints = json.optJSONObject("behaviorHints")
        val configurable = behaviorHints?.optBoolean("configurable", false) ?: false
        val configurationRequired = behaviorHints?.optBoolean("configurationRequired", false) ?: false

        return StremioManifest(
            id = id,
            name = name,
            version = version,
            description = description,
            logo = logo,
            background = background,
            types = types,
            idPrefixes = idPrefixes,
            resources = resources,
            catalogs = catalogs,
            addonCatalogs = addonCatalogs,
            configurable = configurable || configurationRequired,
            configurationRequired = configurationRequired
        )
    }

    private fun parseCatalogArray(arr: JSONArray?): List<StremioCatalogDef> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val type = obj.optString("type", "").trim()
                val id = obj.optString("id", "").trim()
                if (type.isBlank() || id.isBlank()) continue
                val name = obj.optString("name", "").trim().ifBlank { id }

                val extraByName = linkedMapOf<String, StremioExtraProp>()
                val extraArr = obj.optJSONArray("extra")
                if (extraArr != null) {
                    for (j in 0 until extraArr.length()) {
                        val ex = extraArr.optJSONObject(j) ?: continue
                        val exName = ex.optString("name", "").trim()
                        if (exName.isBlank()) continue
                        extraByName[exName.lowercase()] = StremioExtraProp(
                            name = exName,
                            isRequired = ex.optBoolean("isRequired", false),
                            options = ex.optJSONArray("options").toStringList(),
                            optionsLimit = ex.optInt("optionsLimit", 1)
                        )
                    }
                }

                // Always merge legacy extraSupported / extraRequired / genres as well!
                val requiredSet = obj.optJSONArray("extraRequired").toStringList().map { it.lowercase() }.toSet()
                val supportedList = obj.optJSONArray("extraSupported").toStringList()
                val genres = obj.optJSONArray("genres").toStringList()
                (supportedList + obj.optJSONArray("extraRequired").toStringList()).forEach { exName ->
                    val key = exName.lowercase()
                    val existing = extraByName[key]
                    if (existing == null) {
                        extraByName[key] = StremioExtraProp(
                            name = exName,
                            isRequired = key in requiredSet,
                            options = if (key == "genre") genres else emptyList()
                        )
                    } else if (key in requiredSet && !existing.isRequired) {
                        extraByName[key] = existing.copy(isRequired = true)
                    }
                }

                add(StremioCatalogDef(type = type, id = id, name = name, extra = extraByName.values.toList()))
            }
        }
    }

    private fun parseMetaPreview(obj: JSONObject, defaultType: String): StremioMetaPreview? {
        val id = obj.optString("id", "").trim().ifBlank { obj.optString("imdb_id", "").trim() }
        val name = obj.optString("name", "").trim().ifBlank { obj.optString("title", "").trim() }
        if (id.isBlank() || name.isBlank()) return null
        val type = obj.optString("type", "").trim().ifBlank { defaultType }
        return StremioMetaPreview(
            id = id,
            type = type,
            name = name,
            poster = obj.optCleanString("poster"),
            posterShape = obj.optCleanString("posterShape"),
            background = obj.optCleanString("background"),
            logo = obj.optCleanString("logo"),
            description = obj.optCleanString("description"),
            releaseInfo = obj.optCleanString("releaseInfo") ?: obj.optCleanString("year"),
            imdbRating = obj.optCleanString("imdbRating"),
            genres = obj.optJSONArray("genres").toStringList()
        )
    }

    private fun parseMeta(obj: JSONObject, defaultType: String): StremioMeta? {
        val id = obj.optString("id", "").trim().ifBlank { obj.optString("imdb_id", "").trim() }
        val name = obj.optString("name", "").trim().ifBlank { obj.optString("title", "").trim() }
        if (id.isBlank() || name.isBlank()) return null
        val type = obj.optString("type", "").trim().ifBlank { defaultType }

        val linksArr = obj.optJSONArray("links")
        val links = buildList {
            if (linksArr != null) {
                for (i in 0 until linksArr.length()) {
                    val lObj = linksArr.optJSONObject(i) ?: continue
                    val lName = lObj.optString("name", "").trim()
                    val lCat = lObj.optString("category", "").trim()
                    if (lName.isNotBlank()) {
                        add(StremioMetaLink(lName, lCat, lObj.optCleanString("url")))
                    }
                }
            }
        }

        val genres = obj.optJSONArray("genres").toStringList().ifEmpty {
            links.filter { it.category.equals("Genres", true) || it.category.equals("genre", true) }.map { it.name }
        }
        val cast = obj.optJSONArray("cast").toStringList().ifEmpty {
            links.filter { it.category.equals("Cast", true) || it.category.equals("actor", true) }.map { it.name }
        }
        val directors = obj.optJSONArray("director").toStringList().ifEmpty {
            links.filter { it.category.equals("Directors", true) || it.category.equals("director", true) }.map { it.name }
        }

        val videosArr = obj.optJSONArray("videos")
        val videos = buildList {
            if (videosArr != null) {
                for (i in 0 until videosArr.length()) {
                    val vObj = videosArr.optJSONObject(i) ?: continue
                    val vId = vObj.optString("id", "").trim().ifBlank { "$id:${i + 1}" }
                    val epNum = when {
                        vObj.has("episode") && !vObj.isNull("episode") -> vObj.optInt("episode")
                        vObj.has("number") && !vObj.isNull("number") -> vObj.optInt("number")
                        else -> null
                    }
                    val vTitle = vObj.optCleanString("title")
                        ?: vObj.optCleanString("name")
                        ?: "Episode ${epNum ?: (i + 1)}"
                    val seasonVal = if (vObj.has("season") && !vObj.isNull("season")) vObj.optInt("season") else null
                    add(
                        StremioVideo(
                            id = vId,
                            title = vTitle,
                            released = vObj.optCleanString("released"),
                            thumbnail = vObj.optCleanString("thumbnail"),
                            season = seasonVal,
                            episode = epNum,
                            overview = vObj.optCleanString("overview") ?: vObj.optCleanString("description")
                        )
                    )
                }
            }
        }

        return StremioMeta(
            id = id,
            type = type,
            name = name,
            poster = obj.optCleanString("poster"),
            posterShape = obj.optCleanString("posterShape"),
            background = obj.optCleanString("background"),
            logo = obj.optCleanString("logo"),
            description = obj.optCleanString("description"),
            releaseInfo = obj.optCleanString("releaseInfo") ?: obj.optCleanString("year"),
            released = obj.optCleanString("released"),
            imdbRating = obj.optCleanString("imdbRating"),
            runtime = obj.optCleanString("runtime"),
            genres = genres,
            cast = cast,
            directors = directors,
            links = links,
            videos = videos,
            country = obj.optCleanString("country"),
            awards = obj.optCleanString("awards"),
            website = obj.optCleanString("website")
        )
    }

    private fun JSONObject.optCleanString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "").trim()
        return v.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                val s = optString(i, "").trim()
                if (s.isNotBlank() && !s.equals("null", ignoreCase = true)) add(s)
            }
        }
    }
}
