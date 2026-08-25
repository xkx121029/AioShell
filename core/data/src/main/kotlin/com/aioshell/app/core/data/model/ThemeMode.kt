package com.aioshell.app.core.data.model

/** 主题偏好：跟随浅色 / 深色 / 系统。 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** ThemeMode 显示文案。 */
val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
        ThemeMode.SYSTEM -> "跟随系统"
    }