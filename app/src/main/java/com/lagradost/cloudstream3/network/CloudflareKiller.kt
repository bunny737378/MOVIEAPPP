package com.lagradost.cloudstream3.network

import android.webkit.CookieManager
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Hikari-grade CloudflareKiller matching upstream CloudStream3's exact bytecode surface:
 * - NEVER wipes CookieManager on `init` (preserves earned `cf_clearance` cookies!)
 * - Aligns User-Agent with the real WebView UA that minted `cf_clearance`
 * - Uses shared `CloudflareSolver` for per-host locked headless solving + interactive fallback
 */
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";")
                .mapNotNull { part ->
                    val eq = part.indexOf('=')
                    if (eq <= 0) return@mapNotNull null
                    part.substring(0, eq).trim() to part.substring(eq + 1).trim()
                }
                .filter { it.first.isNotBlank() && it.second.isNotBlank() }
                .toMap()
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = ConcurrentHashMap()

    fun getCookieHeaders(url: String): Headers = runCatching {
        val builder = Headers.Builder()
        val ua = CloudflareSession.effectiveUserAgent()
        if (ua.isNotBlank()) builder.add("user-agent", ua)
        savedCookies[hostOf(url)]?.forEach { (k, v) -> builder.add(k, v) }
        webViewCookie(url)?.takeIf { it.isNotBlank() }?.let { builder.add("Cookie", it) }
        builder.build()
    }.getOrDefault(Headers.Builder().build())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()
        val host = hostOf(url)

        val initialCookie = webViewCookie(url)
        val firstReq = applyJarCookie(request, initialCookie)
        val response = chain.proceed(firstReq)

        val isCf = !response.header("cf-mitigated").isNullOrBlank() ||
            ((response.code == 403 || response.code == 503 || response.code == 429) &&
                (response.header("Server")?.contains("cloudflare", ignoreCase = true) == true ||
                    !response.header("cf-ray").isNullOrBlank()))

        if (!isCf) return response

        // Try fresh jar cookie first; if still blocked, solve headlessly via CloudflareSolver
        val freshBefore = webViewCookie(url)
        if (!freshBefore.isNullOrBlank() && freshBefore != initialCookie && freshBefore.contains("cf_clearance")) {
            if (host != null) savedCookies[host] = parseCookieMap(freshBefore)
            response.close()
            return chain.proceed(applyJarCookie(request, freshBefore))
        }

        val solved = CloudflareSolver.solve(
            url = url,
            userAgent = CloudflareSession.effectiveUserAgent(),
            referer = request.header("Referer")
        )
        if (!solved) return response

        val freshAfter = webViewCookie(url)
        if (!freshAfter.isNullOrBlank() && host != null) {
            savedCookies[host] = parseCookieMap(freshAfter)
        }
        response.close()
        return chain.proceed(applyJarCookie(request, freshAfter))
    }

    private fun applyJarCookie(request: Request, jarCookie: String?): Request {
        if (jarCookie.isNullOrBlank()) return request
        val existing = request.header("Cookie")
        val merged = if (existing.isNullOrBlank()) jarCookie else "$jarCookie; $existing"
        val builder = request.newBuilder().header("Cookie", merged)
        if (jarCookie.contains("cf_clearance")) {
            builder.header("User-Agent", CloudflareSession.effectiveUserAgent())
        }
        return builder.build()
    }

    private fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()

    private fun webViewCookie(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
}