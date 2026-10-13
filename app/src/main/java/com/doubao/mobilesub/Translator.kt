package com.doubao.mobilesub

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 翻译器：dummy 模式或"字幕翻译=不翻译"时直通原文；llm 模式调用 OpenAI 兼容接口翻译成目标语言。
 */
class Translator(private val cfg: AppConfig) {

    fun translate(text: String): String {
        if (cfg.engine != "llm" || cfg.apiKey.isBlank()) return text
        if (cfg.targetLang == "不翻译") return text
        val target = cfg.targetLang.ifBlank { "简体中文" }
        return try {
            val url = URL(cfg.baseUrl.trimEnd('/') + "/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
            val system = "你是影视同声传译字幕助手。请把用户发送的台词翻译成$target，输出自然简洁、适合作为字幕，只输出译文，不要解释、不要引号、不要编号。"
            val body = JSONObject()
                .put("model", cfg.llmModel)
                .put("temperature", 0.1)
                .put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", text)))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val j = JSONObject(resp)
            j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        } catch (e: Exception) {
            "[翻译失败] $text"
        }
    }
}
