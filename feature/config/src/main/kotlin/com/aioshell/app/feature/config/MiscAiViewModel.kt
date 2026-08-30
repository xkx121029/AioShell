package com.aioshell.app.feature.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aioshell.app.core.data.model.MiscAiConfig
import com.aioshell.app.core.data.repository.MiscAiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 杂项 AI 档案编辑表单（与聊天主模型相互独立）。 */
data class MiscAiForm(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val prompt: String = "",
    val enabled: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class MiscAiViewModel @Inject constructor(
    private val miscAiRepo: MiscAiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MiscAiForm())
    val state: StateFlow<MiscAiForm> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val c = miscAiRepo.get()
            _state.value = MiscAiForm(c.baseUrl, c.apiKey, c.model, c.prompt, c.enabled)
        }
    }

    fun update(field: String, value: String) {
        _state.value = _state.value.copy(saved = false)
        _state.value = when (field) {
            "baseUrl" -> _state.value.copy(baseUrl = value)
            "apiKey" -> _state.value.copy(apiKey = value)
            "model" -> _state.value.copy(model = value)
            "prompt" -> _state.value.copy(prompt = value)
            else -> _state.value
        }
    }

    fun setEnabled(v: Boolean) {
        _state.value = _state.value.copy(enabled = v, saved = false)
    }

    fun save(onDone: () -> Unit) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val f = _state.value
            miscAiRepo.save(
                MiscAiConfig(
                    baseUrl = f.baseUrl.trim(),
                    apiKey = f.apiKey.trim(),
                    model = f.model.trim(),
                    prompt = f.prompt.trim(),
                    enabled = f.enabled,
                )
            )
            _state.value = _state.value.copy(saving = false, saved = true)
            onDone()
        }
    }
}