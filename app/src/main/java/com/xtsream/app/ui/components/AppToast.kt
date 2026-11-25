package com.xtsream.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class AppToastType { Info, Success, Error }

data class AppToastMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: AppToastType = AppToastType.Info
)

object AppToastManager {
    private val _currentToast = MutableStateFlow<AppToastMessage?>(null)
    val currentToast: StateFlow<AppToastMessage?> = _currentToast.asStateFlow()

    fun show(text: String, type: AppToastType = AppToastType.Info) {
        if (text.isBlank()) return
        _currentToast.value = AppToastMessage(text = text, type = type)
    }

    fun showSuccess(text: String) = show(text, AppToastType.Success)
    fun showError(text: String) = show(text, AppToastType.Error)
    fun dismiss() {
        _currentToast.value = null
    }
}

/**
 * Top notification-style overlay toast popup:
 * - Floats cleanly over all screens below the status bar
 * - Auto-dismisses after 3 seconds
 * - Interactive swipe UP or swipe LEFT/RIGHT to dismiss
 */
@Composable
fun AppToastHost(
    modifier: Modifier = Modifier
) {
    val toast by AppToastManager.currentToast.collectAsState()
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(toast?.id) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        if (toast != null) {
            delay(3000L)
            AppToastManager.dismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 40 },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(180)),
            exit = slideOutVertically(
                targetOffsetY = { -it - 40 },
                animationSpec = tween(220)
            ) + fadeOut(tween(180))
        ) {
            val current = toast ?: return@AnimatedVisibility
            val icon = when (current.type) {
                AppToastType.Success -> Icons.Filled.Check
                AppToastType.Error -> Icons.Filled.Warning
                AppToastType.Info -> Icons.Filled.Info
            }
            val iconTint = when (current.type) {
                AppToastType.Success -> AppColors.Success
                AppToastType.Error -> AppColors.Danger
                AppToastType.Info -> AppColors.Primary
            }

            val swipeAlpha = (1f - (abs(offsetX.value) / 380f) - ((-offsetY.value).coerceAtLeast(0f) / 160f))
                .coerceIn(0.15f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .graphicsLayer {
                        translationX = offsetX.value
                        translationY = offsetY.value
                        alpha = swipeAlpha
                    }
                    .pointerInput(current.id) {
                        detectDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offsetY.value < -55f || abs(offsetX.value) > 150f) {
                                        AppToastManager.dismiss()
                                    } else {
                                        launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                        launch { offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    launch { offsetX.animateTo(0f, spring()) }
                                    launch { offsetY.animateTo(0f, spring()) }
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                val nextY = (offsetY.value + dragAmount.y).coerceAtMost(24f)
                                offsetY.snapTo(nextY)
                            }
                        }
                    }
                    .shadow(14.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.SurfaceElevated)
                    .border(0.8.dp, AppColors.BorderStrong, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = current.text,
                    color = AppColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
