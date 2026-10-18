package com.xtsream.app.downloader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

object DownloadManager {

    var activeTitle by mutableStateOf("")
    var downloadStatus by mutableIntStateOf(0)
    var downloadedMb by mutableStateOf("0.0 MB")
    var totalMb by mutableStateOf("")
    var downloadSpeed by mutableStateOf("0 KB/s")
    var progressPercent by mutableIntStateOf(0)
    var mergeProgressPercent by mutableIntStateOf(0)
    var etaText by mutableStateOf("--")

    const val THREADS = 20
    const val MAX_RETRIES = 5
    const val RETRY_DELAY_MS = 1000L
    const val CONNECT_TIMEOUT_MS = 15000
    const val READ_TIMEOUT_MS = 20000

    val LANG3 = mapOf(
        "hi" to "hin", "en" to "eng", "ja" to "jpn", "ta" to "tam",
        "te" to "tel", "ml" to "mal", "bn" to "ben", "mr" to "mar",
        "gu" to "guj", "pa" to "pan", "ur" to "urd", "ko" to "kor",
        "zh" to "zho", "es" to "spa", "fr" to "fra", "de" to "deu",
        "pt" to "por", "ru" to "rus", "ar" to "ara", "th" to "tha",
        "vi" to "vie", "id" to "ind", "ms" to "msa", "tr" to "tur",
        "it" to "ita", "nl" to "nld", "pl" to "pol", "kn" to "kan"
    )

    var downloadJob: Job? = null
    val globalBytesDownloaded = AtomicLong(0L)
    val segmentsDownloadedCount = AtomicInteger(0)
    val totalSegmentsToDownload = AtomicInteger(0)
    var lastTime = 0L
    var bytesSinceLast = 0L
    var startTime = 0L

    var lastUrlStr = ""
    var lastProviderId = ""
    var lastSeriesTitle = ""
    var lastEpisodeTitle = ""
    var lastEpisodeId = ""
    var lastServerName = ""
    var lastHeaders = mapOf<String, String>()
    var lastExternalSubs = listOf<String>()
    var lastStreamType = ""

    var selQualityTag = ""
    var selAudioLangs: List<String> = emptyList()
    var selSubLangs: List<String> = emptyList()
    var lastThumbUrl = ""

    val aesByTrack = mutableMapOf<String, Triple<ByteArray?, Int, ByteArray?>?>()

    data class ResolvedTrack(val tag: String, val lang: String, val playlistFile: File, val jobs: List<Pair<String, File>>, val aes: Triple<ByteArray?, Int, ByteArray?>? = null)
    data class DownloadPlan(val videoTrack: ResolvedTrack, val audioTracks: List<ResolvedTrack>, val subTracks: List<ResolvedTrack>, val extSubFiles: List<File>)

    fun resolutionToTag(res: String): String {
        if (!res.contains("x")) return ""
        val h = res.substringAfter("x").toIntOrNull() ?: return ""
        return if (h > 0) "${h}p" else ""
    }

    fun getFinalFileName(
        providerId: String,
        seriesTitle: String,
        episodeTitle: String,
        episodeId: String,
        serverName: String,
        qualityTag: String
    ): String {
        val safeEpTitle = episodeTitle.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
        val safeEpId = episodeId.replace(Regex("[^a-zA-Z0-9]"), "_")
        val safeServer = serverName.replace(Regex("[^a-zA-Z0-9]"), "_")
        val safeQuality = qualityTag.replace(Regex("[^a-zA-Z0-9]"), "_")

        var name = safeEpTitle
        if (safeEpId.isNotBlank()) name += "_$safeEpId"
        if (safeServer.isNotBlank()) name += "_$safeServer"
        if (safeQuality.isNotBlank()) name += "_$safeQuality"
        return name
    }

