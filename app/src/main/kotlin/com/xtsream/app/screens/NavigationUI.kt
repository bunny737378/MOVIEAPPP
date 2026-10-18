package com.xtsream.app.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtsream.app.AppDarkBg
import com.xtsream.app.ScreenState

val ActiveTabColor = Color(0xFFFFFFFF)
val InactiveTabColor = Color(0x99FFFFFF)

@Composable
fun NavigationUI(
    currentScreen: ScreenState,
    onNavigate: (ScreenState) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        Triple("Home", Icons.Filled.Home, ScreenState.Home),
        Triple("Search", Icons.Filled.Search, ScreenState.GlobalSearch),
        Triple("List", Icons.AutoMirrored.Filled.List, ScreenState.ListPage),
        Triple("Profile", Icons.Filled.Person, ScreenState.Profile)
    )

    val barShape = RoundedCornerShape(26.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        AppDarkBg.copy(alpha = 0.4f),
                        AppDarkBg.copy(alpha = 0.8f),
                        AppDarkBg
                    )
                )
            )
            .navigationBarsPadding()
            .padding(top = 48.dp, bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(62.dp)
                .clip(barShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(25.dp)
                    .background(Color(0xFF2F2C3A).copy(alpha = 0.5f))
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(
                        border = BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x4DFFFFFF),
                                    Color(0x1A000000)
                                )
                            )
                        ),
                        shape = barShape
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = currentScreen == tab.third

                        val animatedColor by animateColorAsState(
                            targetValue = if (isSelected) ActiveTabColor else InactiveTabColor,
                            animationSpec = tween(durationMillis = 200),
                            label = "TabColorAnimation"
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onNavigate(tab.third) }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = tab.second,
                                    contentDescription = tab.first,
                                    tint = animatedColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = tab.first,
                                    color = animatedColor,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    letterSpacing = 0.2.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
