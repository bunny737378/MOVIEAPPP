package com.xtsream.app.downloader.handler

import com.xtsream.app.downloader.DownloadManager
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import kotlin.math.ceil

object DashHandler {
    data class DashTrack(val tag: String, val initUrl: String, val segTemplate: String, val startNumber: Int, val duration: Long, val timescale: Long, val totalDurationSec: Double, val lang: String, val bw: Int)

    suspend fun planDash(masterUrl: String, content: String, workDir: File): DownloadManager.DownloadPlan {
        val baseUrl = masterUrl.substringBeforeLast("/") + "/"
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
        val root = doc.documentElement

        var durationSec = 0.0
        val durAttr = root.getAttribute("mediaPresentationDuration")
        if (durAttr.isNotEmpty()) {
            val h = Regex("""(\d+)H""").find(durAttr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val m = Regex("""(\d+)M""").find(durAttr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val s = Regex("""([\d.]+)S""").find(durAttr)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            durationSec = h * 3600 + m * 60 + s
        }

        val videoTracks = mutableListOf<DashTrack>()
        val audioTracks = mutableListOf<DashTrack>()
        val subTracks = mutableListOf<DashTrack>()

        fun getElement(parent: Element, tag: String): Element? {
            val list = parent.getElementsByTagName(tag)
            return if (list.length > 0) list.item(0) as Element else null
        }

        val adaptationSets = doc.getElementsByTagName("AdaptationSet")
        for (i in 0 until adaptationSets.length) {
            val asElement = adaptationSets.item(i) as Element
            val contentType = asElement.getAttribute("contentType")
            val lang = asElement.getAttribute("lang").takeIf { it.isNotBlank() } ?: "und"

            val representations = asElement.getElementsByTagName("Representation")
            for (j in 0 until representations.length) {
                val repElement = representations.item(j) as Element
                val repId = repElement.getAttribute("id")
                val height = repElement.getAttribute("height")
                val bw = repElement.getAttribute("bandwidth").toIntOrNull() ?: 0

                var segTemplate = getElement(repElement, "SegmentTemplate")
                if (segTemplate == null) segTemplate = getElement(asElement, "SegmentTemplate")

                if (segTemplate != null) {
                    val initTpl = segTemplate.getAttribute("initialization")
                    val mediaTpl = segTemplate.getAttribute("media")
                    val timescale = segTemplate.getAttribute("timescale").toLongOrNull() ?: 1L
                    val segDur = segTemplate.getAttribute("duration").toLongOrNull() ?: 0L
                    val startNum = segTemplate.getAttribute("startNumber").toIntOrNull() ?: 1

                    val repTarget = "\$RepresentationID\$"
                    val initUrl = initTpl.replace(repTarget, repId)
                    val mediaStr = mediaTpl.replace(repTarget, repId)

                    val tag = if (contentType == "video") {
                        if (height.isNotEmpty()) "${height}p" else "${bw / 1000}k"
                    } else if (contentType == "audio") {
                        "audio_$lang"
                    } else "subtitle_$lang"

                    val track = DashTrack(tag, initUrl, mediaStr, startNum, segDur, timescale, durationSec, lang, bw)

                    if (contentType == "video") videoTracks.add(track)
                    else if (contentType == "audio") audioTracks.add(track)
                    else if (contentType == "text" || contentType == "subtitles") subTracks.add(track)
                }
            }
        }

        val chosenVideo = if (DownloadManager.selQualityTag.isNotBlank()) {
            videoTracks.find { it.tag == DownloadManager.selQualityTag } ?: videoTracks.maxByOrNull { it.bw }
        } else {
            videoTracks.maxByOrNull { it.bw }
        }

        val chosenAudios = if (DownloadManager.selAudioLangs.isEmpty()) audioTracks else audioTracks.filter { it.lang in DownloadManager.selAudioLangs }
        val chosenSubs = if (DownloadManager.selSubLangs.isEmpty()) subTracks else subTracks.filter { it.lang in DownloadManager.selSubLangs }

        fun prepareDashTrack(track: DashTrack, folderName: String): DownloadManager.ResolvedTrack {
            val dir = File(workDir, folderName).apply { mkdirs() }
            val trackJobs = mutableListOf<Pair<String, File>>()
            val playlistLines = mutableListOf<String>()

            if (track.initUrl.isNotBlank()) {
                val initFull = if (track.initUrl.startsWith("http")) track.initUrl else baseUrl + track.initUrl
                val initFile = File(dir, "init.mp4")
                DownloadManager.totalSegmentsToDownload.incrementAndGet()
                trackJobs.add(Pair(initFull, initFile))
                playlistLines.add("#EXT-X-MAP:URI=\"init.mp4\"")
            }

            val segCount = ceil((track.totalDurationSec * track.timescale) / track.duration.toDouble()).toInt()
            val regex = Regex("\\\$Number(?:%0(\\d+)d)?\\\$")
            val numberTarget = "\$Number\$"
            
            // Calculate exact duration for #EXTINF tag required by FFmpeg
            val extInfDur = track.duration.toDouble() / track.timescale.toDouble()
            val targetDuration = ceil(extInfDur).toInt().coerceAtLeast(1)

            for (i in 0 until segCount) {
                val num = track.startNumber + i
                var mediaUrl = track.segTemplate
                
                val match = regex.find(mediaUrl)
                if (match != null) {
                    val padding = match.groupValues[1]
                    val numStr = if (padding.isNotEmpty()) String.format("%0${padding.toInt()}d", num) else num.toString()
                    mediaUrl = mediaUrl.replace(match.value, numStr)
                } else {
                    mediaUrl = mediaUrl.replace(numberTarget, num.toString())
                }

                val fullUri = if (mediaUrl.startsWith("http")) mediaUrl else baseUrl + mediaUrl
                val fileName = "seg_$num.m4s"
                val segFile = File(dir, fileName)
                DownloadManager.totalSegmentsToDownload.incrementAndGet()
                trackJobs.add(Pair(fullUri, segFile))
                
                // Add valid EXTINF tag before segment name so FFmpeg doesn't reject it
                playlistLines.add("#EXTINF:$extInfDur,")
                playlistLines.add(fileName)
            }

            val localMpd = File(dir, "playlist.m3u8")
            localMpd.writeText(buildString {
                append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:$targetDuration\n#EXT-X-PLAYLIST-TYPE:VOD\n")
                append(playlistLines.joinToString("\n"))
                append("\n#EXT-X-ENDLIST\n")
            })

            return DownloadManager.ResolvedTrack(track.tag, track.lang, localMpd, trackJobs, null)
        }

        val vTrack = prepareDashTrack(chosenVideo!!, "video")
        val aTracks = chosenAudios.mapIndexed { idx, it -> prepareDashTrack(it, "audio_${it.lang}_$idx") }
        val sTracks = chosenSubs.mapIndexed { idx, it -> prepareDashTrack(it, "subtitle_${it.lang}_$idx") }

        val extSubFiles = mutableListOf<File>()
        if (DownloadManager.lastExternalSubs.isNotEmpty()) {
            val extDir = File(workDir, "ext_caption").apply { mkdirs() }
            DownloadManager.lastExternalSubs.forEachIndexed { idx, extUrl ->
                val ext = DownloadManager.detectExtension(extUrl, "vtt")
                extSubFiles.add(File(extDir, "ext_sub_$idx$ext"))
            }
        }

        return DownloadManager.DownloadPlan(vTrack, aTracks, sTracks, extSubFiles)
    }
}