package com.doubao.mobilesub

import android.content.Context
import java.io.File

/** 应用配置：翻译引擎与模型设置，存于 SharedPreferences。 */
data class AppConfig(
    val engine: String,      // llm / dummy
    val apiKey: String,
    val baseUrl: String,
    val llmModel: String,
    val language: String,    // "" = 自动检测
    val modelSize: String    // tiny / base / small
) {
    companion object {
        private const val PREF = "pref"

        fun load(c: Context): AppConfig {
            val p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            return AppConfig(
                engine = p.getString("engine", "llm") ?: "llm",
                apiKey = p.getString("api_key", "") ?: "",
                baseUrl = p.getString("base_url", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1",
                llmModel = p.getString("llm_model", "deepseek-chat") ?: "deepseek-chat",
                language = p.getString("language", "") ?: "",
                modelSize = p.getString("model_size", "base") ?: "base",
            )
        }

        fun save(c: Context, cfg: AppConfig) {
            c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("engine", cfg.engine)
                .putString("api_key", cfg.apiKey)
                .putString("base_url", cfg.baseUrl)
                .putString("llm_model", cfg.llmModel)
                .putString("language", cfg.language)
                .putString("model_size", cfg.modelSize)
                .apply()
        }

        fun modelDir(c: Context, size: String): File {
            val base = c.getDir("models", Context.MODE_PRIVATE)
            return File(base, size)
        }

        fun modelReady(c: Context, size: String): Boolean {
            val d = modelDir(c, size)
            return File(d, "$size-encoder.int8.onnx").exists() &&
                File(d, "$size-decoder.int8.onnx").exists() &&
                File(d, "$size-tokens.txt").exists()
        }
    }
}
