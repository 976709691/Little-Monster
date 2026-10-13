package com.doubao.mobilesub

import android.content.Context
import java.io.File

/** 应用配置：翻译引擎、模型、语言与字幕显示设置，存于 SharedPreferences。 */
data class AppConfig(
    val engine: String,      // llm / dummy
    val apiKey: String,
    val baseUrl: String,
    val llmModel: String,
    val language: String,    // "" = 自动检测（Whisper 语言代码，如 en/ja/ko/zh）
    val modelSize: String,   // tiny / base / small
    val targetLang: String,  // 字幕翻译目标语言："不翻译"/"简体中文"/"繁体中文"/"英语"/...
    val showBilingual: Boolean, // 同时显示双语（原文 + 译文）
    val fontSizeSp: Int,     // 字幕字号（sp），默认 22
    val bgAlpha: Int         // 字幕背景不透明度（0-100），默认 70
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
                targetLang = p.getString("target_lang", "简体中文") ?: "简体中文",
                showBilingual = p.getBoolean("show_bilingual", true),
                fontSizeSp = p.getInt("font_size_sp", 22),
                bgAlpha = p.getInt("bg_alpha", 70),
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
                .putString("target_lang", cfg.targetLang)
                .putBoolean("show_bilingual", cfg.showBilingual)
                .putInt("font_size_sp", cfg.fontSizeSp)
                .putInt("bg_alpha", cfg.bgAlpha)
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

        /** Whisper 语言代码 → 中文名（用于语言方向标签）；未知代码原样返回。 */
        fun srcLangLabel(code: String): String = when (code) {
            "" -> "自动检测"
            "en" -> "英语"
            "ja" -> "日语"
            "ko" -> "韩语"
            "zh" -> "中文"
            "fr" -> "法语"
            "de" -> "德语"
            "ru" -> "俄语"
            "es" -> "西班牙语"
            "pt" -> "葡萄牙语"
            "it" -> "意大利语"
            "ar" -> "阿拉伯语"
            "th" -> "泰语"
            "vi" -> "越南语"
            "tr" -> "土耳其语"
            "pl" -> "波兰语"
            "uk" -> "乌克兰语"
            "hi" -> "印地语"
            else -> code
        }
    }
}
