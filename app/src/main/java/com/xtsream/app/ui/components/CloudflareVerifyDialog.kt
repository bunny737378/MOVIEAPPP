package com.xtsream.app.ui.components

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.network.CloudflareSession
import com.lagradost.cloudstream3.network.CloudflareVerifier
import com.xtsream.app.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * Interactive Cloudflare Turnstile fallback dialog.
 * Only appears if headless CloudflareSolver could not auto-clear a challenge.
 * Auto-dismisses as soon as cf_clearance cookie is acquired.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareVerifyDialog() {
    val targetUrl by CloudflareVerifier.pendingVerificationUrl.collectAsState()
    val url = targetUrl ?: return

    var solvedTriggered by remember(url) { mutableStateOf(false) }
    var pageSettled by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        while (!solvedTriggered) {
            delay(400)
            val hasClearance = !CloudflareVerifier.clearanceFor(url).isNullOrBlank()
            if (hasClearance && pageSettled) {
                solvedTriggered = true
                CloudflareVerifier.onInteractiveSolved(url)
                AppToastManager.showSuccess("Verified — retrying")
                break
            }
        }
    }

    Dialog(
        onDismissRequest = { CloudflareVerifier.dismissVerification() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.NavBarBg)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Security Verification",
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = CloudflareSession.hostOf(url) ?: url,
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { CloudflareVerifier.dismissVerification() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = AppColors.TextPrimary
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = CloudflareSession.effectiveUserAgent()

                        runCatching {
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                                runCatching { CookieManager.getInstance().flush() }
                                val title = runCatching { view?.title }.getOrNull()
                                if (!CloudflareVerifier.isChallengeTitle(title)) {
                                    pageSettled = true
                                    if (!CloudflareVerifier.clearanceFor(url).isNullOrBlank() && !solvedTriggered) {
                                        solvedTriggered = true
                                        CloudflareVerifier.onInteractiveSolved(url)
                                        AppToastManager.showSuccess("Verified — retrying")
                                    }
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

                        loadUrl(url)
                    }
                },
                onRelease = { wv ->
                    runCatching {
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.onPause()
                        wv.destroy()
                    }
                }
            )
        }
    }
}
