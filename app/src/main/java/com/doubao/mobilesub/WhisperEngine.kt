package com.doubao.mobilesub

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.util.concurrent.Executors

/**
 * 本地语音识别引擎（sherpa-onnx + Whisper 模型）。
 * 单线程队列解码，识别结果回到主线程回调。
 */
class WhisperEngine(
    private val context: Context,
    private val cfg: AppConfig,
    private val onResult: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var recognizer: OfflineRecognizer? = null
    private val language = cfg.language

    /** 加载模型；成功返回 null，失败返回错误信息。 */
    fun init(): String? {
        return try {
            val dir = AppConfig.modelDir(context, cfg.modelSize)
            val encoder = File(dir, "${cfg.modelSize}-encoder.int8.onnx").absolutePath

            val whisperCfg = OfflineWhisperModelConfig.builder()
                .setEncoder(encoder)
                .setLanguage(language)
                .setTask("transcribe")
                .setTailPaddings(0)
                .build()
            val modelCfg = OfflineModelConfig.builder()
                .setWhisper(whisperCfg)
                .setNumThreads(2)
                .setDebug(false)
                .build()
            val featCfg = FeatureConfig.builder()
                .setSampleRate(16000)
                .setFeatureDim(80)
                .build()
            val config = OfflineRecognizerConfig.builder()
                .setOfflineModelConfig(modelCfg)
                .setFeatureConfig(featCfg)
                .build()
            recognizer = OfflineRecognizer(config)
            null
        } catch (e: Exception) {
            e.message ?: "模型初始化失败"
        }
    }

    /** 提交一段音频（16k 单声道 float），异步解码。 */
    fun decode(chunk: FloatArray) {
        executor.execute {
            try {
                val rec = recognizer ?: return@execute
                val stream = rec.createStream()
                stream.acceptWaveform(chunk, 16000)
                rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                stream.release()
                if (text.isNotEmpty()) handler.post { onResult(text) }
            } catch (e: Exception) {
                // 单句解码失败直接跳过
            }
        }
    }

    fun shutdown() {
        executor.shutdown()
    }
}
