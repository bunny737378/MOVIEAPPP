package com.xtsream.app.downloader

object NativeDownloader {
    init {
        try {
            System.loadLibrary("native-downloader")
        } catch (e: Exception) {}
    }

    external fun getProgressNative(): Long
    external fun getStatusNative(): Int
    external fun startDownloadNative(url: String, outPath: String, headersJson: String, streamIndices: String)
}
