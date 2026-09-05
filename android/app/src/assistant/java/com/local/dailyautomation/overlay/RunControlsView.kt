package com.local.dailyautomation.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

class RunControlsView(context: Context, callbacks: Callbacks) : LinearLayout(context) {
    interface Callbacks { fun onStop(); fun onTogglePause() }
    val stopButton = circleButton("■", Color.RED).apply { contentDescription = "停止本次运行"; setOnClickListener { callbacks.onStop() } }
    val pauseButton = circleButton("Ⅱ", Color.BLUE).apply { contentDescription = "暂停运行"; setOnClickListener { callbacks.onTogglePause() } }

    init {
        orientation = VERTICAL
        gravity = Gravity.END
        addView(stopButton)
        addView(pauseButton)
    }

    fun paused(value: Boolean) {
        pauseButton.text = if (value) "▶" else "Ⅱ"
        pauseButton.contentDescription = if (value) "继续运行" else "暂停运行"
    }

    private fun circleButton(label: String, color: Int) = Button(context).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 18f
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        layoutParams = LayoutParams(56.dp, 56.dp).apply { bottomMargin = 10.dp }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
}
