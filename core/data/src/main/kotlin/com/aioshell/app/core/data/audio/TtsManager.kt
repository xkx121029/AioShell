package com.aioshell.app.core.data.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** TTS 播放状态。 */
sealed interface TtsState {
    data object Idle : TtsState
    data class Playing(val utteranceId: String) : TtsState
}

/**
 * 系统 TTS 语音朗读管理：负责朗读 AI 回复，支持停止。
 * 通过 [state] 暴露当前朗读状态，UI 据此切换播放/停止图标。
 */
@Singleton
class TtsManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready = false

    /** 初始化 TTS（懒加载，仅在有朗读请求时创建）。 */
    private fun ensureTts(onReady: () -> Unit) {
        if (ready) {
            onReady()
            return
        }
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    val supported = tts?.setLanguage(Locale.CHINESE)
                    // 系统无中文语音时回退默认语言
                    if (supported == TextToSpeech.LANG_MISSING_DATA || supported == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.getDefault())
                    }
                    onReady()
                }
            }
        } else {
            onReady()
        }
    }

    /**
     * 朗读文本。若正在朗读同一条消息则停止；否则先停止当前朗读再朗读新文本。
     * @param utteranceId 消息 id，用于标识当前朗读来源。
     */
    fun speak(text: String, utteranceId: String) {
        val content = text.trim()
        if (content.isEmpty()) return

        if (_state.value is TtsState.Playing && (_state.value as TtsState.Playing).utteranceId == utteranceId) {
            stop()
            return
        }

        ensureTts {
            tts?.stop()
            _state.value = TtsState.Playing(utteranceId)
            val listener = object : UtteranceProgressListener() {
                override fun onStart(p0: String?) {}
                override fun onDone(p0: String?) { _state.value = TtsState.Idle }
                @Deprecated("Deprecated in Java")
                override fun onError(p0: String?) { _state.value = TtsState.Idle }
            }
            tts?.setOnUtteranceProgressListener(listener)
            tts?.speak(content, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    /** 停止当前朗读并复位状态。 */
    fun stop() {
        tts?.stop()
        _state.value = TtsState.Idle
    }

    /** 资源释放（应用退出时调用，防止泄漏）。 */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    fun isPlaying(utteranceId: String): Boolean =
        _state.value is TtsState.Playing && (_state.value as TtsState.Playing).utteranceId == utteranceId
}
