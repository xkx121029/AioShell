package com.aioshell.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aioshell.app.core.data.model.ThemeMode
import com.aioshell.app.core.ui.theme.AioShellTheme
import com.aioshell.app.nav.AioAppNav
import com.aioshell.app.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeVm: ThemeViewModel = viewModel()
            val themeMode by themeVm.themeMode.collectAsStateWithLifecycle()
            val amoled by themeVm.amoledPureBlack.collectAsStateWithLifecycle()
            val accentHex by themeVm.accentHex.collectAsStateWithLifecycle()
            val chatText by themeVm.chatText.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AioShellTheme(
                darkTheme = darkTheme,
                amoled = amoled,
                accent = parseAccent(accentHex),
                chatText = chatText ?: com.aioshell.app.core.ui.theme.ChatTextSettings(),
            ) {
                AppLockGate()
            }
        }
    }
}

/** 应用锁门卫：若开启应用锁则先显示解锁界面，解锁后进入主界面。 */
@androidx.compose.runtime.Composable
private fun AppLockGate() {
    val lockVm: LockViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val unlocked = remember { mutableStateOf(false) }
    val lockEnabled by lockVm.lockEnabled.collectAsStateWithLifecycle(initialValue = false)
    if (lockEnabled && !unlocked.value) {
        LockScreen(onUnlock = { unlocked.value = true })
    } else {
        AioAppNav()
    }
}

/** 解析 "#RRGGBB"[AA] 强调色；非法或空返回 null。 */
private fun parseAccent(hex: String?): Color? {
    if (hex == null) return null
    val clean = hex.removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = runCatching { clean.toLong(16) }.getOrNull() ?: return null
    return if (clean.length == 8) Color(value) else Color(0xFF000000.toInt() or value.toInt())
}