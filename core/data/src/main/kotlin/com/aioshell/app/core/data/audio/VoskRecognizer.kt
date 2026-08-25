package com.aioshell.app.core.data.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer

/** 识别结果。 */
data class RecognitionResult(val text: String, val finalized: Boolean, val volume: Float = 0f)

/**
 * Vosk 离线语音识别运行器：AudioRecord 采集 16k PCM → Recognizer 半实时识别。
 * 本地推理，不联网。
 */
object VoskRecognizer {

    private const val SAMPLE_RATE = 16000
    private val json = Json { ignoreUnknownKeys = true }

    fun listen(model: Model): Flow<RecognitionResult> = callbackFlow {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize <= 0) { close(IllegalStateException("麦克风不可用")); return@callbackFlow }
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2,
            )
        } catch (e: Exception) {
            close(e); return@callbackFlow
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            close(IllegalStateException("无法初始化录音")); return@callbackFlow
        }
        val recognizer = Recognizer(model, SAMPLE_RATE.toFloat())

        val job = launch(Dispatchers.IO) {
            try {
                audioRecord.startRecording()
                val shorts = ShortArray(bufferSize / 2)
                while (true) {
                    if (isClosedForSend) break
                    val read = audioRecord.read(shorts, 0, shorts.size)
                    if (read < 0) break
                    val volume = computeVolume(shorts, read)
                    if (recognizer.acceptWaveForm(shorts, read)) {
                        val res = recognizer.result
                        val text = parseText(res)
                        if (text.isNotEmpty()) trySend(RecognitionResult(text, finalized = true, volume = volume))
                    } else {
                        val partial = parseText(recognizer.partialResult)
                        trySend(RecognitionResult(partial, finalized = false, volume = volume))
                    }
                }
            } catch (e: Exception) {
                close(e)
            } finally {
                try { recognizer.close() } catch (_: Exception) {}
                try { audioRecord.stop() } catch (_: Exception) {}
                audioRecord.release()
            }
        }
        awaitClose { job.cancel() }
    }

    /** 计算当前帧音量（RMS 归一化 0..1）。 */
    private fun computeVolume(shorts: ShortArray, read: Int): Float {
        if (read <= 0) return 0f
        var sum = 0.0
        for (i in 0 until read) {
            val v = shorts[i].toDouble() / 32768.0
            sum += v * v
        }
        return (kotlin.math.sqrt(sum / read) * 3.0).coerceIn(0.0, 1.0).toFloat()
    }

    /** 从 Vosk JSON 结果提取文本。 */
    private fun parseText(voskJson: String): String = runCatching {
        json.parseToJsonElement(voskJson).jsonObject["text"]?.jsonPrimitive?.content ?: ""
    }.getOrDefault("")
}