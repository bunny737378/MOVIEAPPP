package com.lagradost.cloudstream3.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.webkit.*
import com.lagradost.api.Log
import com.lagradost.api.getContext
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.debugException
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.Coroutines.mainWork
import com.lagradost.cloudstream3.utils.Coroutines.runOnMainThread
import com.lagradost.nicehttp.requestCreator
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * When used as Interceptor additionalUrls cannot be returned, use WebViewResolver(...).resolveUsingWebView(...)
 * @param interceptUrl will stop the WebView when reaching this url.
 * @param additionalUrls this will make resolveUsingWebView also return all other requests matching the list of Regex.
 * @param userAgent if null then will use the default user agent
 * @param useOkhttp will try to use the okhttp client as much as possible, but this might cause some requests to fail. Disable for cloudflare.
 * @param script pass custom js to execute
 * @param scriptCallback will be called with the result from custom js
 * @param timeout close webview after timeout
 */
class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = USER_AGENT,
    val useOkhttp: Boolean = true,
    val script: String? = null,
    val scriptCallback: ((String) -> Unit)? = null,
    val timeout: Long = DEFAULT_TIMEOUT
) : Interceptor {

    companion object {
        @Volatile
        var webViewUserAgent: String? = null
        val DEFAULT_TIMEOUT = 60_000L
        private const val TAG = "WebViewResolver"

        private fun resolveContext(): Context? {
            return CommonActivity.activity ?: (getContext() as? Context)
        }

        @JvmName("getWebViewUserAgent1")
        fun getWebViewUserAgent(): String? {
            return webViewUserAgent ?: resolveContext()?.let { ctx ->
                runCatching {
                    WebSettings.getDefaultUserAgent(ctx).also { ua ->
                        webViewUserAgent = ua
                    }
                }.getOrNull() ?: runCatching {
                    runBlocking {
                        mainWork {
                            val wv = WebView(ctx)
                            val ua = wv.settings.userAgentString
                            wv.destroy()
                            webViewUserAgent = ua
                            ua
                        }
                    }
                }.getOrNull()
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return runBlocking {
            val fixedRequest = resolveUsingWebView(request).first
            return@runBlocking chain.proceed(fixedRequest ?: request)
        }
    }

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    suspend fun resolveUsingWebView(
        url: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        method: String = "GET",
        requestCallBack: (Request) -> Boolean = { false },
    ): Pair<Request?, List<Request>> {
        return try {
            resolveUsingWebView(
                requestCreator(method, url, referer = referer, headers = headers), requestCallBack
            )
        } catch (e: java.lang.IllegalArgumentException) {
            logError(e)
            debugException { "ILLEGAL URL IN resolveUsingWebView!" }
            return null to emptyList()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean = { false }
    ): Pair<Request?, List<Request>> {
        val url = request.url.toString()
        val headers = request.headers
        Log.i(TAG, "Initial web-view request: $url")
        val webViewRef = AtomicReference<WebView?>(null)
        val shouldExit = AtomicBoolean(false)
        val destroyed = AtomicBoolean(false)

        fun destroyWebView() {
            if (!destroyed.compareAndSet(false, true)) return
            shouldExit.set(true)
            main {
                runCatching {
                    val wv = webViewRef.getAndSet(null)
                    wv?.stopLoading()
                    wv?.onPause()
                    wv?.destroy()
                }
                Log.i(TAG, "Destroyed webview")
            }
        }

        val fixedRequest = AtomicReference<Request?>(null)
        val extraRequestList = atomicListOf<Request>()

        main {
            try {
                val ctx = resolveContext()
                    ?: throw RuntimeException("No base context in WebViewResolver")

                val createdWebView = WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.loadsImagesAutomatically = !useOkhttp

                    webViewUserAgent = settings.userAgentString
                    // Don't override user agent when null (Cloudflare validates native WebView UA)
                    if (userAgent != null) {
                        settings.userAgentString = userAgent
                    }
                    // Give the headless WebView realistic viewport dimensions so JS challenges run reliably
                    layout(0, 0, 1080, 1920)
                    onResume()
                }
                webViewRef.set(createdWebView)

                safe {
                    val cm = CookieManager.getInstance()
                    cm.setAcceptCookie(true)
                    cm.setAcceptThirdPartyCookies(createdWebView, true)
                }

                createdWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        super.onPageFinished(view, finishedUrl)
                        safe { CookieManager.getInstance().flush() }
                        if (!shouldExit.get() && requestCallBack(request)) {
                            destroyWebView()
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        webResourceRequest: WebResourceRequest
                    ): WebResourceResponse? = runBlocking {
                        if (shouldExit.get()) return@runBlocking null
                        val webViewUrl = webResourceRequest.url.toString()
                        Log.i(TAG, "Loading WebView URL: $webViewUrl")

                        if (script != null) {
                            runOnMainThread {
                                view.evaluateJavascript(script) { scriptCallback?.invoke(it) }
                            }
                        }

                        if (interceptUrl.containsMatchIn(webViewUrl)) {
                            fixedRequest.set(
                                webResourceRequest.toRequest()?.also {
                                    requestCallBack(it)
                                }
                            )
                            Log.i(TAG, "Web-view request finished: $webViewUrl")
                            destroyWebView()
                            return@runBlocking null
                        }

                        if (additionalUrls.any { it.containsMatchIn(webViewUrl) }) {
                            webResourceRequest.toRequest()?.also {
                                if (requestCallBack(it)) destroyWebView()
                            }?.let(extraRequestList::add)
                        }

                        // When useOkhttp == false (e.g. Cloudflare challenge), let WebView load everything natively
                        if (!useOkhttp) {
                            return@runBlocking super.shouldInterceptRequest(view, webResourceRequest)
                        }

                        val blacklistedFiles = listOf(
                            ".jpg", ".png", ".webp", ".mpg", ".mpeg", ".jpeg", ".webm",
                            ".mp4", ".mp3", ".gifv", ".flv", ".asf", ".mov", ".mng",
                            ".mkv", ".ogg", ".avi", ".wav", ".woff2", ".woff", ".ttf",
                            ".css", ".vtt", ".srt", ".ts", ".gif", "wss://"
                        )

                        return@runBlocking try {
                            when {
                                blacklistedFiles.any { Url(webViewUrl).encodedPath.decodeURLPart().contains(it) } ||
                                    webViewUrl.endsWith("/favicon.ico") -> WebResourceResponse(
                                    "image/png", null, null,
                                )

                                webViewUrl.contains("recaptcha") || webViewUrl.contains("/cdn-cgi/") ->
                                    super.shouldInterceptRequest(view, webResourceRequest)

                                webResourceRequest.method == "GET" -> app.get(
                                    webViewUrl,
                                    headers = webResourceRequest.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                webResourceRequest.method == "POST" -> app.post(
                                    webViewUrl,
                                    headers = webResourceRequest.requestHeaders
                                ).okhttpResponse.toWebResourceResponse()

                                else -> super.shouldInterceptRequest(view, webResourceRequest)
                            }
                        } catch (_: Exception) {
                            null
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
                }
                createdWebView.loadUrl(url, headers.toMap())
            } catch (e: Exception) {
                logError(e)
                shouldExit.set(true)
            }
        }

        val totalTime = timeout
        val delayTime = 150L
        var loop = 0

        try {
            while (loop < totalTime / delayTime && !shouldExit.get()) {
                val found = fixedRequest.get()
                if (found != null) return found to extraRequestList
                // Periodically poll callback (catches cf_clearance cookies set via JS without navigation)
                if (!useOkhttp && loop > 0 && loop % 4 == 0) {
                    if (runCatching { requestCallBack(request) }.getOrDefault(false)) {
                        break
                    }
                }
                delay(delayTime)
                loop += 1
            }
            if (!shouldExit.get()) {
                Log.i(TAG, "Web-view timeout after ${totalTime / 1000}s")
            }
        } finally {
            // Always clean up WebView even if the parent coroutine is cancelled!
            destroyWebView()
        }

        return fixedRequest.get() to extraRequestList
    }
}

fun WebResourceRequest.toRequest(): Request? {
    val webViewUrl = this.url.toString()
    return safe {
        requestCreator(
            this.method,
            webViewUrl,
            this.requestHeaders,
        )
    }
}

fun Response.toWebResourceResponse(): WebResourceResponse {
    val contentTypeValue = this.header("Content-Type")
    val typeRegex = Regex("""(.*);(?:.*charset=(.*)(?:|;)|)""")
    return if (contentTypeValue != null) {
        val found = typeRegex.find(contentTypeValue)
        val contentType = found?.groupValues?.getOrNull(1)?.ifBlank { null } ?: contentTypeValue
        val charset = found?.groupValues?.getOrNull(2)?.ifBlank { null }
        WebResourceResponse(contentType, charset, this.body.byteStream())
    } else {
        WebResourceResponse("application/octet-stream", null, this.body.byteStream())
    }
}
