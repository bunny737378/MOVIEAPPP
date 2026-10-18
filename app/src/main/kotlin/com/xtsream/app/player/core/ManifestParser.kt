package com.xtsream.app.player.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class ParsedTracks(
    val qualities: List<String>,
    val audios: List<String>,
    val subtitles: List<String>,
    val qualityUrlMap: Map<String, String> = emptyMap()
)

object ManifestParser {
    suspend fun parse(urlStr: String, headers: Map<String, String>): ParsedTracks = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val response = conn.inputStream.bufferedReader().readText()
            val baseUrlPrefix = urlStr.substringBeforeLast("/") + "/"

            val qualities = mutableListOf<String>()
            val audios = mutableListOf<String>()
            val subs = mutableListOf<String>()
            val qualityMap = mutableMapOf<String, String>()

            if (urlStr.contains(".m3u8") || response.contains("#EXTM3U")) {
                val lines = response.lines()
                var currentQuality: String? = null

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                        val resMatcher = Pattern.compile("RESOLUTION=(\\d+x\\d+)").matcher(trimmed)
                        val bwMatcher = Pattern.compile("BANDWIDTH=(\\d+)").matcher(trimmed)
                        
                        currentQuality = if (resMatcher.find()) {
                            val res = resMatcher.group(1)!!
                            val h = res.substringAfter("x")
                            "${h}p"
                        } else if (bwMatcher.find()) {
                            val bw = bwMatcher.group(1)!!.toLongOrNull() ?: 0L
                            "${bw / 1000}k"
                        } else {
                            "Default"
                        }
                    } else if (currentQuality != null && !trimmed.startsWith("#") && trimmed.isNotBlank()) {
                        val fullUrl = if (trimmed.startsWith("http")) trimmed else baseUrlPrefix + trimmed
                        qualities.add(currentQuality)
                        qualityMap[currentQuality] = fullUrl
                        currentQuality = null
                    }

                    if (trimmed.startsWith("#EXT-X-MEDIA")) {
                        val typeMatcher = Pattern.compile("TYPE=(AUDIO|SUBTITLES)").matcher(trimmed)
                        val nameMatcher = Pattern.compile("(?:NAME|LANGUAGE)=[\"']?([^\"',\\s]+)[\"']?").matcher(trimmed)

                        if (typeMatcher.find() && nameMatcher.find()) {
                            val type = typeMatcher.group(1)
                            val name = nameMatcher.group(1)!!
                            if (type == "AUDIO") {
                                audios.add(name)
                            } else if (type == "SUBTITLES") {
                                subs.add(name)
                            }
                        }
                    }
                }
            } else if (urlStr.contains(".mpd") || response.contains("<MPD")) {
                val resMatcher = Pattern.compile("height=[\"']?(\\d+)[\"']?").matcher(response)
                while (resMatcher.find()) { qualities.add("${resMatcher.group(1)}p") }

                val audMatcher = Pattern.compile("contentType=[\"']?audio[\"']?.*?lang=[\"']?([^\"'>\\s]+)[\"']?").matcher(response)
                while (audMatcher.find()) { audios.add(audMatcher.group(1)!!) }

                val subMatcher = Pattern.compile("contentType=[\"']?text[\"']?.*?lang=[\"']?([^\"'>\\s]+)[\"']?").matcher(response)
                while (subMatcher.find()) { subs.add(subMatcher.group(1)!!) }
            }

            val sortedQualities = qualities.distinct().sortedByDescending { 
                it.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0 
            }

            Log.d("DOWNLOAD_DEBUG", "Parsed Qualities: $sortedQualities, Audios: $audios, Subs: $subs")

            ParsedTracks(
                qualities = sortedQualities,
                audios = audios.distinct(),
                subtitles = subs.distinct(),
                qualityUrlMap = qualityMap
            )
        } catch (e: Exception) {
            Log.e("DOWNLOAD_DEBUG", "Manifest parsing failed", e)
            ParsedTracks(emptyList(), emptyList(), emptyList())
        }
    }
}
