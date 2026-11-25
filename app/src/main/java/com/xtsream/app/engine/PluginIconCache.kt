package com.xtsream.app.engine

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Local persistent icon cache for CloudStream extensions & Stremio addons.
 * Stores downloaded icons in filesDir/plugin_icons/ so installed plugin icons
 * load reliably even offline or when remote hosts throttle.
 */
object PluginIconCache {
    private const val TAG = "PluginIconCache"
    private const val DIR_NAME = "plugin_icons"

    private fun cacheDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }

    private fun hashKey(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    fun getCachedIconFile(context: Context, keyOrUrl: String?): File? {
        if (keyOrUrl.isNullOrBlank()) return null
        val file = File(cacheDir(context), "${hashKey(keyOrUrl)}.img")
        return file.takeIf { it.isFile && it.length() > 32L }
    }

    suspend fun cacheIcon(context: Context, iconUrl: String?): File? = withContext(Dispatchers.IO) {
        if (iconUrl.isNullOrBlank()) return@withContext null
        val trimmed = iconUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }

        val target = File(cacheDir(context), "${hashKey(trimmed)}.img")
        if (target.isFile && target.length() > 32L) {
            return@withContext target
        }

        return@withContext try {
            val response = app.get(
                url = trimmed,
                cacheTime = 60,
                cacheUnit = TimeUnit.MINUTES
            )
            val bytes = response.okhttpResponse.body.bytes()
            if (bytes.size > 32) {
                val tmp = File(target.parentFile, "${target.name}.tmp")
                tmp.writeBytes(bytes)
                if (target.exists()) target.delete()
                tmp.renameTo(target)
                target
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Icon cache miss for $trimmed: ${t.message}")
            null
        }
    }
}
