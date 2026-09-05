package com.local.dailyautomation.decision

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.ceil

class DecisionOverlayView(
    context: Context,
    private val onDecision: (Decision) -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val countdown = TextView(context).apply {
        textSize = 18f
        setTextColor(Color.DKGRAY)
        gravity = Gravity.CENTER
    }
    private val status = TextView(context).apply {
        textSize = 15f
        setTextColor(Color.RED)
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private val runButton = Button(context).apply {
        text = "运行"
        setOnClickListener { onDecision(Decision.RUN_NOW) }
    }
    private val laterButton = Button(context).apply {
        text = "稍后运行"
        setOnClickListener { onDecision(Decision.RUN_LATER) }
    }
    private val root: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(40, 32, 40, 32)
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 28f
            setStroke(2, Color.LTGRAY)
        }
        addView(TextView(context).apply {
            text = "运行每日自动化任务？"
            textSize = 20f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        })
        addView(countdown, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 16 })
        addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(runButton)
            addView(laterButton)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
    }
    private var attached = false

    fun show() {
        if (attached) return
        windowManager.addView(
            root,
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
            },
        )
        attached = true
    }

    fun updateCountdown(remainingMillis: Long) {
        countdown.text = "${ceil(remainingMillis / 1000.0).toInt().coerceIn(0, 15)} 秒后自动稍后运行"
    }

    fun showStarting() {
        countdown.text = "正在启动自动化…"
        status.visibility = View.GONE
        runButton.isEnabled = false
        laterButton.isEnabled = false
    }

    fun showError(message: String) {
        countdown.text = "未能启动，请处理后重试或选择稍后运行"
        status.text = message
        status.visibility = View.VISIBLE
        runButton.text = "重试运行"
        runButton.isEnabled = true
        laterButton.isEnabled = true
    }

    fun remove() {
        if (!attached) return
        runCatching { windowManager.removeViewImmediate(root) }
        attached = false
    }
}
