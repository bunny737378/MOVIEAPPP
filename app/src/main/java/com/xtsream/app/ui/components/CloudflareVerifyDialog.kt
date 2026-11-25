package com.xtsream.app.ui.components

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lagradost.cloudstream3.network.CloudflareSession
import com.lagradost.cloudstream3.network.CloudflareVerifier
import com.xtsream.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Two-stage Cloudflare verification UX:
 * 1. Confirmation popup asking the user before opening the WebView (Cancel / Open).
 * 2. Floating, non-blocking mini-window WebView overlay that the user can:
 *    - drag anywhere on screen (including off to the side),
 *    - resize smaller or bigger (bottom-right corner drag handle + quick size toggle),
 *    - tap and interact inside normally while the app remains visible behind it.
 * Auto-dismisses as soon as cf_clearance cookie is acquired.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareVerifyDialog() {
    val targetUrl by CloudflareVerifier.pendingVerificationUrl.collectAsState()
    val url = targetUrl ?: return

    var userConfirmedOpen by remember(url) { mutableStateOf(false) }
    var solvedTriggered by remember(url) { mutableStateOf(false) }
    var pageSettled by remember(url) { mutableStateOf(false) }

    val host = remember(url) { CloudflareSession.hostOf(url) ?: url }

    // Stage 1: Confirmation Popup before opening the floating WebView
    if (!userConfirmedOpen) {
        AlertDialog(
            onDismissRequest = { CloudflareVerifier.dismissVerification() },
            containerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            textContentColor = AppColors.TextSecondary,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Verification Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$host requires a quick Cloudflare security check before loading content.",
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "Open the floating verification box to complete the check? You can move or resize it anywhere on screen.",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { CloudflareVerifier.dismissVerification() }) {
                    Text("Cancel", color = AppColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = { userConfirmedOpen = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.OnPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Open", fontWeight = FontWeight.Bold)
                }
            }
        )
        return
    }

    // Stage 2: Poll for cf_clearance once user opened the floating mini-window
    LaunchedEffect(url, userConfirmedOpen) {
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

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(320f)
    val screenHeightDp = configuration.screenHeightDp.toFloat().coerceAtLeast(520f)

    val minWidthDp = 210f
    val maxWidthDp = (screenWidthDp - 16f).coerceAtLeast(minWidthDp)
    val minHeightDp = 210f
    val maxHeightDp = (screenHeightDp - 120f).coerceAtLeast(minHeightDp)

    var boxWidthDp by remember(url) { mutableFloatStateOf((screenWidthDp * 0.80f).coerceIn(minWidthDp, 320f)) }
    var boxHeightDp by remember(url) { mutableFloatStateOf((screenHeightDp * 0.44f).coerceIn(minHeightDp, 380f)) }
    var isMinimized by remember(url) { mutableStateOf(false) }

    val initialOffsetX = remember(url, screenWidthDp) {
        with(density) { ((screenWidthDp - boxWidthDp) / 2f).dp.toPx() }
    }
    val initialOffsetY = remember(url, screenHeightDp) {
        with(density) { (screenHeightDp * 0.18f).dp.toPx() }
    }
    var offsetX by remember(url) { mutableFloatStateOf(initialOffsetX) }
    var offsetY by remember(url) { mutableFloatStateOf(initialOffsetY) }

    val screenWpx = with(density) { screenWidthDp.dp.toPx() }
    val screenHpx = with(density) { screenHeightDp.dp.toPx() }

    val activeWidthDp = if (isMinimized) 210f else boxWidthDp
    val activeHeightDp = if (isMinimized) 48f else boxHeightDp
    val isExpanded = boxWidthDp > (screenWidthDp * 0.88f) && boxHeightDp > (screenHeightDp * 0.65f)

    // Non-blocking floating overlay inside MovieAppRoot Box (taps outside pass right through!)
    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(activeWidthDp.dp)
            .height(activeHeightDp.dp)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(14.dp), clip = false)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.BorderStrong, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Draggable Header Bar (move box anywhere, even partially off-screen to the side!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(AppColors.NavBarBg)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curWpx = with(density) { activeWidthDp.dp.toPx() }
                            val curHpx = with(density) { activeHeightDp.dp.toPx() }
                            // Allow sliding well to the side while keeping a grabbable edge visible
                            val minX = -(curWpx * 0.65f)
                            val maxX = screenWpx - (curWpx * 0.35f)
                            val minY = 24f
                            val maxY = screenHpx - 64f
                            offsetX = (offsetX + dragAmount.x).coerceIn(minX, maxX)
                            offsetY = (offsetY + dragAmount.y).coerceIn(minY, maxY)
                        }
                    }
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag grip pill indicator
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(CircleShape)
                        .background(AppColors.BorderStrong)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloudflare Check",
                        color = AppColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = host,
                        color = AppColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Minimize / Restore toggle
                IconButton(
                    onClick = { isMinimized = !isMinimized },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isMinimized) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (isMinimized) "Restore" else "Collapse",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Quick Small / Big toggle
                if (!isMinimized) {
                    TextButton(
                        onClick = {
                            if (isExpanded) {
                                boxWidthDp = (screenWidthDp * 0.76f).coerceIn(minWidthDp, 300f)
                                boxHeightDp = (screenHeightDp * 0.42f).coerceIn(minHeightDp, 350f)
                            } else {
                                boxWidthDp = maxWidthDp
                                boxHeightDp = (screenHeightDp * 0.74f).coerceIn(minHeightDp, maxHeightDp)
                                offsetX = with(density) { 8.dp.toPx() }
                                offsetY = with(density) { 56.dp.toPx() }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Small" else "Big",
                            color = AppColors.Primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Close
                IconButton(
                    onClick = { CloudflareVerifier.dismissVerification() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = AppColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (!isMinimized) {
                HorizontalDivider(thickness = 0.5.dp, color = AppColors.Divider)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
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

                    // Bottom-Right Corner Resize Drag Handle (drag to freely shrink or enlarge)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(RoundedCornerShape(topStart = 10.dp))
                            .background(AppColors.NavBarBg.copy(alpha = 0.88f))
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val dxDp = with(density) { dragAmount.x.toDp().value }
                                    val dyDp = with(density) { dragAmount.y.toDp().value }
                                    boxWidthDp = (boxWidthDp + dxDp).coerceIn(minWidthDp, maxWidthDp)
                                    boxHeightDp = (boxHeightDp + dyDp).coerceIn(minHeightDp, maxHeightDp)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "◢",
                            color = AppColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}
