package com.aioshell.app.core.ui.animation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.aioshell.app.core.ui.theme.AppMotion

/** 流式光标：1→0.2 呼吸闪烁。 */
@Composable
fun rememberBlinkingAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )
    return alpha
}

/** 消息出现 / 会话切换等统一时长。 */
object Motion {
    const val Fast = AppMotion.durationFast
    const val Normal = AppMotion.durationNormal
    const val Slow = AppMotion.durationSlow
}