    fun isAlreadyDownloadedOrActive(
        context: Context,
        providerId: String,
        seriesTitle: String,
        episodeTitle: String,
        episodeId: String,
        serverName: String,
        qualityTag: String
    ): Boolean {
        val safeEpTitle = episodeTitle.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
        val finalFileName = getFinalFileName(providerId, seriesTitle, episodeTitle, episodeId, serverName, qualityTag)
        val safeProvider = providerId.replace(Regex("[^a-zA-Z0-9]"), "_")
        val safeSeries = seriesTitle.replace(Regex("[^a-zA-Z0-9]"), "_")
        
        val targetDir = File(File(File(File(context.getExternalFilesDir(null), "downloads"), safeProvider), safeSeries), safeEpTitle)
        val outFile = File(targetDir, "$finalFileName.mkv")

        if (outFile.exists() && outFile.length() > 0) return true
        
        if ((downloadStatus == 1 || downloadStatus == 2 || downloadStatus == 4) && 
            lastProviderId == providerId && lastSeriesTitle == seriesTitle && 
            lastEpisodeId == episodeId && lastServerName == serverName && selQualityTag == qualityTag) {
            return true
        }
        
        return false
    }

    fun startDownload(
        context: Context,
        masterUrl: String,
        providerId: String,
        seriesTitle: String,
        episodeTitle: String,
        episodeId: String,
        serverName: String,
        headers: Map<String, String>,
        streamType: String,
        qualityTag: String = "",
        audioLangs: List<String> = emptyList(),
        subLangs: List<String> = emptyList(),
        externalSubs: List<String> = emptyList(),
        thumbUrl: String = ""
    ) {
        if (downloadStatus == 1 || downloadStatus == 2) {
            return
        }
        lastUrlStr = masterUrl
        lastProviderId = providerId
        lastSeriesTitle = seriesTitle
        lastEpisodeTitle = episodeTitle.ifBlank { "Ep" }
        lastEpisodeId = episodeId
        lastServerName = serverName
        lastHeaders = headers
        lastExternalSubs = externalSubs
        lastStreamType = streamType.lowercase()
        selQualityTag = qualityTag
        selAudioLangs = audioLangs
        selSubLangs = subLangs
        lastThumbUrl = thumbUrl
        
        val displayQuality = if (selQualityTag.isNotBlank()) " [$selQualityTag]" else ""
        val displayTitle = "${lastEpisodeTitle}$displayQuality".trim()
        resetStats(displayTitle)
        
        val safeProvider = providerId.replace(Regex("[^a-zA-Z0-9]"), "_")
        val safeSeries = seriesTitle.replace(Regex("[^a-zA-Z0-9]"), "_")
        val safeEpTitleFolder = lastEpisodeTitle.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
        
        val finalFileName = getFinalFileName(providerId, seriesTitle, episodeTitle, episodeId, serverName, qualityTag)
        
        val targetDir = File(File(context.getExternalFilesDir(null), "downloads"), safeProvider).apply { mkdirs() }
        val seriesDir = File(targetDir, safeSeries).apply { mkdirs() }
        val epDir = File(seriesDir, safeEpTitleFolder).apply { mkdirs() }
        
        saveInfoFile(epDir, finalFileName, displayTitle, safeEpTitleFolder)
        
        processDownload(context)
    }
    
