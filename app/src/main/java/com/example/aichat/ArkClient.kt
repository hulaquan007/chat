package com.example.aichat

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 火山方舟（Ark）接口：聊天补全 + Seedream 文生图，使用同一个 API Key。 */
object ArkClient {

    private fun post(url: String, key: String, body: JSONObject, readTimeoutMs: Int): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $key")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code：${text.take(300)}")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    /** history: role("user"/"assistant") 到内容 */
    fun chat(cfg: Settings, history: List<Pair<String, String>>): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", cfg.systemPrompt()))
        history.forEach { (r, c) -> msgs.put(JSONObject().put("role", r).put("content", c)) }
        val body = JSONObject()
            .put("model", cfg.chatModel)
            .put("messages", msgs)
            .put("temperature", 0.9)
            .put("max_tokens", 300)
        val resp = post("${cfg.baseUrl}/chat/completions", cfg.apiKey, body, 60000)
        return resp.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content").trim()
    }

    /** 用 Seedream 生成形象图，返回图片字节。 */
    fun generateImage(cfg: Settings): ByteArray {
        val body = JSONObject()
            .put("model", cfg.imageModel)
            .put("prompt", cfg.portraitPrompt)
            .put("size", "1K")
            .put("response_format", "b64_json")
        val resp = post("${cfg.baseUrl}/images/generations", cfg.apiKey, body, 120000)
        val item = resp.getJSONArray("data").getJSONObject(0)
        if (item.has("error")) throw IOException(item.getJSONObject("error").optString("message", "生成失败"))
        return Base64.decode(item.getString("b64_json"), Base64.DEFAULT)
    }
}
