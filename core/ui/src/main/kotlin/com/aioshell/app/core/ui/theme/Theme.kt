package com.aioshell.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = OnDarkPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = OnDarkPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = OnDarkSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = OnDarkSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = OnDarkTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = OnDarkTertiaryContainer,
    error = DarkError,
    onError = OnDarkError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = OnDarkErrorContainer,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainer,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkSurfaceBright,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = OnLightPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = OnLightPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = OnLightSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = OnLightSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = OnLightTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = OnLightTertiaryContainer,
    error = LightError,
    onError = OnLightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = OnLightErrorContainer,
    background = LightBackground,
    onBackground = OnLightBackground,
    surface = LightSurface,
    onSurface = OnLightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSurfaceVariant,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceContainer,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightSurfaceDim,
)

/** 纯黑深色 ColorScheme：背景/表面切换为纯黑，适配 OLED 屏。 */
private val AmoledColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = OnDarkPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = OnDarkPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = OnDarkSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = OnDarkSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = OnDarkTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = OnDarkTertiaryContainer,
    error = DarkError,
    onError = OnDarkError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = OnDarkErrorContainer,
    background = AmoledBlack,
    onBackground = OnDarkBackground,
    surface = AmoledSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    surfaceDim = AmoledBlack,
    surfaceBright = AmoledSurfaceHigh,
    surfaceContainerLowest = AmoledBlack,
    surfaceContainerLow = AmoledSurface,
    surfaceContainer = AmoledSurface,
    surfaceContainerHigh = AmoledSurfaceHigh,
    surfaceContainerHighest = AmoledSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = AmoledSurfaceVariant,
    inverseSurface = AmoledSurfaceHigh,
)

/**
 * 聊天排版自定义参数：字号（sp）与行距（sp）。
 * 默认值对应 Material 3 bodyLarge（16sp，行高约 24sp）。
 */
data class ChatTextSettings(
    val fontSizeSp: Float = 16f,
    val lineSpacingSp: Float = 0f,
)

/** AioShell 主题入口。 */
@Composable
fun AioShellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoled: Boolean = false,
    accent: Color? = null,
    dynamicColor: Boolean = false,
    chatText: ChatTextSettings = ChatTextSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val effectiveDark = darkTheme || amoled
    val base = when {
        effectiveDark && amoled -> AppAmoledColors.scheme
        effectiveDark -> AppDarkColors.scheme
        else -> AppLightColors.scheme
    }
    // 动态取色（Material You）：跟随系统壁纸，忽略自定义强调色
    if (dynamicColor) {
        val m3 = if (effectiveDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val scheme = base.copy(
            primary = m3.primary,
            onPrimary = m3.onPrimary,
            secondary = m3.secondary,
            onSecondary = m3.onSecondary,
            userBubble = m3.primary,
            onUserBubble = m3.onPrimary,
            aiBubble = base.aiBubble,
            success = m3.tertiary,
        )
        CompositionLocalProvider(LocalAppColors provides scheme, LocalChatTextSettings provides chatText) {
            MaterialTheme(colorScheme = m3, typography = AioTypography, shapes = AppShapes.shapes, content = content)
        }
        return
    }
    // 自定义强调色：替换 primary / onPrimary / userBubble（含对比度计算）
    val scheme = if (accent == null) base else {
        val onAccent = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
        base.copy(primary = accent, onPrimary = onAccent, userBubble = accent, onUserBubble = onAccent)
    }
    val m3 = when {
        effectiveDark && amoled -> AmoledColorScheme
        effectiveDark -> DarkColorScheme
        else -> LightColorScheme
    }
    val materialScheme = if (accent == null) m3 else {
        val onAccent = if (accent.luminance() > 0.5f) Color(0xFF000000) else Color(0xFFFFFFFF)
        m3.copy(primary = accent, onPrimary = onAccent, primaryContainer = accent, onPrimaryContainer = onAccent)
    }
    CompositionLocalProvider(
        LocalAppColors provides scheme,
        LocalChatTextSettings provides chatText,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = AioTypography,
            shapes = AppShapes.shapes,
            content = content,
        )
    }
}

/** 业务层访问语义色 Token 的入口。 */
object AppTheme {
    val colors: AppColorScheme
        @Composable get() = LocalAppColors.current
}