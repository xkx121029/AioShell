package com.aioshell.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 间距：4dp 网格体系。 */
object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** 圆角 Token。 */
object AppRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val full = 999.dp
}

/** 动效时长（毫秒）。 */
object AppMotion {
    const val durationFast = 150
    const val durationNormal = 250
    const val durationSlow = 300
}

/** 形状系统（沿用 M3 Expressive 取向）。 */
object AppShapes {
    val shapes: Shapes = Shapes(
        extraSmall = RoundedCornerShape(AppRadius.sm),
        small = RoundedCornerShape(AppRadius.md),
        medium = RoundedCornerShape(AppRadius.lg),
        large = RoundedCornerShape(AppRadius.xl),
        extraLarge = RoundedCornerShape(32.dp),
    )
}