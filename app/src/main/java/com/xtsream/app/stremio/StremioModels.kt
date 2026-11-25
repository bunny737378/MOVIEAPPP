package com.xtsream.app.stremio

/**
 * Stremio addon resource filter categories shown in Providers → Stremio.
 */
enum class StremioAddonCategory(val label: String, val resourceName: String?) {
    All("All", null),
    Catalog("Catalog", "catalog"),
    Meta("Meta", "meta"),
    Stream("Stream", "stream"),
    Subtitles("Subtitles", "subtitles")
}

data class StremioExtraProp(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int = 1
)

data class StremioCatalogDef(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<StremioExtraProp> = emptyList()
) {
    fun supportsExtra(extraName: String): Boolean =
        extra.any { it.name.equals(extraName, ignoreCase = true) }

    fun requiresExtra(extraName: String): Boolean =
        extra.any { it.name.equals(extraName, ignoreCase = true) && it.isRequired }

    fun hasAnyRequiredExtra(): Boolean = extra.any { it.isRequired }

    fun supportsSearch(): Boolean = supportsExtra("search")
    fun supportsSkip(): Boolean = supportsExtra("skip")
    fun isSearchOnly(): Boolean = requiresExtra("search")
}

data class StremioResourceSpec(
    val name: String,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null
)

data class StremioManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
    val resources: List<StremioResourceSpec> = emptyList(),
    val catalogs: List<StremioCatalogDef> = emptyList(),
    val addonCatalogs: List<StremioCatalogDef> = emptyList(),
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false
) {
    fun hasResource(resourceName: String): Boolean {
        if (resourceName.equals("catalog", ignoreCase = true) && catalogs.isNotEmpty()) return true
        return resources.any { it.name.equals(resourceName, ignoreCase = true) }
    }

    fun resourceNames(): List<String> {
        val names = linkedSetOf<String>()
        resources.forEach { if (it.name.isNotBlank()) names.add(it.name.lowercase()) }
        if (catalogs.isNotEmpty()) names.add("catalog")
        if (addonCatalogs.isNotEmpty()) names.add("addon_catalog")
        return names.toList()
    }

    fun matchesResource(resourceName: String, type: String, itemId: String? = null): Boolean {
        val spec = resources.firstOrNull { it.name.equals(resourceName, ignoreCase = true) } ?: return false
        val effectiveTypes = spec.types.ifEmpty { types }
        if (effectiveTypes.isNotEmpty() && effectiveTypes.none { it.equals(type, ignoreCase = true) }) {
            return false
        }
        val effectivePrefixes = spec.idPrefixes ?: idPrefixes
        if (!itemId.isNullOrBlank() && !effectivePrefixes.isNullOrEmpty()) {
            return effectivePrefixes.any { prefix -> itemId.startsWith(prefix, ignoreCase = true) }
        }
        return true
    }

    fun matchesCategory(category: StremioAddonCategory): Boolean {
        val res = category.resourceName ?: return true
        return hasResource(res)
    }

    fun hasBrowseableCatalogs(): Boolean =
        catalogs.any { !it.hasAnyRequiredExtra() } ||
            catalogs.any { cat ->
                cat.extra.filter { it.isRequired }.all { it.name.equals("genre", true) && it.options.isNotEmpty() }
            }

    fun hasSearchableCatalogs(): Boolean = catalogs.any { it.supportsSearch() }
    fun isDiscoveryCapable(): Boolean = hasBrowseableCatalogs() || hasSearchableCatalogs()
}

data class StremioAddonRecord(
    val manifestUrl: String,
    val baseUrl: String,
    val manifest: StremioManifest,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
) {
    val querySuffix: String
        get() {
            val idx = manifestUrl.indexOf('?')
            return if (idx >= 0) manifestUrl.substring(idx) else ""
        }

    val configureUrl: String
        get() = "$baseUrl/configure$querySuffix"
}

data class StremioMetaLink(
    val name: String,
    val category: String,
    val url: String? = null
)

data class StremioVideo(
    val id: String,
    val title: String,
    val released: String? = null,
    val thumbnail: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null
)

data class StremioMetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList()
)

data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val posterShape: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val released: String? = null,
    val imdbRating: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val links: List<StremioMetaLink> = emptyList(),
    val videos: List<StremioVideo> = emptyList(),
    val country: String? = null,
    val awards: String? = null,
    val website: String? = null
)
