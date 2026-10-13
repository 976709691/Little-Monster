package com.doubao.mobilesub

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 主界面：设置（翻译引擎/模型/语言/API Key）+ 下载模型 + 开始/停止。
 * 纯代码构建 UI，无第三方依赖。
 */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var engineSpin: Spinner
    private lateinit var modelSpin: Spinner
    private lateinit var keyInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var llmModelInput: EditText
    private lateinit var dlBtn: Button
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button

    // 语言设置（与截图「语言设置」弹窗对应）
    private var langSource = 0          // SRC_LANGS 索引 → Whisper 语言代码
    private var langTarget = "简体中文"  // 字幕翻译目标（"不翻译"=仅显示原文）
    private var langBilingual = true    // 同时显示双语

    // 字幕外观（与截图「字幕偏好设置」对应）
    private var fontSize = 22           // 字幕字号 sp
    private var bgAlpha = 70            // 背景不透明度 0-100

    companion object {
        private const val REQ_OVERLAY = 1
        private const val REQ_PROJECTION = 2
        private const val REQ_NOTIF = 3
        private const val MODEL_BASE = "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-"

        private val SRC_LANGS = arrayOf(
            "自动检测", "英语", "日语", "韩语", "中文", "法语", "德语", "俄语", "西班牙语",
            "葡萄牙语", "意大利语", "阿拉伯语", "泰语", "越南语", "土耳其语", "波兰语", "乌克兰语", "印地语"
        )
        private val SRC_CODES = arrayOf(
            "", "en", "ja", "ko", "zh", "fr", "de", "ru", "es",
            "pt", "it", "ar", "th", "vi", "tr", "pl", "uk", "hi"
        )
        private val TARGET_LANGS = arrayOf(
            "不翻译", "简体中文", "繁体中文", "英语", "日语", "韩语", "法语", "德语",
            "西班牙语", "俄语", "泰语", "越南语", "阿拉伯语", "葡萄牙语", "意大利语"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cfg = AppConfig.load(this)
        langTarget = cfg.targetLang
        langBilingual = cfg.showBilingual
        langSource = SRC_CODES.indexOfFirst { it == cfg.language }.takeIf { it >= 0 } ?: 0
        fontSize = cfg.fontSizeSp
        bgAlpha = cfg.bgAlpha
        buildUi(cfg)
        refreshStatus()
        if (intent.getBooleanExtra(SubtitleOverlay.EXTRA_OPEN_STYLE, false)) {
            showStyleDialog()
        }
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

        // 语言设置（弹窗入口，与投屏所示一致）
        root.addView(Button(this).apply {
            text = "语言设置（声源语言 / 字幕翻译 / 双语）"
            setOnClickListener { showLangDialog() }
        })

        // 字幕外观（弹窗入口，与投屏所示一致）
        root.addView(Button(this).apply {
            text = "字幕外观（字体大小 / 背景不透明度）"
            setOnClickListener { showStyleDialog() }
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

        // 语言由「语言设置」弹窗控制（声源语言/字幕翻译/同时显示双语）

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

    // ------------------------------------------------------------- 语言设置弹窗
    private fun showLangDialog() {
        val density = resources.displayMetrics.density
        val pad = (18 * density).toInt()

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }

        // 声源语言
        content.addView(label("声源语言"))
        val srcSpin = Spinner(this)
        srcSpin.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, SRC_LANGS.toList()
        )
        srcSpin.setSelection(langSource)
        content.addView(srcSpin)

        // 字幕翻译
        content.addView(label("字幕翻译"))
        val tgtSpin = Spinner(this)
        tgtSpin.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, TARGET_LANGS.toList()
        )
        tgtSpin.setSelection(TARGET_LANGS.indexOf(langTarget).takeIf { it >= 0 } ?: 1)
        content.addView(tgtSpin)

        // 同时显示双语
        val bilingualSwitch = Switch(this).apply {
            text = "同时显示双语"
            textSize = 15f
            isChecked = langBilingual
            setPadding(0, (10 * density).toInt(), 0, 0)
        }
        content.addView(bilingualSwitch)
        content.addView(TextView(this).apply {
            text = "不翻译时，将仅显示声源语言"
            textSize = 12f
            setTextColor(0xFF666666.toInt())
            setPadding(0, (4 * density).toInt(), 0, (6 * density).toInt())
        })

        AlertDialog.Builder(this)
            .setTitle("语言设置")
            .setView(content)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                langSource = srcSpin.selectedItemPosition
                langTarget = TARGET_LANGS[tgtSpin.selectedItemPosition]
                langBilingual = bilingualSwitch.isChecked
                refreshStatus()
            }
            .show()
    }

    // ------------------------------------------------------------- 字幕外观弹窗
    private fun showStyleDialog() {
        val density = resources.displayMetrics.density
        val pad = (18 * density).toInt()

        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(0, (10 * density).toInt(), 0, (4 * density).toInt())
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
        }

        // 字幕外观示例（实时反映字号与背景透明度）
        val sample = TextView(this).apply {
            text = "字幕外观示例\n你给予我的，远不止这些。"
            textSize = fontSize.toFloat()
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 8 * density
                setColor(android.graphics.Color.argb(bgAlpha * 255 / 100, 0, 0, 0))
            }
        }
        content.addView(label("字幕外观示例"))
        content.addView(sample)

        // 字体大小
        content.addView(label("字体大小"))
        val fontLabel = TextView(this).apply {
            text = sizeName(fontSize) + "（${fontSize}sp）"
            textSize = 13f
            gravity = Gravity.END
        }
        val fontBar = SeekBar(this).apply {
            max = 100
            progress = ((fontSize - 14) * 100 / 16).coerceIn(0, 100)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                    val sp = 14 + p * 16 / 100
                    fontLabel.text = sizeName(sp) + "（${sp}sp）"
                    sample.textSize = sp.toFloat()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        content.addView(fontBar)
        content.addView(fontLabel)

        // 背景不透明度
        content.addView(label("背景不透明度"))
        val alphaLabel = TextView(this).apply {
            text = "$bgAlpha%"
            textSize = 13f
            gravity = Gravity.END
        }
        val alphaBar = SeekBar(this).apply {
            max = 100
            progress = bgAlpha
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) {
                    alphaLabel.text = "$p%"
                    (sample.background as? android.graphics.drawable.GradientDrawable)?.setColor(
                        android.graphics.Color.argb(p * 255 / 100, 0, 0, 0)
                    )
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        content.addView(alphaBar)
        content.addView(alphaLabel)

        AlertDialog.Builder(this)
            .setTitle("字幕偏好设置")
            .setView(content)
            .setNeutralButton("还原", { _, _ ->
                fontBar.progress = ((22 - 14) * 100 / 16).coerceIn(0, 100)
                alphaBar.progress = 70
            })
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                fontSize = 14 + fontBar.progress * 16 / 100
                bgAlpha = alphaBar.progress
                AppConfig.save(this, collectConfig())
                refreshStatus()
            }
            .show()
    }

    private fun sizeName(sp: Int): String = when {
        sp < 17 -> "小号"
        sp <= 24 -> "中号"
        else -> "大号"
    }

    // ------------------------------------------------------------- 状态
    private fun collectConfig(): AppConfig {
        val cfg = AppConfig(
            engine = if (engineSpin.selectedItemPosition == 0) "llm" else "dummy",
            apiKey = keyInput.text.toString().trim(),
            baseUrl = urlInput.text.toString().trim().ifEmpty { "https://api.deepseek.com/v1" },
            llmModel = llmModelInput.text.toString().trim().ifEmpty { "deepseek-chat" },
            language = SRC_CODES[langSource],
            modelSize = when (modelSpin.selectedItemPosition) {
                0 -> "tiny"; 2 -> "small"; else -> "base"
            },
            targetLang = langTarget,
            showBilingual = langBilingual,
            fontSizeSp = fontSize,
            bgAlpha = bgAlpha
        )
        AppConfig.save(this, cfg)
        return cfg
    }

    private fun refreshStatus() {
        val cfg = AppConfig.load(this)
        val overlay = Settings.canDrawOverlays(this)
        val model = AppConfig.modelReady(this, cfg.modelSize)
        status.text = "悬浮窗权限：${if (overlay) "已开启" else "未开启"} ｜ 模型：${if (model) "已下载" else "未下载（${cfg.modelSize}）"}" +
            "\n语言：${SRC_LANGS[langSource]} → ${langTarget}${if (langBilingual) "（双语）" else ""}" +
            "\n字幕：${sizeName(fontSize)}（${fontSize}sp） 背景 ${bgAlpha}%"
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
