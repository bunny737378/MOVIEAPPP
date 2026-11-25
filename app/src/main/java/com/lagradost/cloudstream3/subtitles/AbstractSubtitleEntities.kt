package com.lagradost.cloudstream3.subtitles

import com.lagradost.cloudstream3.TvType
import java.io.Closeable
import java.io.File
import java.util.zip.ZipInputStream

class AbstractSubtitleEntities {
    data class SubtitleEntity(
        var idPrefix: String,
        var name: String = "",
        var lang: String = "en",
        var data: String = "",
        var type: TvType = TvType.Movie,
        var source: String = "",
        var epNumber: Int? = null,
        var seasonNumber: Int? = null,
        var year: Int? = null,
        var isHearingImpaired: Boolean = false,
        var headers: Map<String, String> = emptyMap(),
    )

    data class SubtitleSearch(
        var query: String = "",
        var lang: String? = null,
        var imdbId: String? = null,
        var tmdbId: Int? = null,
        var malId: Int? = null,
        var aniListId: Int? = null,
        var epNumber: Int? = null,
        var seasonNumber: Int? = null,
        var year: Int? = null,
    )
}

class SubtitleResource : Closeable {
    private val urls = mutableListOf<String>()
    private val files = mutableListOf<File>()

    fun getUrls(): List<String> = urls.toList()
    fun getFiles(): List<File> = files.toList()

    fun addUrl(url: String?, headers: Map<String, String> = emptyMap()) {
        if (!url.isNullOrBlank()) urls.add(url)
    }

    fun addFile(file: File?) {
        if (file != null && file.exists()) files.add(file)
    }

    fun addZipUrl(url: String?, headers: Map<String, String> = emptyMap()) {
        if (!url.isNullOrBlank()) urls.add(url)
    }

    fun addZipStream(stream: ZipInputStream?) {
        runCatching { stream?.close() }
    }

    override fun close() {
        urls.clear()
        files.clear()
    }
}
