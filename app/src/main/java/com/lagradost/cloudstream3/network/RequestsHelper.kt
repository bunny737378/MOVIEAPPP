package com.lagradost.cloudstream3.network

import android.content.Context
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ignoreAllSSLErrors
import com.xtsream.app.R
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import java.io.File
import java.security.Security
import java.util.concurrent.TimeUnit

private val clientInitLock = Any()

@Volatile
private var sharedHttpCache: Cache? = null

private fun getOrCreateHttpCache(context: Context): Cache {
    sharedHttpCache?.let { return it }
    return synchronized(clientInitLock) {
        sharedHttpCache ?: Cache(
            directory = File(context.applicationContext.cacheDir, "http_cache"),
            maxSize = 50L * 1024L * 1024L // 50 MiB
        ).also { sharedHttpCache = it }
    }
}

fun clearSharedHttpCache(): Long {
    val cache = sharedHttpCache ?: return 0L
    val before = runCatching { cache.size() }.getOrDefault(0L)
    runCatching { cache.evictAll() }
    return before
}

// Backwards compatible constructor, mark as deprecated later
fun Requests.initClient(context: Context) {
    this.baseClient = buildDefaultClient(context, false)
}

/** Only use ignoreSSL if you know what you are doing */
fun Requests.initClient(context: Context, ignoreSSL: Boolean = false) {
    this.baseClient = buildDefaultClient(context, ignoreSSL)
}

// Backwards compatible constructor, mark as deprecated later
fun buildDefaultClient(context: Context): OkHttpClient {
    return buildDefaultClient(context, false)
}

/** Only use ignoreSSL if you know what you are doing */
fun buildDefaultClient(context: Context, ignoreSSL: Boolean = false): OkHttpClient {
    safe { Security.insertProviderAt(Conscrypt.newProvider(), 1) }

    val appContext = context.applicationContext
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(appContext)
    val dns = settingsManager.getInt(appContext.getString(R.string.dns_key), 0)

    val builder = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(16, 2, TimeUnit.MINUTES))
        .apply {
            if (ignoreSSL) {
                ignoreAllSSLErrors()
            }
        }
        // Share a single DiskLruCache instance across clients to avoid lock/file corruption
        .cache(getOrCreateHttpCache(appContext))
        .apply {
            when (dns) {
                1 -> addGoogleDns()
                2 -> addCloudFlareDns()
                4 -> addAdGuardDns()
                5 -> addDNSWatchDns()
                6 -> addQuad9Dns()
                7 -> addDnsSbDns()
                8 -> addCanadianShieldDns()
            }
        }

    return builder.build()
}

private val DEFAULT_HEADERS = mapOf("user-agent" to USER_AGENT)

/**
 * Set headers > Set cookies > Default headers > Default Cookies
 */
fun getHeaders(
    headers: Map<String, String>,
    cookie: Map<String, String>
): Headers {
    val cookieMap =
        if (cookie.isNotEmpty()) mapOf(
            "Cookie" to cookie.entries.joinToString(" ") {
                "${it.key}=${it.value};"
            }
        ) else mapOf()
    val tempHeaders = (DEFAULT_HEADERS + headers + cookieMap)
    return tempHeaders.toHeaders()
}