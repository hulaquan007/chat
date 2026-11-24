package com.example.aichat

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    private lateinit var cfg: Settings
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private val history = mutableListOf<Pair<String, String>>()

    private lateinit var avatar: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var chatList: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var etInput: EditText
    private lateinit var btnCall: Button

    private var recognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private var callActive = false
    private var state = State.IDLE
    private var pulse: ObjectAnimator? = null

    private val avatarFile get() = File(filesDir, "avatar.png")

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startCall() else toast("需要麦克风权限才能语音通话，你也可以用文字聊天")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cfg = Settings(this)

        avatar = findViewById(R.id.avatar)
        tvName = findViewById(R.id.tvName)
        tvStatus = findViewById(R.id.tvStatus)
        chatList = findViewById(R.id.chatList)
        scroll = findViewById(R.id.scroll)
        etInput = findViewById(R.id.etInput)
        btnCall = findViewById(R.id.btnCall)

        avatar.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dp(28).toFloat())
            }
        }
        avatar.clipToOutline = true

        initTts()
        initRecognizer()
        loadAvatar()
        refreshName()

        findViewById<TextView>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<Button>(R.id.btnAvatar).setOnClickListener { generateAvatar() }
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val t = etInput.text.toString().trim()
            if (t.isNotEmpty()) {
                etInput.setText("")
                sendUserText(t)
            }
        }
        btnCall.setOnClickListener { if (callActive) stopCall() else requestAndStartCall() }

        setState(State.IDLE)
        if (!cfg.readyForChat()) showSettings()
        else addBubble("你好呀，我是${cfg.name}。点下面的按钮，我们打电话聊聊吧～", false)
    }

    // ---------- 语音：识别与朗读 ----------

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts.language = Locale.CHINA
                tts.setPitch(1.2f)          // 稍高音调，更甜一点
                tts.setSpeechRate(1.0f)
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post { onSpeakDone() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { onSpeakDone() }
            }
        })
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = setState(State.LISTENING)
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!t.isNullOrEmpty()) tvStatus.text = "听到：$t"
                }

                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.trim()
                    if (t.isNullOrEmpty()) restartListeningLater() else sendUserText(t)
                }

                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restartListeningLater()
                        else -> {
                            toast("语音识别出错（$error），已挂断，可改用文字聊天")
                            stopCall()
                        }
                    }
                }
            })
        }
    }

    private fun requestAndStartCall() {
        if (recognizer == null) {
            toast("这台手机没有可用的语音识别服务，请用文字聊天")
            return
        }
        if (!cfg.readyForChat()) {
            showSettings(); return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) startCall() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startCall() {
        callActive = true
        btnCall.text = "📞 挂断"
        startListening()
    }

    private fun stopCall() {
        callActive = false
        btnCall.text = "📞 开始语音通话"
        main.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        tts.stop()
        setState(State.IDLE)
    }

    private fun startListening() {
        if (!callActive) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer?.startListening(intent)
    }

    private fun restartListeningLater() {
        if (callActive) main.postDelayed({ startListening() }, 500)
    }

    private fun onSpeakDone() {
        if (callActive) startListening() else setState(State.IDLE)
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            onSpeakDone(); return
        }
        setState(State.SPEAKING)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    // ---------- 聊天 ----------

    private fun sendUserText(text: String) {
        if (!cfg.readyForChat()) {
            showSettings(); return
        }
        addBubble(text, true)
        history.add("user" to text)
        setState(State.THINKING)
        val snapshot = history.takeLast(20).toList()
        exec.execute {
            try {
                val reply = ArkClient.chat(cfg, snapshot)
                main.post {
                    history.add("assistant" to reply)
                    addBubble(reply, false)
                    speak(reply)
                }
            } catch (e: Exception) {
                main.post {
                    toast("请求失败：${e.message}")
                    if (history.lastOrNull()?.first == "user") history.removeAt(history.size - 1)
                    if (callActive) stopCall() else setState(State.IDLE)
                }
            }
        }
    }

    private fun addBubble(text: String, mine: Boolean) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setTextColor(if (mine) 0xFFFFFFFF.toInt() else 0xFF222222.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (mine) 0xFFFF6FA8.toInt() else 0xFFF1F1F5.toInt())
            }
            maxWidth = (resources.displayMetrics.widthPixels * 0.75f).toInt()
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = if (mine) Gravity.END else Gravity.START
            setMargins(0, dp(4), 0, dp(4))
        }
        chatList.addView(tv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ---------- Seedream 形象 ----------

    private fun loadAvatar() {
        if (avatarFile.exists()) {
            avatar.setImageBitmap(BitmapFactory.decodeFile(avatarFile.absolutePath))
        }
    }

    private fun generateAvatar() {
        if (!cfg.readyForImage()) {
            toast("请先在设置里填写 API Key 和 Seedream 模型 ID")
            showSettings(); return
        }
        tvStatus.text = "正在用 Seedream 生成形象，约需十几秒…"
        findViewById<Button>(R.id.btnAvatar).isEnabled = false
        exec.execute {
            try {
                val bytes = ArkClient.generateImage(cfg)
                avatarFile.writeBytes(bytes)
                main.post {
                    loadAvatar()
                    findViewById<Button>(R.id.btnAvatar).isEnabled = true
                    setState(state)
                }
            } catch (e: Exception) {
                main.post {
                    toast("生成失败：${e.message}")
                    findViewById<Button>(R.id.btnAvatar).isEnabled = true
                    setState(state)
                }
            }
        }
    }

    // ---------- 设置 ----------

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun field(label: String, value: String, hint: String, multiline: Boolean = false): EditText {
            box.addView(TextView(this).apply {
                text = label
                textSize = 13f
                setPadding(0, dp(12), 0, 0)
            })
            val et = EditText(this).apply {
                setText(value)
                this.hint = hint
                textSize = 14f
                if (multiline) {
                    minLines = 2
                    isSingleLine = false
                } else isSingleLine = true
            }
            box.addView(et)
            return et
        }

        val eKey = field("API Key（火山方舟）", cfg.apiKey, "在方舟控制台创建")
        val eBase = field("接口地址", cfg.baseUrl, "https://ark.cn-beijing.volces.com/api/v3")
        val eChat = field("聊天模型 ID / 接入点 ID", cfg.chatModel, "如 doubao-… 或 ep-…")
        val eImg = field("Seedream 5.0 模型 ID / 接入点 ID", cfg.imageModel, "在方舟控制台模型列表复制")
        val eName = field("角色名字", cfg.name, "小夏")
        val ePrompt = field("形象描述（给 Seedream 的提示词）", cfg.portraitPrompt, "", true)

        AlertDialog.Builder(this)
            .setTitle("设置")
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton("保存") { _, _ ->
                cfg.apiKey = eKey.text.toString()
                cfg.baseUrl = eBase.text.toString().ifBlank { "https://ark.cn-beijing.volces.com/api/v3" }
                cfg.chatModel = eChat.text.toString()
                cfg.imageModel = eImg.text.toString()
                cfg.name = eName.text.toString()
                cfg.portraitPrompt = ePrompt.text.toString()
                refreshName()
                if (cfg.readyForImage() && !avatarFile.exists()) generateAvatar()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshName() {
        tvName.text = cfg.name
    }

    // ---------- 状态与 UI ----------

    private fun setState(s: State) {
        state = s
        tvStatus.text = when (s) {
            State.IDLE -> if (callActive) "通话中" else "未通话"
            State.LISTENING -> "我在听，你说吧…"
            State.THINKING -> "${cfg.name}在想…"
            State.SPEAKING -> "${cfg.name}在说话…"
        }
        pulse?.cancel()
        pulse = null
        avatar.scaleX = 1f
        avatar.scaleY = 1f
        if (s == State.SPEAKING) {
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                avatar,
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.04f),
                PropertyValuesHolder.ofFloat("scaleY", 1f, 1.04f)
            ).apply {
                duration = 500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
        exec.shutdownNow()
    }
}
