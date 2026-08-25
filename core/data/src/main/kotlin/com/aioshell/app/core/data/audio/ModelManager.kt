package com.aioshell.app.core.data.audio

import android.content.Context
import com.aioshell.app.core.data.network.ApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/** 语音模型下载/加载状态。 */
sealed interface VoiceModelState {
    object Idle : VoiceModelState
    data class Downloading(val progress: Float) : VoiceModelState
    data class Error(val message: String) : VoiceModelState
    object Ready : VoiceModelState
}

/**
 * 本地语音识别模型管理：下载 → 解压 → 加载。模型为二进制，运行时从官方/镜像源下载到应用私有目录。
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: ApiClient,
) {

    private val _state = MutableStateFlow<VoiceModelState>(VoiceModelState.Idle)
    val state: StateFlow<VoiceModelState> = _state.asStateFlow()

    private var model: org.vosk.Model? = null

    private val modelsDir: File by lazy { File(context.filesDir, "los") }
    private val targetDir: File
        get() = File(modelsDir, MODEL_NAME)

    fun isReady(): Boolean = model != null

    /**
     * 确保模型就绪：已加载则直接返回，否则触发下载（二次调用时使用）。
     * 阻塞直到模型可用；失败抛异常（见 [state]）。
     */
    suspend fun ensureModel(): org.vosk.Model = withContext(Dispatchers.IO) {
        model ?: run {
            if (!modelExists()) downloadModel()
            loadModel()
        }
    }

    private fun modelExists(): Boolean = File(targetDir, "conf").exists() && File(targetDir, "am").exists()

    private fun downloadModel() {
        _state.value = VoiceModelState.Downloading(0f)
        try {
            val zipFile = File(modelsDir, "$MODEL_NAME.zip")
            modelsDir.mkdirs()
            if (targetDir.exists()) targetDir.deleteRecursively()
            val request = Request.Builder().url(MODEL_URL).build()
            apiClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _state.value = VoiceModelState.Error("下载语音模型失败（HTTP ${response.code}）")
                    throw IllegalStateException("download failed")
                }
                val body = response.body ?: throw IllegalStateException("empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    zipFile.outputStream().use { out ->
                        var read = 0L
                        val buffer = ByteArray(81920)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            read += n
                            if (total > 0) {
                                _state.value = VoiceModelState.Downloading((read.toFloat() / total))
                            }
                        }
                    }
                }
            }
            unpack(zipFile, targetDir)
            zipFile.delete()
            _state.value = VoiceModelState.Downloading(1f)
        } catch (e: Exception) {
            _state.value = VoiceModelState.Error(e.message ?: "模型下载失败")
        }
    }

    private fun loadModel(): org.vosk.Model {
        val m = org.vosk.Model(targetDir.absolutePath)
        model = m
        _state.value = VoiceModelState.Ready
        return m
    }

    private fun unpack(zip: File, dest: File) {
        ZipFile(zip).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                // 去除 zip 内首层目录（模型名目录）
                val relative = e.name.substringAfter('/')
                val out = File(dest, relative)
                out.parentFile?.mkdirs()
                zf.getInputStream(e).use { input -> out.outputStream().use { it.copyFrom(input) } }
            }
        }
    }

    private fun java.io.OutputStream.copyFrom(input: java.io.InputStream) {
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            write(buffer, 0, n)
        }
    }

    private companion object {
        // 中文小模型（含常见英文），约 42MB，官方地址
        const val MODEL_NAME = "vosk-model-small-cn-0.22"
        const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
    }
}