package com.aioshell.app.theme

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.model.ThemeMode
import com.aioshell.app.core.data.store.SettingsStore
import com.aioshell.app.core.ui.theme.ChatTextSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settings: SettingsStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** AMOLED 纯黑模式开关（深色下生效）。 */
    val amoledPureBlack: StateFlow<Boolean> = settings.amoledPureBlack
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** 自定义强调色（十六进制字符串）。 */
    val accentHex: StateFlow<String?> = settings.accentColor
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 聊天排版参数（字号 + 行距）。 */
    val chatText: StateFlow<ChatTextSettings?> = combine(
        settings.chatFontSize,
        settings.chatLineSpacing,
    ) { size, spacing ->
        when {
            size != 16f || spacing != 0f -> ChatTextSettings(fontSizeSp = size, lineSpacingSp = spacing)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}