package com.doubao.mobilesub

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 系统级悬浮字幕窗（TYPE_APPLICATION_OVERLAY），显示在任意 App 之上。
 * 底部居中、左对齐多行字幕，黑色半透明背景：
 * - 顶部控制行：语言方向标签 + "⚙ 字幕外观" + "— 缩小"；
 * - 双语模式：原文小号白字在上 + 译文大号白字在下；单语仅译文；不翻译仅原文；
 * - 字体大小 / 背景不透明度可通过字幕外观设置调节（applyStyle）；
 * - 点「—」或字幕条任意处可缩小为小胶囊，再点恢复；
 * - 整条可按住拖动。
 */
class SubtitleOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var controlRow: LinearLayout? = null
    private var tagView: TextView? = null
    private var origView: TextView? = null
    private var transView: TextView? = null
    private var btnSettings: TextView? = null
    private var btnMin: TextView? = null

    private var lastX = 0f
    private var lastY = 0f
    private var downX = 0f
    private var downY = 0f
    private var minimized = false

    private var fontSizeSp = 22f
    private var bgAlpha = 70

    companion object {
        const val EXTRA_OPEN_STYLE = "open_style"
    }

    private fun buildParams(d: Float): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (48 * d).toInt()
        }

    fun show() {
        if (root != null) return
        val d = context.resources.displayMetrics.density
        val params = buildParams(d)
        val pad = (16 * d).toInt()
        val vpad = (10 * d).toInt()

        val bg = GradientDrawable().apply {
            cornerRadius = 12 * d
            setColor(Color.argb(alphaOf(70), 0, 0, 0))
        }

        // 控制行：语言方向标签 + 设置 / 缩小按钮
        val tag = TextView(context).apply {
            setTextColor(Color.rgb(190, 196, 204))
            textSize = 12f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        val settingsBtn = TextView(context).apply {
            text = "⚙"
            textSize = 16f
            setTextColor(Color.rgb(200, 206, 214))
            gravity = Gravity.CENTER
            setPadding((6 * d).toInt(), 0, (6 * d).toInt(), 0)
        }
        val minBtn = TextView(context).apply {
            text = "—"
            textSize = 18f
            setTextColor(Color.rgb(200, 206, 214))
            gravity = Gravity.CENTER
            setPadding((6 * d).toInt(), 0, (4 * d).toInt(), 0)
        }
        val controlRowView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            addView(tag, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(settingsBtn)
            addView(minBtn)
        }

        val orig = TextView(context).apply {
            setTextColor(Color.rgb(230, 233, 238))
            textSize = 15f
            gravity = Gravity.START
            visibility = View.GONE
        }
        val trans = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START
        }

        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.START
        container.setPadding(pad, vpad, pad, vpad)
        container.background = bg
        container.setOnTouchListener { _, e -> handleTouch(e, d) }
        container.addView(controlRowView)
        container.addView(orig)
        container.addView(trans)

        root = container
        tagView = tag
        origView = orig
        transView = trans
        btnSettings = settingsBtn
        btnMin = minBtn
        controlRow = controlRowView
        runCatching { wm.addView(container, params) }
    }

    /** 点击与拖动处理（点击按钮/字幕条 → 对应动作；移动 → 拖动）。 */
    private fun handleTouch(e: MotionEvent, d: Float): Boolean {
        return when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.rawX
                lastY = e.rawY
                downX = e.x
                downY = e.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - lastX).toInt()
                val dy = (e.rawY - lastY).toInt()
                if (kotlin.math.abs(e.rawX - lastX) > 2 * d || kotlin.math.abs(e.rawY - lastY) > 2 * d) {
                    val params = root?.layoutParams as? WindowManager.LayoutParams ?: return true
                    params.x += dx
                    params.y -= dy
                    lastX = e.rawX
                    lastY = e.rawY
                    runCatching { wm.updateViewLayout(root, params) }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val moved = kotlin.math.abs(e.x - downX) < 24 * d && kotlin.math.abs(e.y - downY) < 24 * d
                if (moved) {
                    val s = btnSettings
                    val m = btnMin
                    if (s != null && e.x >= s.left && e.x <= s.right && e.y >= s.top && e.y <= s.bottom) {
                        openSettings()
                    } else if (m != null && e.x >= m.left && e.x <= m.right && e.y >= m.top && e.y <= m.bottom) {
                        toggleMinimize()
                    } else {
                        toggleMinimize()
                    }
                }
                true
            }
            else -> false
        }
    }

    /** 打开主界面的字幕外观设置。 */
    private fun openSettings() {
        runCatching {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_OPEN_STYLE, true)
            context.startActivity(intent)
        }
    }

    /** 缩小为小胶囊 / 恢复。 */
    fun toggleMinimize() {
        minimized = !minimized
        val d = context.resources.displayMetrics.density
        root?.let { c ->
            c.setPadding(
                (16 * d).toInt(), (if (minimized) 6 else 10) * d.toInt(),
                (16 * d).toInt(), (if (minimized) 6 else 10) * d.toInt()
            )
            (c.background as? GradientDrawable)?.cornerRadius = (if (minimized) 999f else 12 * d)
            origView?.visibility = if (minimized) View.GONE else if (origVisible) View.VISIBLE else View.GONE
            transView?.visibility = if (minimized) View.GONE else View.VISIBLE
            btnMin?.text = if (minimized) "+" else "—"
        }
    }

    /** 应用字幕外观样式。 */
    fun applyStyle(fontSizeSp: Int, bgAlpha: Int) {
        this.fontSizeSp = fontSizeSp.toFloat()
        this.bgAlpha = bgAlpha
        val d = context.resources.displayMetrics.density
        (root?.background as? GradientDrawable)?.setColor(Color.argb(alphaOf(bgAlpha), 0, 0, 0))
        transView?.textSize = fontSizeSp.toFloat()
        origView?.textSize = fontSizeSp * 0.68f
        tagView?.textSize = fontSizeSp * 0.55f
        btnSettings?.textSize = fontSizeSp * 0.72f
        btnMin?.textSize = fontSizeSp * 0.8f
    }

    private fun alphaOf(pct: Int): Int {
        val v = if (pct < 0) 0 else if (pct > 100) 100 else pct
        return v * 255 / 100
    }

    private var origVisible = false

    /**
     * 更新字幕内容。
     * @param orig 原文（声源语言文本）
     * @param trans 译文（不翻译时为原文本身）
     * @param langTag 语言方向标签（如"英语→简体中文"），为空则不显示
     * @param translating 是否处于翻译状态
     * @param showBilingual 是否同时显示双语
     */
    fun update(orig: String, trans: String, langTag: String, translating: Boolean, showBilingual: Boolean) {
        tagView?.apply {
            text = langTag
            visibility = if (langTag.isNotBlank()) View.VISIBLE else View.GONE
        }
        origVisible = translating && showBilingual && orig.isNotBlank()
        if (!minimized) {
            origView?.apply {
                text = orig
                visibility = if (origVisible) View.VISIBLE else View.GONE
            }
            transView?.apply {
                text = if (translating && !showBilingual) trans else if (translating) trans else orig
            }
        }
    }

    fun dismiss() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        controlRow = null
        tagView = null
        origView = null
        transView = null
        btnSettings = null
        btnMin = null
    }
}
