package com.example.aichat

import android.content.Context

/** 本地保存的配置（SharedPreferences）。 */
class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    private fun get(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var apiKey: String
        get() = get("apiKey", "")
        set(v) = put("apiKey", v.trim())

    /** 火山方舟（国内）默认地址；BytePlus 国际版可改成 https://ark.ap-southeast.bytepluses.com/api/v3 */
    var baseUrl: String
        get() = get("baseUrl", "https://ark.cn-beijing.volces.com/api/v3")
        set(v) = put("baseUrl", v.trim().trimEnd('/'))

    /** 聊天模型 ID 或接入点 ID（在方舟控制台获取） */
    var chatModel: String
        get() = get("chatModel", "")
        set(v) = put("chatModel", v.trim())

    /** Seedream 5.0 的模型 ID 或接入点 ID（在方舟控制台获取） */
    var imageModel: String
        get() = get("imageModel", "")
        set(v) = put("imageModel", v.trim())

    var name: String
        get() = get("name", "小夏")
        set(v) = put("name", v.trim().ifEmpty { "小夏" })

    var portraitPrompt: String
        get() = get(
            "portraitPrompt",
            "一位25岁左右的中国年轻女性，长发，温柔甜美的微笑，休闲得体的居家穿搭，柔和自然光，半身人像，真实摄影风格，背景虚化，画面干净"
        )
        set(v) = put("portraitPrompt", v.trim())

    fun readyForChat() = apiKey.isNotEmpty() && chatModel.isNotEmpty()
    fun readyForImage() = apiKey.isNotEmpty() && imageModel.isNotEmpty()

    fun systemPrompt(): String =
        "你是${name}，一位虚构的AI女生，二十多岁，温柔、俏皮、有点爱撒娇，正在和用户语音聊天。" +
            "规则：1）像真人打电话一样口语化，每次只说1到3句话，不超过60个字；" +
            "2）不要使用表情符号、markdown、括号动作描写，因为你说的话会被直接朗读出来；" +
            "3）如果用户问起，坦诚说明你是AI，不是真人；" +
            "4）保持健康得体的聊天氛围，不输出露骨的性内容，不索要隐私、转账或任何财物；" +
            "5）多关心用户的心情，适当提问让话题延续下去。"
}