    private fun saveInfoFile(targetDir: File, finalFileName: String, displayTitle: String, safeEpTitle: String) {
        val infoFile = File(targetDir, "$finalFileName.info")
        val json = JSONObject().apply {
            put("displayTitle", displayTitle)
            put("providerId", lastProviderId)
            put("seriesTitle", lastSeriesTitle)
            put("episodeTitle", lastEpisodeTitle)
            put("episodeId", lastEpisodeId)
            put("serverName", lastServerName)
            put("qualityTag", selQualityTag)
            put("masterUrl", lastUrlStr)
            put("streamType", lastStreamType)
            put("headers", JSONObject(lastHeaders))
            put("audioLangs", JSONArray(selAudioLangs))
            put("subLangs", JSONArray(selSubLangs))
            put("externalSubs", JSONArray(lastExternalSubs))
            put("thumbUrl", lastThumbUrl)
        }
        infoFile.writeText(json.toString())
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val thumbFile = File(targetDir, "$safeEpTitle.jpg")
                if (!thumbFile.exists() && lastThumbUrl.isNotBlank()) {
                    val bytes = fetchBytes(lastThumbUrl, emptyMap())
                    thumbFile.writeBytes(bytes)
                }
            } catch(e:Exception){}
        }
    }

    fun resumeDownload(context: Context) {
        if (downloadStatus != 4 && downloadStatus != 0) return
        downloadStatus = 1
        downloadSpeed = "Resuming..."
        lastTime = System.currentTimeMillis()
        bytesSinceLast = 0L
        
        segmentsDownloadedCount.set(0)
        totalSegmentsToDownload.set(0)
        
        CoroutineScope(Dispatchers.IO).launch {
            val finalFileName = getFinalFileName(lastProviderId, lastSeriesTitle, lastEpisodeTitle, lastEpisodeId, lastServerName, selQualityTag)
            val safeProvider = lastProviderId.replace(Regex("[^a-zA-Z0-9]"), "_")
            val safeSeries = lastSeriesTitle.replace(Regex("[^a-zA-Z0-9]"), "_")
            val safeEpTitleFolder = lastEpisodeTitle.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
            
            val rootDownloads = File(context.getExternalFilesDir(null), "downloads")
            val epDir = File(File(File(rootDownloads, safeProvider), safeSeries), safeEpTitleFolder)
            val workDir = File(epDir, "${finalFileName}_tmp")
            
            var existingBytes = 0L
            if (workDir.exists()) {
                workDir.walkTopDown().filter { it.isFile }.forEach { existingBytes += it.length() }
            }
            globalBytesDownloaded.set(existingBytes)
            
            processDownload(context)
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadStatus = 4
        downloadSpeed = "Paused"
    }

    fun resetStats(title: String) {
        activeTitle = title
        downloadStatus = 1
        progressPercent = 0
        mergeProgressPercent = 0
        globalBytesDownloaded.set(0L)
        segmentsDownloadedCount.set(0)
        totalSegmentsToDownload.set(0)
        lastTime = System.currentTimeMillis()
        startTime = System.currentTimeMillis()
        bytesSinceLast = 0L
        downloadedMb = "0.0 MB"
        totalMb = ""
        downloadSpeed = "0 KB/s"
        etaText = "--"
    }

    fun parseAttributes(line: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("""([A-Z0-9\-]+)=(".*?"|[^,]*)""")
        regex.findAll(line).forEach { match ->
            map[match.groupValues[1]] = match.groupValues[2].trim('"')
        }
        return map
    }

    fun detectExtension(url: String, fallback: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        return when {
            path.endsWith(".vtt")    -> ".vtt"
            path.endsWith(".webvtt") -> ".vtt"
            path.endsWith(".srt")    -> ".srt"
            path.endsWith(".m4s")    -> ".m4s"
            path.endsWith(".mp4")    -> ".mp4"
            path.endsWith(".m4a")    -> ".m4a"
            path.endsWith(".aac")    -> ".aac"
            path.endsWith(".ts")     -> ".ts"
            else -> ".$fallback"
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun processDownload(context: Context) {
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val rootDownloads = File(context.getExternalFilesDir(null), "downloads")
                val safeProvider = lastProviderId.replace(Regex("[^a-zA-Z0-9]"), "_")
                val safeSeries = lastSeriesTitle.replace(Regex("[^a-zA-Z0-9]"), "_")
                val safeEpTitleFolder = lastEpisodeTitle.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
                val finalFileName = getFinalFileName(lastProviderId, lastSeriesTitle, lastEpisodeTitle, lastEpisodeId, lastServerName, selQualityTag)
                
                val targetDir = File(File(File(rootDownloads, safeProvider), safeSeries), safeEpTitleFolder)
                if (!targetDir.exists()) targetDir.mkdirs()

                val workDir = File(targetDir, "${finalFileName}_tmp")

                if (lastStreamType.contains("mkv") || lastStreamType.contains("mp4")) {
                    com.xtsream.app.downloader.handler.MkvHandler.downloadDirect(context, finalFileName, targetDir)
                    return@launch
                }

                if (!workDir.exists()) workDir.mkdirs()
                val masterUrl = lastUrlStr
                val content = fetchString(masterUrl, lastHeaders)

                val plan = if (lastStreamType.contains("dash") || lastStreamType.contains("mpd")) {
                    com.xtsream.app.downloader.handler.DashHandler.planDash(masterUrl, content, workDir)
                } else {
                    com.xtsream.app.downloader.handler.ManifestHandler.planHls(masterUrl, content, workDir)
                }

                executePlan(context, plan, finalFileName, targetDir, workDir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { downloadStatus = 4 }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun executePlan(context: Context, plan: DownloadPlan, safeTitle: String, rootDownloads: File, workDir: File) {
        val dispatcher = Dispatchers.IO.limitedParallelism(THREADS)
        val allJobs = mutableListOf<Deferred<Boolean>>()
        
        val extSubFilesToDownload = mutableListOf<Pair<String, File>>()
        plan.extSubFiles.forEachIndexed { idx, file ->
            extSubFilesToDownload.add(Pair(lastExternalSubs[idx], file))
        }

        val allTracks = listOf(plan.videoTrack) + plan.audioTracks + plan.subTracks
        
        for (track in allTracks) {
            aesByTrack[track.tag] = track.aes
            track.jobs.forEach { (segUrl, segFile) ->
                allJobs.add(CoroutineScope(Dispatchers.IO).async(dispatcher) {
                    if (!segFile.exists() || segFile.length() == 0L) {
                        val iv = track.aes?.third ?: ByteBuffer.allocate(16).putLong(8, 0L).array()
                        downloadSegmentWithRetries(segUrl, segFile, lastHeaders, track.aes?.first, iv)
                    } else {
                        segmentsDownloadedCount.incrementAndGet()
                        updateProgressTick()
                        true
                    }
                })
            }
        }

        extSubFilesToDownload.forEach { (extUrl, eFile) ->
            totalSegmentsToDownload.incrementAndGet()
            allJobs.add(CoroutineScope(Dispatchers.IO).async(dispatcher) {
                if (!eFile.exists() || eFile.length() == 0L) {
                    downloadSegmentWithRetries(extUrl, eFile, lastHeaders, null, null)
                } else {
                    segmentsDownloadedCount.incrementAndGet()
                    updateProgressTick()
                    true
                }
            })
        }

        val results = allJobs.awaitAll()
        val failedCount = results.count { !it }

        if (failedCount > 0) {
            withContext(Dispatchers.Main) { downloadSpeed = "Retrying $failedCount..." }
            for (track in allTracks) {
                for ((segUrl, segFile) in track.jobs) {
                    if (!segFile.exists() || segFile.length() == 0L) {
                        val iv = track.aes?.third ?: ByteBuffer.allocate(16).putLong(8, 0L).array()
                        downloadSegmentWithRetries(segUrl, segFile, lastHeaders, track.aes?.first, iv, 8)
                    }
                }
            }
            extSubFilesToDownload.forEach { (extUrl, eFile) ->
                if (!eFile.exists() || eFile.length() == 0L) {
                    downloadSegmentWithRetries(extUrl, eFile, lastHeaders, null, null, 8)
                }
            }
        }

        if (CoroutineScope(Dispatchers.IO).isActive) {
            withContext(Dispatchers.Main) {
                downloadStatus = 2
                downloadSpeed = "Merging..."
                progressPercent = 100
                mergeProgressPercent = 0
                etaText = "--"
            }

            val finalOut = File(rootDownloads, "$safeTitle.mkv")
            val success = muxWithFfmpeg(
                context = context,
                videoPlaylist = plan.videoTrack.playlistFile,
                audioPlaylists = plan.audioTracks.map { it.playlistFile },
                audioLangs = plan.audioTracks.map { LANG3[it.lang] ?: it.lang },
                subPlaylists = plan.subTracks.map { it.playlistFile },
                subLangs = plan.subTracks.map { LANG3[it.lang] ?: it.lang },
                extSubFiles = plan.extSubFiles,
                outFile = finalOut
            )

            if (success && finalOut.exists() && finalOut.length() > 0) {
                workDir.deleteRecursively()
                withContext(Dispatchers.Main) { downloadStatus = 3 }
            } else {
                withContext(Dispatchers.Main) { downloadStatus = -1 }
            }
        }
    }

    suspend fun muxWithFfmpeg(
        context: Context,
        videoPlaylist: File,
        audioPlaylists: List<File>,
        audioLangs: List<String>,
        subPlaylists: List<File>,
        subLangs: List<String>,
        extSubFiles: List<File>,
        outFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        val ffmpegPath = context.applicationInfo.nativeLibraryDir + "/libffmpeg.so"
        val nativeLibDir = context.applicationInfo.nativeLibraryDir

        if (!File(ffmpegPath).exists()) {
            return@withContext false
        }

        var totalDurationSec = 0.0
        try {
            videoPlaylist.readLines().forEach { line ->
                if (line.startsWith("#EXTINF:")) {
                    totalDurationSec += line.substringAfter(":").substringBefore(",").toDoubleOrNull() ?: 0.0
                }
            }
        } catch(e:Exception){}

        val cmd = mutableListOf<String>()
        cmd.add(ffmpegPath)
        cmd.add("-y")
        cmd.add("-i"); cmd.add(videoPlaylist.absolutePath)
        audioPlaylists.forEach { cmd.add("-i"); cmd.add(it.absolutePath) }
        subPlaylists.forEach { cmd.add("-i"); cmd.add(it.absolutePath) }
        extSubFiles.forEach { cmd.add("-i"); cmd.add(it.absolutePath) }

        cmd.add("-map"); cmd.add("0:v:0")
        for (i in audioPlaylists.indices) { cmd.add("-map"); cmd.add("${i + 1}:a:0") }
        val subStartIdx = 1 + audioPlaylists.size
        val totalSubs = subPlaylists.size + extSubFiles.size
        for (i in 0 until totalSubs) { cmd.add("-map"); cmd.add("${subStartIdx + i}:s:0") }

        cmd.add("-c:v"); cmd.add("copy")
        if (audioPlaylists.isNotEmpty()) { cmd.add("-c:a"); cmd.add("copy") }
        if (totalSubs > 0) { cmd.add("-c:s"); cmd.add("srt") }

        audioLangs.forEachIndexed { i, lang ->
            cmd.add("-metadata:s:a:$i"); cmd.add("language=$lang")
        }
        for (i in audioPlaylists.indices) {
            if (i == 0) { cmd.add("-disposition:a:$i"); cmd.add("default") }
            else { cmd.add("-disposition:a:$i"); cmd.add("0") }
        }
        subLangs.forEachIndexed { i, lang ->
            cmd.add("-metadata:s:s:$i"); cmd.add("language=$lang")
        }

        cmd.add(outFile.absolutePath)

        val pb = ProcessBuilder(cmd)
        pb.directory(videoPlaylist.parentFile)
        pb.environment()["LD_LIBRARY_PATH"] = nativeLibDir
        pb.redirectErrorStream(true)

        val proc = pb.start()
        val reader = BufferedReader(InputStreamReader(proc.inputStream))
        val timeRegex = Regex("""time=(\d{2}):(\d{2}):(\d{2}\.\d+)""")
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val match = timeRegex.find(line!!)
            if (match != null && totalDurationSec > 0) {
                val h = match.groupValues[1].toInt()
                val m = match.groupValues[2].toInt()
                val s = match.groupValues[3].toDouble()
                val currentSec = h * 3600 + m * 60 + s
                val pct = ((currentSec / totalDurationSec) * 100).toInt().coerceIn(0, 100)
                withContext(Dispatchers.Main) { mergeProgressPercent = pct }
            }
        }
        val exitCode = proc.waitFor()
        exitCode == 0
    }

    suspend fun downloadSegmentWithRetries(
        urlStr: String,
        outFile: File,
        headers: Map<String, String>,
        aesKey: ByteArray?,
        aesIv: ByteArray?,
        maxAttempts: Int = MAX_RETRIES
    ): Boolean {
        var attempt = 0

        while (attempt < maxAttempts) {
            attempt++
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Accept-Encoding", "identity")
                conn.setRequestProperty("Connection", "keep-alive")

                conn.inputStream.use { input ->
                    val tmp = File(outFile.parentFile, outFile.name + ".part")
                    FileOutputStream(tmp).use { fos ->
                        val output = if (aesKey != null && aesIv != null) {
                            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(aesIv))
                            CipherOutputStream(fos, cipher)
                        } else fos
                        output.use { out ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                updateBytesTick(bytesRead)
                            }
                        }
                    }
                    if (tmp.exists() && tmp.length() > 0) tmp.renameTo(outFile)
                }

                segmentsDownloadedCount.incrementAndGet()
                updateProgressTick()
                return true
            } catch (e: Exception) {
                if (attempt < maxAttempts) delay(RETRY_DELAY_MS * (1L shl (attempt - 1)).coerceAtMost(8000L))
            }
        }
        return false
    }

    fun parseAesKey(
        baseUrl: String,
        content: String,
        headers: Map<String, String>
    ): Triple<ByteArray?, Int, ByteArray?> {
        val baseUrlPrefix = baseUrl.substringBeforeLast("/") + "/"
        var aesKey: ByteArray? = null
        var aesIv: ByteArray? = null
        var mediaSequence = 0
        
        val seqMatcher = Regex("""#EXT-X-MEDIA-SEQUENCE:(\d+)""").find(content)
        if (seqMatcher != null) {
            mediaSequence = seqMatcher.groupValues[1].toInt()
        }
        
        val keyMatcher = Regex("""#EXT-X-KEY:METHOD=AES-128,URI=["']([^"']+)["'](?:,IV=(0x[0-9a-fA-F]+))?""").find(content)
        if (keyMatcher != null) {
            val keyUrl = keyMatcher.groupValues[1]
            val ivStr = keyMatcher.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            val fullKeyUrl = if (keyUrl.startsWith("http")) keyUrl else baseUrlPrefix + keyUrl
            aesKey = fetchBytes(fullKeyUrl, headers)
            if (ivStr != null) {
                val cleanIv = ivStr.removePrefix("0x").padStart(32, '0')
                aesIv = cleanIv.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
        }
        return Triple(aesKey, mediaSequence, aesIv)
    }

    fun updateBytesTick(bytesRead: Int) {
        val total = globalBytesDownloaded.addAndGet(bytesRead.toLong())
        synchronized(this) {
            bytesSinceLast += bytesRead
            val now = System.currentTimeMillis()
            val diff = now - lastTime
            if (diff >= 500) {
                val speedKbps = (bytesSinceLast / 1024f) / (diff / 1000f)
                val speedStr = if (speedKbps > 1024) String.format("%.2f MB/s", speedKbps / 1024f)
                else String.format("%.0f KB/s", speedKbps)
                val elapsed = (now - startTime) / 1000f
                val done = segmentsDownloadedCount.get()
                val totalSeg = totalSegmentsToDownload.get()
                val eta = if (done > 0 && totalSeg > 0) {
                    val rate = done / elapsed.coerceAtLeast(1f)
                    if (rate > 0) ((totalSeg - done) / rate).toInt() else -1
                } else -1
                CoroutineScope(Dispatchers.Main).launch {
                    downloadedMb = String.format("%.1f MB", total / (1024f * 1024f))
                    if (!lastStreamType.contains("mkv") && !lastStreamType.contains("mp4")) {
                        totalMb = ""
                    }
                    downloadSpeed = speedStr
                    etaText = if (eta >= 0) "${eta}s" else "--"
                }
                lastTime = now
                bytesSinceLast = 0
            }
        }
    }

    fun updateProgressTick() {
        val done = segmentsDownloadedCount.get()
        val total = totalSegmentsToDownload.get()
        if (total > 0) {
            val pct = ((done.toFloat() / total) * 100).roundToInt()
            CoroutineScope(Dispatchers.Main).launch { progressPercent = pct }
        }
    }

    fun fetchString(urlStr: String, headers: Map<String, String>): String =
        String(fetchBytes(urlStr, headers))

    fun fetchBytes(urlStr: String, headers: Map<String, String>): ByteArray {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.setRequestProperty("Accept-Encoding", "identity")
        return conn.inputStream.use { it.readBytes() }
    }
}
