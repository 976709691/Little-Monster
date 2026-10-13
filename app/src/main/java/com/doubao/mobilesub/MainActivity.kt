package com.doubao.mobilesub

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 主界面：设置（翻译引擎/模型/API Key）+ 下载模型 + 开始/停止。
 * 纯代码构建 UI，无第三方依赖。
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var engineSpin: Spinner
    private lateinit var modelSpin: Spinner
    private lateinit var langInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var llmModelInput: EditText
    private lateinit var dlBtn: Button
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    companion object {
        private const val REQ_OVERLAY = 1
        private const val REQ_PROJECTION = 2
        private const val REQ_NOTIF = 3
        private const val MODEL_BASE = "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = AppConfig.load(this)
        buildUi(cfg)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ------------------------------------------------------------- UI
    private fun buildUi(cfg: AppConfig) {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (24 * density).toInt(), pad, pad)
        }

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }

        root.addView(TextView(this).apply {
            text = "影视同声传译"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * density).toInt())
        })

        // 翻译引擎
        root.addView(label("翻译引擎"))
        engineSpin = Spinner(this)
        engineSpin.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, listOf("llm（大模型翻译，推荐）", "dummy（不翻译，只显示原文）")
        )
        engineSpin.setSelection(if (cfg.engine == "llm") 0 else 1)
        root.addView(engineSpin)

        // 模型大小
        root.addView(label("语音模型（越大越准越慢）"))
        modelSpin = Spinner(this)
        modelSpin.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, listOf("tiny（最快）", "base（均衡，推荐）", "small（更准）")
        )
        modelSpin.setSelection(when (cfg.modelSize) {
            "tiny" -> 0; "small" -> 2; else -> 1
        })
        root.addView(modelSpin)

        // 语言
        root.addView(label("源语言（留空=自动检测，或填 en / ja / ko / zh）"))
        langInput = EditText(this).apply { setText(cfg.language); hint = "auto" }
        root.addView(langInput)

        // API Key
        root.addView(label("LLM API Key（DeepSeek 等 OpenAI 兼容接口）"))
        keyInput = EditText(this).apply {
            setText(cfg.apiKey)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            hint = "sk-xxx"
        }
        root.addView(keyInput)

        root.addView(label("接口地址"))
        urlInput = EditText(this).apply { setText(cfg.baseUrl) }
        root.addView(urlInput)

        root.addView(label("模型名"))
        llmModelInput = EditText(this).apply { setText(cfg.llmModel) }
        root.addView(llmModelInput)

        // 进度
        progress = ProgressBar(this).apply {
            isIndeterminate = false
            max = 100
        }
        root.addView(progress)

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }
        root.addView(status)

        // 按钮
        dlBtn = Button(this).apply { text = "下载模型（首次必做）" }
        startBtn = Button(this).apply { text = "开始翻译（需先授权录屏）" }
        stopBtn = Button(this).apply { text = "停止" }
        root.addView(dlBtn)
        root.addView(startBtn)
        root.addView(stopBtn)

        root.addView(TextView(this).apply {
            text = "使用流程：\n1. 下载模型\n2. 填 API Key（dummy 模式可跳过）\n" +
                "3. 点「开始翻译」→ 允许悬浮窗 → 允许投屏/录屏\n" +
                "4. 回到播放器放电影，字幕悬浮窗出现在屏幕下方\n" +
                "5. 看完后回到本应用点「停止」\n\n" +
                "说明：DRM 加密内容（部分在线视频）无法捕获声音；" +
                "识别与翻译在后台进行，字幕可按住拖动。"
            textSize = 13f
            setPadding(0, (12 * density).toInt(), 0, 0)
        })

        dlBtn.setOnClickListener { downloadModel() }
        startBtn.setOnClickListener { onStartClick() }
        stopBtn.setOnClickListener {
            stopService(Intent(this, CaptureService::class.java))
            status.text = "已停止"
        }

        setContentView(root)
    }

    // ------------------------------------------------------------- 状态
    private fun collectConfig(): AppConfig {
        val cfg = AppConfig(
            engine = if (engineSpin.selectedItemPosition == 0) "llm" else "dummy",
            apiKey = keyInput.text.toString().trim(),
            baseUrl = urlInput.text.toString().trim().ifEmpty { "https://api.deepseek.com/v1" },
            llmModel = llmModelInput.text.toString().trim().ifEmpty { "deepseek-chat" },
            language = langInput.text.toString().trim(),
            modelSize = when (modelSpin.selectedItemPosition) {
                0 -> "tiny"; 2 -> "small"; else -> "base"
            }
        )
        AppConfig.save(this, cfg)
        return cfg
    }

    private fun refreshStatus() {
        val cfg = AppConfig.load(this)
        val overlay = Settings.canDrawOverlays(this)
        val model = AppConfig.modelReady(this, cfg.modelSize)
        status.text = "悬浮窗权限：${if (overlay) "已开启" else "未开启"} ｜ 模型：${if (model) "已下载" else "未下载（${cfg.modelSize}）"}"
    }

    // ------------------------------------------------------------- 下载模型
    private fun downloadModel() {
        val cfg = collectConfig()
        val size = cfg.modelSize
        val dir = AppConfig.modelDir(this, size)
        dir.mkdirs()
        val files = listOf("$size-encoder.int8.onnx", "$size-decoder.int8.onnx", "$size-tokens.txt")
        dlBtn.isEnabled = false
        status.text = "开始下载 $size 模型…"
        Thread {
            var ok = true
            try {
                for (f in files) {
                    val target = File(dir, f)
                    if (target.exists() && target.length() > 1024) continue
                    val conn = URL(MODEL_BASE + size + "/resolve/main/" + f).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    conn.setRequestProperty("User-Agent", "MovieSubtitle/1.0")
                    conn.connect()
                    val total = conn.contentLengthLong
                    val input = conn.inputStream
                    val out = target.outputStream()
                    val buf = ByteArray(64 * 1024)
                    var got = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        val g = got
                        runOnUiThread {
                            if (total > 0) {
                                progress.isIndeterminate = false
                                progress.progress = ((g * 100) / total).toInt()
                            } else {
                                progress.isIndeterminate = true
                            }
                            status.text = "下载 $f  ${g / 1024 / 1024}MB/${total / 1024 / 1024}MB"
                        }
                    }
                    out.flush()
                    out.close()
                    input.close()
                }
            } catch (e: Exception) {
                ok = false
                val m = e.message ?: "网络错误"
                runOnUiThread { status.text = "模型下载失败：$m（可重试）" }
            }
            runOnUiThread {
                progress.isIndeterminate = false
                progress.progress = 0
                dlBtn.isEnabled = true
                if (ok) status.text = "模型下载完成 ✓"
                refreshStatus()
            }
        }.start()
    }

    // ------------------------------------------------------------- 开始流程
    private fun onStartClick() {
        val cfg = collectConfig()

        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ),
                REQ_OVERLAY
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), REQ_NOTIF)
            return
        }
        if (!AppConfig.modelReady(this, cfg.modelSize)) {
            status.text = "请先点击「下载模型」"
            return
        }
        if (cfg.engine == "llm" && cfg.apiKey.isBlank()) {
            status.text = "请填写 API Key（或把翻译引擎设为 dummy）"
            return
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                val svc = Intent(this, CaptureService::class.java)
                    .putExtra(CaptureService.EXTRA_CODE, resultCode)
                    .putExtra(CaptureService.EXTRA_DATA, data)
                if (Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
                status.text = "已启动，正在监听声音并翻译…"
            } else {
                status.text = "已取消授权"
            }
        }
    }
}
