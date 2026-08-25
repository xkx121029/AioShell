package com.aioshell.app.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义化颜色 Token。
 * 业务层一律引用 [AppTheme.colors]，禁止硬编码色值。
 */
data class AppColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val error: Color,
    val onError: Color,
    val success: Color,
    val warning: Color,
    val outline: Color,
    val codeBackground: Color,
    val userBubble: Color,
    val onUserBubble: Color,
    val aiBubble: Color,
    val onAiBubble: Color,
)

object AppLightColors {
    val scheme = AppColorScheme(
        primary = LightPrimary,
        onPrimary = OnLightPrimary,
        secondary = LightSecondary,
        onSecondary = OnLightSecondary,
        background = LightBackground,
        surface = LightSurface,
        surfaceVariant = LightSurfaceVariant,
        onBackground = OnLightBackground,
        onSurface = OnLightSurface,
        error = LightError,
        onError = OnLightError,
        success = SuccL,
        warning = WarnL,
        outline = LightOutline,
        codeBackground = CodeL,
        userBubble = LightPrimary,
        onUserBubble = OnLightPrimary,
        aiBubble = LightSurfaceVariant,
        onAiBubble = OnLightSurfaceVariant,
    )
}

object AppDarkColors {
    val scheme = AppColorScheme(
        primary = DarkPrimary,
        onPrimary = OnDarkPrimary,
        secondary = DarkSecondary,
        onSecondary = OnDarkSecondary,
        background = DarkBackground,
        surface = DarkSurface,
        surfaceVariant = DarkSurfaceVariant,
        onBackground = OnDarkBackground,
        onSurface = OnDarkSurface,
        error = DarkError,
        onError = OnDarkError,
        success = SuccD,
        warning = WarnD,
        outline = DarkOutline,
        codeBackground = CodeD,
        userBubble = DarkPrimary,
        onUserBubble = OnDarkPrimary,
        aiBubble = DarkSurfaceVariant,
        onAiBubble = OnDarkSurfaceVariant,
    )
}

/** 供业务层直接读取语义色。 */
val LocalAppColors = staticCompositionLocalOf { AppLightColors.scheme }