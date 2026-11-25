package com.lagradost.cloudstream3.network

import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.AnyThread
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.nicehttp.Requests.Companion.await
import com.lagradost.nicehttp.cookies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@AnyThread
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = setOf(403, 429, 503)
        private val CLOUDFLARE_SERVERS = setOf("cloudflare-nginx", "cloudflare")

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val idx = it.indexOf('=')
                if (idx <= 0) "" to ""
                else it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    init {
        safe {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
            }
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = ConcurrentHashMap()
    private val hostLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(host: String): Mutex = hostLocks.computeIfAbsent(host) { Mutex() }

    /**
     * Gets the headers with cookies, webview user agent included!
     */
    fun getCookieHeaders(url: String): Headers {
        val userAgentHeaders = WebViewResolver.getWebViewUserAgent()?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        val host = runCatching { URI(url).host }.getOrNull()
        return getHeaders(userAgentHeaders, host?.let { savedCookies[it] } ?: emptyMap())
    }

    private fun isCloudflareChallenge(response: Response): Boolean {
        if (response.code !in ERROR_CODES) return false
        val server = response.header("Server")?.lowercase().orEmpty()
        val mitigated = response.header("cf-mitigated")?.lowercase().orEmpty()
        val hasCfRay = response.header("cf-ray") != null
        return server in CLOUDFLARE_SERVERS ||
            server.contains("cloudflare") ||
            mitigated == "challenge" ||
            (hasCfRay && (response.code == 403 || response.code == 503))
    }

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val host = request.url.host

        // If we already have cached cookies for this host, try using them first.
        val cached = savedCookies[host]
        if (cached != null) {
            val cachedResponse = try {
                proceed(request, cached)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Cached cookie request failed for $host", t)
                null
            }

            if (cachedResponse != null && !isCloudflareChallenge(cachedResponse)) {
                return@runBlocking cachedResponse
            }
            // Cached cf_clearance expired or rejected — evict and solve fresh!
            cachedResponse?.close()
            savedCookies.remove(host)
        }

        val initialResponse = chain.proceed(request)
        if (!isCloudflareChallenge(initialResponse)) {
            return@runBlocking initialResponse
        }

        initialResponse.close()

        // Serialize solving per host so parallel requests share the freshly solved cookie.
        val solvedResponse = lockFor(host).withLock {
            val freshCached = savedCookies[host]
            if (freshCached != null) {
                val retry = runCatching { proceed(request, freshCached) }.getOrNull()
                if (retry != null && !isCloudflareChallenge(retry)) {
                    return@withLock retry
                }
                retry?.close()
                savedCookies.remove(host)
            }
            bypassCloudflare(request)
        }

        if (solvedResponse != null) {
            Log.d(TAG, "Succeeded bypassing cloudflare: ${request.url}")
            return@runBlocking solvedResponse
        }

        debugWarning({ true }) { "Failed cloudflare at: ${request.url}" }
        return@runBlocking chain.proceed(request)
    }

    private fun getWebViewCookie(url: String): String? {
        return safe {
            val cm = CookieManager.getInstance()
            cm?.flush()
            cm?.getCookie(url)
        }
    }

    /**
     * Returns true if cf_clearance cookies were successfully fetched from CookieManager.
     * Also saves the cookies.
     */
    private fun trySolveWithSavedCookies(request: Request): Boolean {
        val url = request.url.toString()
        val cookie = getWebViewCookie(url) ?: getWebViewCookie("${request.url.scheme}://${request.url.host}/")
        return cookie?.let { raw ->
            val solved = raw.contains("cf_clearance")
            if (solved) {
                savedCookies[request.url.host] = parseCookieMap(raw)
            }
            solved
        } ?: false
    }

    private suspend fun proceed(request: Request, cookies: Map<String, String>): Response {
        val userAgentMap = WebViewResolver.getWebViewUserAgent()?.let {
            mapOf("user-agent" to it)
        } ?: emptyMap()

        val headers = getHeaders(request.headers.toMap() + userAgentMap, cookies + request.cookies)
        return app.baseClient.newCall(
            request.newBuilder()
                .headers(headers)
                .build()
        ).await()
    }

    private suspend fun bypassCloudflare(request: Request): Response? {
        val url = request.url.toString()

        if (!trySolveWithSavedCookies(request)) {
            Log.d(TAG, "Loading webview to solve cloudflare for ${request.url}")
            WebViewResolver(
                // Never exit based on url alone — exit as soon as cf_clearance is obtained
                interceptUrl = Regex(".^"),
                // Cloudflare requires the real WebView default user agent
                userAgent = null,
                // Cannot use okhttp (intercepting breaks browser fingerprint & cookies)
                useOkhttp = false,
                additionalUrls = listOf(Regex(".")),
                timeout = 35_000L
            ).resolveUsingWebView(
                url = url,
                requestCallBack = { trySolveWithSavedCookies(request) }
            )
            // One final cookie check in case cf_clearance landed right before exit
            trySolveWithSavedCookies(request)
        }

        val cookies = savedCookies[request.url.host] ?: return null
        return proceed(request, cookies)
    }
}