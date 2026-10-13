package com.doubao.mobilesub

import android.content.Context
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
 * 底部居中，可拖动；译文大号白字 + 原文小号灰字。
 */
class SubtitleOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var transView: TextView? = null
    private var origView: TextView? = null
    private var lastX = 0f
    private var lastY = 0f

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
            y = (64 * d).toInt()
        }

    fun show() {
        if (root != null) return
        val d = context.resources.displayMetrics.density
        val params = buildParams(d)

        val bg = GradientDrawable().apply {
            cornerRadius = 14 * d
            setColor(Color.argb(170, 0, 0, 0))
        }

        val trans = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val orig = TextView(context).apply {
            setTextColor(Color.rgb(165, 170, 178))
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.gravity = Gravity.CENTER_HORIZONTAL
        container.setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
        container.background = bg
        container.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = e.rawX
                    lastY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - lastX).toInt()
                    val dy = (e.rawY - lastY).toInt()
                    params.x += dx
                    params.y -= dy
                    lastX = e.rawX
                    lastY = e.rawY
                    runCatching { wm.updateViewLayout(container, params) }
                    true
                }
                else -> false
            }
        }
        container.addView(trans)
        container.addView(orig)

        root = container
        transView = trans
        origView = orig
        runCatching { wm.addView(container, params) }
    }

    fun update(orig: String, trans: String) {
        val t = transView ?: return
        t.text = trans
        origView?.apply {
            text = orig
            visibility = if (orig.isNotBlank()) View.VISIBLE else View.GONE
        }
    }

    fun dismiss() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        transView = null
        origView = null
    }
}
