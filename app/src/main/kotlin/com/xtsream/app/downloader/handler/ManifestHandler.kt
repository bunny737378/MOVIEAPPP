package com.xtsream.app.downloader.handler

import com.xtsream.app.downloader.DownloadManager
import java.io.File

object ManifestHandler {
    suspend fun planHls(masterUrl: String, content: String, workDir: File): DownloadManager.DownloadPlan {
        val baseUrlMaster = masterUrl.substringBeforeLast("/") + "/"
        val allAudios = mutableListOf<Pair<String, String>>()
        val allSubs = mutableListOf<Pair<String, String>>()
        val variants = mutableListOf<Triple<String, Int, String>>()

        content.lines().forEach { line ->
            if (line.startsWith("#EXT-X-MEDIA:")) {
                val attrs = DownloadManager.parseAttributes(line)
                val type = attrs["TYPE"]
                val uri = attrs["URI"]
                val lang = attrs["LANGUAGE"] ?: attrs["NAME"] ?: "und"
                if (!uri.isNullOrBlank()) {
                    val fullUri = if (uri.startsWith("http")) uri else baseUrlMaster + uri
                    if (type == "AUDIO") allAudios.add(Pair(fullUri, lang))
                    if (type == "SUBTITLES") allSubs.add(Pair(fullUri, lang))
                }
            }
        }

        var pendingBw = 0
        var pendingRes = ""
        for (line in content.lines()) {
            val t = line.trim()
            if (t.startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = DownloadManager.parseAttributes(t)
                pendingBw = attrs["BANDWIDTH"]?.toIntOrNull() ?: 0
                pendingRes = DownloadManager.resolutionToTag(attrs["RESOLUTION"] ?: "")
            } else if (t.isNotBlank() && !t.startsWith("#")) {
                val fullUri = if (t.startsWith("http")) t else baseUrlMaster + t
                val tag = pendingRes.ifEmpty { "${pendingBw / 1000}k" }
                variants.add(Triple(tag, pendingBw, fullUri))
                pendingBw = 0; pendingRes = ""
            }
        }

        val chosen: Triple<String, Int, String> = when {
            variants.isEmpty() -> Triple("video", 0, masterUrl)
            DownloadManager.selQualityTag.isNotBlank() ->
                variants.firstOrNull { it.first == DownloadManager.selQualityTag } ?: variants.maxByOrNull { it.second }!!
            else -> variants.maxByOrNull { it.second }!!
        }

        val audios = if (DownloadManager.selAudioLangs.isEmpty()) allAudios else allAudios.filter { it.second in DownloadManager.selAudioLangs }
        val subs = if (DownloadManager.selSubLangs.isEmpty()) allSubs else allSubs.filter { it.second in DownloadManager.selSubLangs }

        fun prepareHlsTrack(tag: String, lang: String, url: String, folderName: String): DownloadManager.ResolvedTrack {
            val dir = File(workDir, folderName).apply { mkdirs() }
            val trackContent = DownloadManager.fetchString(url, DownloadManager.lastHeaders)
            val baseUrl = url.substringBeforeLast("/") + "/"
            val playlistLines = mutableListOf<String>()
            val aes = DownloadManager.parseAesKey(url, trackContent, DownloadManager.lastHeaders)
            val trackJobs = mutableListOf<Pair<String, File>>()
            var seq = 0

            for (line in trackContent.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#EXT-X-KEY")) continue

                if (trimmed.startsWith("#EXT-X-MAP")) {
                    val uri = Regex("""URI=["']?([^"',\s]+)["']?""").find(trimmed)?.groupValues?.get(1)
                    if (uri != null) {
                        val fullUri = if (uri.startsWith("http")) uri else baseUrl + uri
                        val ext = DownloadManager.detectExtension(fullUri, "mp4")
                        val initName = tag + "_init" + ext
                        val initFile = File(dir, initName)
                        DownloadManager.totalSegmentsToDownload.incrementAndGet()
                        trackJobs.add(Pair(fullUri, initFile))
                        val mapLine = "#EXT-X-MAP:URI=\"" + initName + "\""
                        playlistLines.add(mapLine)
                    }
                } else if (!trimmed.startsWith("#")) {
                    val fullUri = if (trimmed.startsWith("http")) trimmed else baseUrl + trimmed
                    val ext = DownloadManager.detectExtension(fullUri, "ts")
                    seq++
                    val fileName = tag + "_" + seq + ext
                    val segFile = File(dir, fileName)
                    DownloadManager.totalSegmentsToDownload.incrementAndGet()
                    trackJobs.add(Pair(fullUri, segFile))
                    playlistLines.add(fileName)
                } else {
                    playlistLines.add(trimmed)
                }
            }

            val m3u8File = File(dir, "playlist.m3u8")
            m3u8File.writeText(buildString {
                append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:10\n#EXT-X-PLAYLIST-TYPE:VOD\n")
                append(playlistLines.joinToString("\n"))
                append("\n#EXT-X-ENDLIST\n")
            })

            return DownloadManager.ResolvedTrack(tag, lang, m3u8File, trackJobs, aes)
        }

        val vTrack = prepareHlsTrack("video", "und", chosen.third, "video")
        val aTracks = audios.mapIndexed { idx, it -> prepareHlsTrack("audio_${it.second}", it.second, it.first, "audio_${it.second}_$idx") }
        val sTracks = subs.mapIndexed { idx, it -> prepareHlsTrack("subtitle_${it.second}", it.second, it.first, "subtitle_${it.second}_$idx") }

        val extSubFiles = mutableListOf<File>()
        if (DownloadManager.lastExternalSubs.isNotEmpty()) {
            val extDir = File(workDir, "ext_caption").apply { mkdirs() }
            DownloadManager.lastExternalSubs.forEachIndexed { idx, extUrl ->
                val ext = DownloadManager.detectExtension(extUrl, "vtt")
                extSubFiles.add(File(extDir, "ext_sub_${idx}$ext"))
            }
        }

        return DownloadManager.DownloadPlan(vTrack, aTracks, sTracks, extSubFiles)
    }
}