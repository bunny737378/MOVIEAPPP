package com.xtsream.app.screens.meta

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

data class SheetOpt(val text: String, val isSelected: Boolean, val onClick: () -> Unit)
enum class SheetType { NONE, SEASON, SUB_SEASON, RANGE }

fun adaptColorMeta(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    if (hsv[2] < 0.6f) hsv[2] = 0.9f
    if (hsv[1] < 0.5f) hsv[1] = 0.8f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Composable
fun EpSkeleton() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        repeat(6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.width(135.dp).aspectRatio(16f/9f).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1D1D1D)))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(14.dp).background(Color(0xFF1D1D1D)))
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF1D1D1D)))
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(10.dp).background(Color(0xFF1D1D1D)))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun MetaSkeleton() {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
        Box(modifier = Modifier.height(24.dp).width(150.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1D1D1D)))
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.height(14.dp).fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0xFF1D1D1D)))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1D1D1D)))
    }
}