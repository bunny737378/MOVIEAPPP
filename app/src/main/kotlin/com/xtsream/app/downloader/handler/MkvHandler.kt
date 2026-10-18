package com.xtsream.app.downloader.handler

import android.content.Context
import com.xtsream.app.downloader.DownloadManager
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MkvHandler {
    suspend fun downloadDirect(context: Context, finalFileName: String, targetDir: File) {
        val outFile = File(targetDir, "$finalFileName.mkv")
        val tmpFile = File(targetDir, "$finalFileName.part")
        
        var downloaded = 0L
        val isResume = tmpFile.exists() && tmpFile.length() > 0
        if (isResume) {
            downloaded = tmpFile.length()
        }

        withContext(Dispatchers.Main) {
            DownloadManager.downloadStatus = 1
            DownloadManager.totalMb = "Starting..."
            DownloadManager.globalBytesDownloaded.set(downloaded)
        }

        try {
            val url = URL(DownloadManager.lastUrlStr)
            val conn = url.openConnection() as HttpURLConnection
            DownloadManager.lastHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            
            // 🔥 Yahan HTTP Range header lga diya jisse resume wahi se hoga
            if (isResume) {
                conn.setRequestProperty("Range", "bytes=$downloaded-")
            }
            
            conn.connectTimeout = DownloadManager.CONNECT_TIMEOUT_MS
            conn.readTimeout = DownloadManager.READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true

            val contentLength = conn.contentLength.toLong()
            val totalLength = if (contentLength > 0) downloaded + contentLength else -1L

            if (totalLength > 0) {
                withContext(Dispatchers.Main) { 
                    DownloadManager.totalMb = String.format("%.1f MB", totalLength / (1024f * 1024f)) 
                }
            }

            conn.inputStream.use { input ->
                // 🔥 append = isResume flag se purani file overwrite nahi hogi
                FileOutputStream(tmpFile, isResume).use { out ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!CoroutineScope(Dispatchers.IO).isActive) throw CancellationException()
                        out.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        DownloadManager.updateBytesTick(bytesRead)
                        
                        if (totalLength > 0) {
                            val pct = ((downloaded.toFloat() / totalLength) * 100).toInt()
                            if (pct != DownloadManager.progressPercent) {
                                withContext(Dispatchers.Main) { DownloadManager.progressPercent = pct }
                            }
                        }
                    }
                }
            }
            
            if (tmpFile.exists() && tmpFile.length() > 0) {
                tmpFile.renameTo(outFile)
            }
            
            withContext(Dispatchers.Main) {
                DownloadManager.downloadStatus = 3
                DownloadManager.progressPercent = 100
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { DownloadManager.downloadStatus = 4 }
        }
    }
}
