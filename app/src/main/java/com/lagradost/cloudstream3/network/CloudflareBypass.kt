package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.USER_AGENT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream

object CloudflareSession {
    @Volatile
    var webViewUserAgent: String = USER_AGENT
        private set

    @Volatile
    private var uaResolved: Boolean = false

    fun init(context: Context) {
        if (uaResolved) return
        uaResolved = true
        val ua = runCatching { WebSettings.getDefaultUserAgent(context.applicationContext) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        if (ua != null) {
            webViewUserAgent = ua
            WebViewResolver.webViewUserAgent = ua
        }
    }

    fun effectiveUserAgent(): String {
        if (!uaResolved) {
            val ctx = CommonActivity.activity ?: (getContext() as? Context)
            if (ctx != null) init(ctx)
        }
        return webViewUserAgent.ifBlank { USER_AGENT }
    }

    fun hostOf(url: String): String? =
        runCatching { URI(url).host?.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * Tracks hosts challenged by Cloudflare and coordinates interactive WebView verification
 * only when headless auto-solving encounters a hard Turnstile checkbox.
 */
object CloudflareVerifier {
    private val blockedHosts = ConcurrentHashMap<String, Long>()
    private val lastPromptedHosts = ConcurrentHashMap<String, Long>()
    private const val PROMPT_COOLDOWN_MS = 45_000L

    private val _pendingVerificationUrl = MutableStateFlow<String?>(null)
    val pendingVerificationUrl: StateFlow<String?> = _pendingVerificationUrl.asStateFlow()

    private val _verificationCompleteCounter = MutableStateFlow(0L)
    val verificationCompleteCounter: StateFlow<Long> = _verificationCompleteCounter.asStateFlow()

    val INTERSTITIAL_TITLES = listOf(
        "just a moment",
        "one moment",
        "attention required",
        "checking your browser",
        "performing security verification",
        "verify you are human"
    )

    val HARD_MARKERS = listOf(
        "just a moment",
        "one moment, please",
        "wsidchk",
        "cf_chl_",
        "cf_chl_opt",
        "cf-chl-opt",
        "challenge-platform",
        "cf-browser-verification",
        "checking your browser",
        "performing security verification",
        "verify you are human",
        "attention required",
        "you have been blocked",
        "cf-error-details",
        "cf-error-code"
    )

    fun isChallengeTitle(title: String?): Boolean {
        val t = title?.lowercase()?.trim().orEmpty()
        return t.isNotBlank() && INTERSTITIAL_TITLES.any { t.contains(it) }
    }

    fun clearanceFor(url: String): String? {
        val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
        return cookie?.takeIf { it.contains("cf_clearance") }
    }

    fun markBlocked(url: String) {
        val host = CloudflareSession.hostOf(url) ?: return
        val now = System.currentTimeMillis()
        blockedHosts[host] = now
        val lastPrompt = lastPromptedHosts[host] ?: 0L
        if (_pendingVerificationUrl.value == null && now - lastPrompt > PROMPT_COOLDOWN_MS) {
            lastPromptedHosts[host] = now
            _pendingVerificationUrl.value = url
        }
    }

    fun clearBlocked(host: String) {
        blockedHosts.remove(host.lowercase())
    }

    fun onInteractiveSolved(url: String) {
        CloudflareSession.hostOf(url)?.let { host ->
            clearBlocked(host)
            CloudflareSolver.markSolved(host)
        }
        runCatching { CookieManager.getInstance().flush() }
        _pendingVerificationUrl.value = null
        _verificationCompleteCounter.value += 1L
    }

    fun dismissVerification() {
        _pendingVerificationUrl.value = null
    }
}

/**
 * Hikari-grade headless WebView Cloudflare solver with per-host locking,
 * settle grace period, real WebView User-Agent alignment, and clean cleanup.
 */
object CloudflareSolver {
    private const val SOLVED_TTL_MS = 10 * 60_000L
    private const val POLL_MS = 300L
    private const val SETTLE_GRACE_MS = 2_800L

    private val solvedAt = ConcurrentHashMap<String, Long>()
    private val locks = ConcurrentHashMap<String, Any>()

    fun markSolved(host: String) {
        solvedAt[host.lowercase()] = System.currentTimeMillis()
    }

    fun recentlySolved(url: String): Boolean {
        val host = CloudflareSession.hostOf(url) ?: return false
        val at = solvedAt[host] ?: return false
        return System.currentTimeMillis() - at <= SOLVED_TTL_MS &&
            !CloudflareVerifier.clearanceFor(url).isNullOrBlank()
    }

    fun solve(
        url: String,
        userAgent: String = CloudflareSession.effectiveUserAgent(),
        referer: String? = null,
        timeoutMs: Long = 18_000L
    ): Boolean {
        val host = CloudflareSession.hostOf(url) ?: return false
        val lock = locks.getOrPut(host) { Any() }
        return synchronized(lock) {
            if (recentlySolved(url)) return@synchronized true
            val ok = runCatching { loadAndWait(url, userAgent, referer, timeoutMs) }
                .getOrDefault(false)
            if (ok) {
                solvedAt[host] = System.currentTimeMillis()
                CloudflareVerifier.clearBlocked(host)
                runCatching { CookieManager.getInstance().flush() }
            } else {
                CloudflareVerifier.markBlocked(url)
            }
            ok
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadAndWait(
        url: String,
        userAgent: String,
        referer: String?,
        timeoutMs: Long
    ): Boolean {
        val ctx: Context = CommonActivity.activity?.takeIf { !it.isFinishing && !it.isDestroyed }
            ?: (getContext() as? Context)
            ?: return false

        val main = Handler(Looper.getMainLooper())
        val settledAt = AtomicLong(0L)
        val clearanceAt = AtomicLong(0L)
        val aborted = AtomicBoolean(false)
        val done = CountDownLatch(1)
        var webView: WebView? = null

        fun destroy() {
            main.post {
                webView?.let { wv ->
                    runCatching { wv.stopLoading() }
                    runCatching { wv.loadUrl("about:blank") }
                    runCatching { wv.onPause() }
                    runCatching { wv.destroy() }
                }
                webView = null
            }
        }

        main.post {
            try {
                val wv = WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = userAgent
                    layout(0, 0, 1080, 1920)
                    onResume()
                }
                runCatching {
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(wv, true)
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        runCatching { CookieManager.getInstance().flush() }
                        val title = runCatching { view?.title }.getOrNull().orEmpty()
                        if (!CloudflareVerifier.isChallengeTitle(title)) {
                            settledAt.set(System.currentTimeMillis())
                        }
                    }

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        handler?.proceed()
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        aborted.set(true)
                        done.countDown()
                        return true
                    }
                }
                webView = wv
                val extra = LinkedHashMap<String, String>()
                if (!referer.isNullOrBlank()) extra["Referer"] = referer
                wv.loadUrl(url, extra)
            } catch (_: Throwable) {
                aborted.set(true)
                done.countDown()
            }
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        var solved = false
        try {
            while (System.currentTimeMillis() < deadline) {
                if (done.await(POLL_MS, TimeUnit.MILLISECONDS) || aborted.get()) break
                if (clearanceAt.get() == 0L) {
                    val cookie = runCatching { CookieManager.getInstance().getCookie(url) }
                        .getOrNull().orEmpty()
                    if (cookie.contains("cf_clearance")) {
                        clearanceAt.set(System.currentTimeMillis())
                    }
                }
                val settled = settledAt.get()
                if (settled != 0L && System.currentTimeMillis() - settled >= SETTLE_GRACE_MS) {
                    solved = true
                    break
                }
                val cleared = clearanceAt.get()
                if (cleared != 0L && System.currentTimeMillis() - cleared >= 2 * SETTLE_GRACE_MS) {
                    solved = true
                    break
                }
            }
        } finally {
            destroy()
        }
        return solved
    }
}

/**
 * Global OkHttp interceptor attached directly to `app` & `insecureApp`:
 * - Merges live WebView CookieManager cookies IN FRONT of stale hardcoded plugin cookies
 * - Aligns User-Agent to the exact WebView UA that minted `cf_clearance`
 * - Automatically detects and solves Cloudflare / JS interstitial challenges on 403/503/429 and 200 HTML walls
 */
class ExtensionCloudflareInterceptor : Interceptor {
    companion object {
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "avif", "bmp", "heic", "heif", "jfif", "svg", "ico"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val first = chain.proceed(withJarCookies(request))
        if (!isChallenge(first) || isImageRequest(request)) return first

        val url = request.url.toString()
        val solved = CloudflareSolver.solve(
            url = url,
            userAgent = CloudflareSession.effectiveUserAgent(),
            referer = request.header("Referer")
        )
        if (!solved) return first

        first.close()
        val retry = chain.proceed(withJarCookies(request))
        if (isChallenge(retry)) {
            CloudflareVerifier.markBlocked(url)
        }
        return retry
    }

    private fun withJarCookies(request: Request): Request {
        val url = request.url.toString()
        val jar = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty()
        if (jar.isBlank()) return request
        val existing = request.header("Cookie")
        val merged = if (existing.isNullOrBlank()) jar else "$jar; $existing"
        val builder = request.newBuilder().header("Cookie", merged)
        if (jar.contains("cf_clearance")) {
            builder.header("User-Agent", CloudflareSession.effectiveUserAgent())
        }
        return builder.build()
    }

    private fun isChallenge(response: Response): Boolean {
        val code = response.code
        val server = response.header("Server")?.lowercase().orEmpty()
        if (!response.header("cf-mitigated").isNullOrBlank()) return true

        if (code == 403 || code == 503) {
            if (server.contains("cloudflare")) return true
        } else if (code != 429) {
            if (code !in 200..299) return false
            val type = response.header("Content-Type")?.lowercase().orEmpty()
            if (type.isNotBlank() && !type.contains("html") && !type.contains("text") && !type.contains("javascript")) {
                return false
            }
            val body = peekText(response)
            if (body.isBlank()) return false
            return CloudflareVerifier.HARD_MARKERS.any { body.contains(it, ignoreCase = true) }
        }

        val type = response.header("Content-Type")?.lowercase().orEmpty()
        if (type.isNotBlank() && !type.contains("html") && !type.contains("text") && !type.contains("javascript")) {
            return false
        }
        val body = peekText(response)
        if (body.isBlank()) return false
        return CloudflareVerifier.HARD_MARKERS.any { body.contains(it, ignoreCase = true) }
    }

    private fun peekText(response: Response): String = runCatching {
        val bytes = response.peekBody(64L * 1024).bytes()
        val raw = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            GZIPInputStream(bytes.inputStream().buffered()).readBytes()
        } else bytes
        String(raw, 0, minOf(raw.size, 64 * 1024), Charsets.UTF_8)
    }.getOrNull().orEmpty()

    private fun isImageRequest(request: Request): Boolean {
        val last = request.url.pathSegments.lastOrNull() ?: return false
        return last.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
    }
}
