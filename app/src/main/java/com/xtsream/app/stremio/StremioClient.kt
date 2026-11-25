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
 * - Transport URL normalization (`stremio://`, bare host, `/manifest.json`)
 * - Full string & object resource spec parsing (`name`, `types`, `idPrefixes`)
 * - Catalog requests with optional extra args (`search`, `genre`, `skip`)
 * - Meta resolution routed by `types` + `idPrefixes` across installed addons
 * - Addon catalog discovery (`/addon_catalog/{type}/{id}.json`)
 */
object StremioClient {
    private const val TAG = "StremioClient"

    fun normalizeManifestUrl(rawInput: String): String? {
        var cleaned = rawInput.trim()
        if (cleaned.isBlank()) return null

        if (cleaned.startsWith("stremio://", ignoreCase = true)) {
            cleaned = "https://" + cleaned.substring("stremio://".length)
        } else if (!cleaned.startsWith("http://", ignoreCase = true) &&
            !cleaned.startsWith("https://", ignoreCase = true)
        ) {
            cleaned = "https://$cleaned"
        }

        // Split query string if any (rare, e.g., auth query params)
        val queryIdx = cleaned.indexOf('?')
        val pathPart = if (queryIdx >= 0) cleaned.substring(0, queryIdx) else cleaned
        val queryPart = if (queryIdx >= 0) cleaned.substring(queryIdx) else ""

        val trimmedPath = pathPart.trimEnd('/')
        val manifestPath = when {
            trimmedPath.endsWith("/manifest.json", ignoreCase = true) -> trimmedPath
            trimmedPath.endsWith("/configure", ignoreCase = true) ->
                trimmedPath.removeSuffix("/configure") + "/manifest.json"
            else -> "$trimmedPath/manifest.json"
        }
        return manifestPath + queryPart
    }

    fun baseUrlFromManifestUrl(manifestUrl: String): String {
        val withoutQuery = manifestUrl.substringBefore('?')
        return withoutQuery.removeSuffix("/manifest.json").trimEnd('/')
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

        val url = if (extraPath.isBlank()) {
            "${addon.baseUrl}/catalog/$encodedType/$encodedId.json"
        } else {
            "${addon.baseUrl}/catalog/$encodedType/$encodedId/$extraPath.json"
        }

        val text = app.get(
            url = url,
            cacheTime = 5,
            cacheUnit = TimeUnit.MINUTES
        ).text.trim()

        if (!text.startsWith("{")) return@withContext emptyList()
        val root = JSONObject(text)
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
            // Fallback: if preferred addon didn't declare idPrefixes explicitly, still try it before external fallback
            if (preferredAddon != null && none { it.manifestUrl == preferredAddon.manifestUrl } &&
                preferredAddon.manifest.hasResource("meta")
            ) {
                add(preferredAddon)
            }
        }

        val encodedType = encodeSegment(type)
        val encodedId = encodeSegment(id)

        for (addon in candidates) {
            val url = "${addon.baseUrl}/meta/$encodedType/$encodedId.json"
            val parsed = runCatching {
                val text = app.get(url = url, cacheTime = 10, cacheUnit = TimeUnit.MINUTES).text.trim()
                if (!text.startsWith("{")) return@runCatching null
                val metaObj = JSONObject(text).optJSONObject("meta") ?: return@runCatching null
                parseMeta(metaObj, defaultType = type)
            }.getOrNull()
            if (parsed != null) return@withContext parsed
        }

        // Public Cinemeta fallback for standard IMDb IDs (tt...) when user only installed a catalog-only addon
        if (id.startsWith("tt", ignoreCase = true) && (type.equals("movie", true) || type.equals("series", true))) {
            val cinemetaUrl = "https://v3-cinemeta.strem.io/meta/$encodedType/$encodedId.json"
            return@withContext runCatching {
                val text = app.get(url = cinemetaUrl, cacheTime = 15, cacheUnit = TimeUnit.MINUTES).text.trim()
                if (!text.startsWith("{")) return@runCatching null
                val metaObj = JSONObject(text).optJSONObject("meta") ?: return@runCatching null
                parseMeta(metaObj, defaultType = type)
            }.getOrNull()
        }

        null
    }

    suspend fun fetchAddonCatalog(
        addon: StremioAddonRecord,
        catalog: StremioCatalogDef
    ): List<StremioAddonRecord> = withContext(Dispatchers.IO) {
        val url = "${addon.baseUrl}/addon_catalog/${encodeSegment(catalog.type)}/${encodeSegment(catalog.id)}.json"
        val text = runCatching {
            app.get(url = url, cacheTime = 10, cacheUnit = TimeUnit.MINUTES).text.trim()
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
            configurable = configurable,
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

                val extraList = mutableListOf<StremioExtraProp>()
                val extraArr = obj.optJSONArray("extra")
                if (extraArr != null) {
                    for (j in 0 until extraArr.length()) {
                        val ex = extraArr.optJSONObject(j) ?: continue
                        val exName = ex.optString("name", "").trim()
                        if (exName.isBlank()) continue
                        extraList.add(
                            StremioExtraProp(
                                name = exName,
                                isRequired = ex.optBoolean("isRequired", false),
                                options = ex.optJSONArray("options").toStringList(),
                                optionsLimit = ex.optInt("optionsLimit", 1)
                            )
                        )
                    }
                } else {
                    // Legacy short format: extraSupported + extraRequired
                    val required = obj.optJSONArray("extraRequired").toStringList().toSet()
                    val supported = obj.optJSONArray("extraSupported").toStringList()
                    val genres = obj.optJSONArray("genres").toStringList()
                    (supported + required).distinct().forEach { exName ->
                        extraList.add(
                            StremioExtraProp(
                                name = exName,
                                isRequired = exName in required,
                                options = if (exName.equals("genre", true)) genres else emptyList()
                            )
                        )
                    }
                }

                add(StremioCatalogDef(type = type, id = id, name = name, extra = extraList))
            }
        }
    }

    private fun parseMetaPreview(obj: JSONObject, defaultType: String): StremioMetaPreview? {
        val id = obj.optString("id", "").trim()
        val name = obj.optString("name", "").trim()
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
            releaseInfo = obj.optCleanString("releaseInfo"),
            imdbRating = obj.optCleanString("imdbRating"),
            genres = obj.optJSONArray("genres").toStringList()
        )
    }

    private fun parseMeta(obj: JSONObject, defaultType: String): StremioMeta? {
        val id = obj.optString("id", "").trim()
        val name = obj.optString("name", "").trim()
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
                    val vId = vObj.optString("id", "").trim()
                    if (vId.isBlank()) continue
                    val vTitle = vObj.optCleanString("title")
                        ?: vObj.optCleanString("name")
                        ?: "Episode ${vObj.optInt("episode", i + 1)}"
                    val seasonVal = if (vObj.has("season") && !vObj.isNull("season")) vObj.optInt("season") else null
                    val episodeVal = if (vObj.has("episode") && !vObj.isNull("episode")) vObj.optInt("episode")
                    else if (vObj.has("number") && !vObj.isNull("number")) vObj.optInt("number") else null
                    add(
                        StremioVideo(
                            id = vId,
                            title = vTitle,
                            released = vObj.optCleanString("released"),
                            thumbnail = vObj.optCleanString("thumbnail"),
                            season = seasonVal,
                            episode = episodeVal,
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
            releaseInfo = obj.optCleanString("releaseInfo"),
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
