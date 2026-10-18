package com.xtsream.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.AppDarkBg
import com.xtsream.app.R
import com.xtsream.app.manage.ProviderManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WaveProgress(progress: Int, modifier: Modifier = Modifier) {
    val phase = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            phase.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
            )
            phase.snapTo(0f)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = -90f }) {
            val waveCount = 8
            val baseAmp = 3.2f
            val amp = when {
                progress <= 0 -> 0f
                progress < 5 -> (progress / 5f) * baseAmp
                progress <= 95 -> baseAmp
                progress < 100 -> ((100f - progress) / 5f) * baseAmp
                else -> 0f
            }
            val cx = size.width / 2
            val cy = size.height / 2
            val radiusBase = size.width * 0.41f
            val steps = 500
            val scaleRatio = size.width / 100f
            val phaseRad = phase.value * PI.toFloat() / 180f

            val path = Path()
            for (i in 0..steps) {
                val angle = (i.toFloat() / steps) * 2 * PI.toFloat()
                val r = radiusBase + (amp * scaleRatio) * cos(waveCount * angle + phaseRad)
                val x = cx + r * cos(angle)
                val y = cy + r * sin(angle)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            val totalLength = 280f
            val dashOffset = totalLength - (progress / 100f) * totalLength

            drawPath(
                path = path,
                color = Color(0xFFA8C7FA),
                style = Stroke(
                    width = 5f * scaleRatio,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        intervals = floatArrayOf(totalLength * scaleRatio, totalLength * scaleRatio),
                        phase = dashOffset * scaleRatio
                    )
                )
            )
        }

        Text(
            text = "$progress",
            color = Color(0xFFA8C7FA),
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Default,
            letterSpacing = (-0.5).sp
        )
    }
}

@Composable
fun SplashUI() {
    Box(modifier = Modifier.fillMaxSize().background(AppDarkBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painter = painterResource(id = R.drawable.ic_play_custom), contentDescription = null, tint = Color.White, modifier = Modifier.size(80.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "MOVIEBOX", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun SetupUI(onFinish: () -> Unit) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Setting up please wait...") }
    var installProgress by remember { mutableIntStateOf(0) }
    var isInstalling by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            delay(2000)
            if (!isInstalling) statusText = "Almost done..."
        }

        if (!ProviderManager.isListLoaded) {
            ProviderManager.fetchPluginsList(context)
        }

        if (!ProviderManager.plugins.any { it.isInstalled }) {
            isInstalling = true
            statusText = "Downloading essential files..."
            ProviderManager.autoInstallDefault(context) { p -> installProgress = p }
        }
        
        delay(300)
        onFinish()
    }

    Box(modifier = Modifier.fillMaxSize().background(AppDarkBg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isInstalling) {
                WaveProgress(progress = installProgress, modifier = Modifier.size(160.dp))
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                CustomLoader()
                Spacer(modifier = Modifier.height(24.dp))
            }
            Text(text = statusText, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
