package com.xtsream.app.ui.components

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xtsream.app.stremio.StremioClient
import com.xtsream.app.ui.theme.AppColors

/**
 * In-app Stremio Addon Configurator WebView:
 * - Opens `{baseUrl}/configure` for any configurable Stremio addon.
 * - Automatically intercepts `stremio://.../manifest.json`, `web.stremio.com/#/addons?addon=...`,
 *   and direct `.../manifest.json` redirects when the user clicks "Install" on the configure page.
 * - Also provides a quick "Apply Copied URL" action for configurators that copy the manifest URL to clipboard.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StremioConfigureDialog(
    addonName: String,
    configureUrl: String,
    onManifestCaptured: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var currentTitle by remember(addonName) { mutableStateOf("Configure $addonName") }
    var captured by remember { mutableStateOf(false) }

    fun tryInterceptUrl(rawUrl: String): Boolean {
        if (captured) return true
        val lower = rawUrl.lowercase()
        val isInstallTarget = lower.startsWith("stremio://") ||
            lower.contains("web.stremio.com/#/addons") ||
            lower.contains("app.strem.io/shell") ||
            (lower.endsWith("/manifest.json") || lower.contains("/manifest.json?"))

        if (!isInstallTarget) return false

        val normalized = StremioClient.normalizeManifestUrl(rawUrl) ?: return false
        captured = true
        onManifestCaptured(normalized)
        return true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val wv = webViewRef
                    if (wv != null && wv.canGoBack()) wv.goBack() else onDismiss()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.Primary)
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(
                        text = currentTitle,
                        color = AppColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Configure & tap Install (or Copy URL)",
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                        val normalized = if (clip.isNotBlank()) StremioClient.normalizeManifestUrl(clip) else null
                        if (normalized != null && (clip.contains("manifest", true) || clip.startsWith("stremio://", true) || clip.startsWith("http", true))) {
                            captured = true
                            onManifestCaptured(normalized)
                        } else {
                            AppToastManager.showError("Copy the configured manifest URL first")
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Use Copied",
                        color = AppColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = { webViewRef?.reload() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Refresh, "Reload", tint = AppColors.TextSecondary, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Filled.Close, "Close", tint = AppColors.TextSecondary, modifier = Modifier.size(19.dp))
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    progress = { (loadProgress / 100f).coerceIn(0.05f, 1f) },
                    color = AppColors.Primary,
                    trackColor = AppColors.SurfaceCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                )
            } else {
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            val wv = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(false)
                            }
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(wv, true)
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                    isLoading = newProgress < 100
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        currentTitle = title
                                    }
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    return tryInterceptUrl(url)
                                }

                                @Deprecated("Deprecated in Java")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url.isNullOrBlank()) return false
                                    return tryInterceptUrl(url)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }
                            }
                            webViewRef = this
                            loadUrl(configureUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            }
        }
    }
}
