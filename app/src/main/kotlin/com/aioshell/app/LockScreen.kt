package com.aioshell.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aioshell.app.core.data.security.SecurityCrypto
import com.aioshell.app.core.data.store.SettingsStore
import com.aioshell.app.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 应用锁校验视图模型：读取开关与 PIN 哈希，提供解锁能力。 */
@HiltViewModel
class LockViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val lockEnabled = settings.lockEnabled

    /** 校验 PIN 是否与存储哈希一致。 */
    suspend fun verify(pin: String): Boolean {
        val stored = settings.lockPinHash.first() ?: return false
        return stored == SecurityCrypto.sha256Hex(pin)
    }
}

/**
 * 应用锁界面：启动时若开启则覆盖在主界面之上，验证 PIN 通过后解除。
 * 输入满 4 位且匹配即自动解锁；错误显示提示文字。
 */
@Composable
fun LockScreen(
    onUnlock: () -> Unit,
    viewModel: LockViewModel = hiltViewModel(),
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(c.background).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "AioShell",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = c.onSurface,
            )
            Text("输入密码以解锁", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
        OutlinedTextField(
            value = pin,
            onValueChange = { raw: String ->
                pin = raw.filter { ch: Char -> ch.isDigit() }.take(6)
                error = false
                if (pin.length >= 4) {
                    scope.launch {
                        if (viewModel.verify(pin)) {
                            onUnlock()
                        } else {
                            error = true
                            pin = ""
                        }
                    }
                }
                Unit
            },
            label = { Text(if (error) "密码错误，请重试" else "PIN") },
            singleLine = true,
            isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        )
        Text("应用锁已开启，请输入 PIN 解锁以访问对话内容。", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
}