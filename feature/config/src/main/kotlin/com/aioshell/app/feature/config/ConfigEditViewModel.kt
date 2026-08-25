package com.aioshell.app.feature.config

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.model.ChatConfig
import com.aioshell.app.core.data.network.ApiException
import com.aioshell.app.core.data.repository.ChatRepository
import com.aioshell.app.core.data.repository.ConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConfigFormUi(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: String = "0.7",
    val maxTokens: String = "2048",
    val topP: String = "1.0",
    val loaded: Boolean = false,
)

data class ConfigEditState(
    val form: ConfigFormUi = ConfigFormUi(),
    val testing: Boolean = false,
    val saving: Boolean = false,
    val testMessage: String? = null,
    val testSuccess: Boolean? = null,
)

@HiltViewModel
class ConfigEditViewModel @Inject constructor(
    private val configRepo: ConfigRepository,
    private val chatRepo: ChatRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val configId: String? = savedStateHandle["configId"]

    private val _state = MutableStateFlow(ConfigEditState())
    val state: StateFlow<ConfigEditState> = _state.asStateFlow()

    init {
        if (!configId.isNullOrBlank()) {
            viewModelScope.launch {
                val cfg = configRepo.getById(configId)
                if (cfg != null) {
                    _state.value = _state.value.copy(
                        form = ConfigFormUi(
                            id = cfg.id,
                            name = cfg.name,
                            baseUrl = cfg.baseUrl,
                            apiKey = cfg.apiKey,
                            model = cfg.model,
                            temperature = cfg.temperature.toString(),
                            maxTokens = cfg.maxTokens.toString(),
                            topP = cfg.topP.toString(),
                            loaded = true,
                        ),
                    )
                }
            }
        }
    }

    fun updateField(field: String, value: String) {
        val f = _state.value.form
        val updated = when (field) {
            "name" -> f.copy(name = value)
            "baseUrl" -> f.copy(baseUrl = value)
            "apiKey" -> f.copy(apiKey = value)
            "model" -> f.copy(model = value)
            "temperature" -> f.copy(temperature = value)
            "maxTokens" -> f.copy(maxTokens = value)
            "topP" -> f.copy(topP = value)
            else -> f
        }
        _state.value = _state.value.copy(form = updated)
    }

    private fun validatedConfig(): ChatConfig? {
        val f = _state.value.form
        return if (f.baseUrl.isBlank() || f.model.isBlank()) {
            _state.value = _state.value.copy(testMessage = "请填写 Base URL 与模型名称", testSuccess = false)
            null
        } else {
            ChatConfig(
                id = f.id,
                name = f.name.ifBlank { f.model },
                baseUrl = f.baseUrl,
                apiKey = f.apiKey,
                model = f.model,
                temperature = f.temperature.toFloatOrNull() ?: 0.7f,
                maxTokens = f.maxTokens.toIntOrNull() ?: 2048,
                topP = f.topP.toFloatOrNull() ?: 1.0f,
            )
        }
    }

    fun testConnection() {
        val cfg = validatedConfig() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(testing = true, testMessage = null)
            runCatching { chatRepo.testConnection(cfg) }
                .onSuccess {
                    _state.value = _state.value.copy(testing = false, testSuccess = true, testMessage = "连接成功")
                }
                .onFailure { e ->
                    val msg = (e as? ApiException)?.message ?: e.message ?: "连接失败"
                    _state.value = _state.value.copy(testing = false, testSuccess = false, testMessage = msg)
                }
        }
    }

    fun save(onSaved: (String) -> Unit) {
        val cfg = validatedConfig() ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val saved = configRepo.save(cfg)
            _state.value = _state.value.copy(saving = false)
            onSaved(saved.id)
        }
    }
}