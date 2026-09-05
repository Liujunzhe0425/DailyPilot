package com.local.dailyautomation.overlay

import android.app.AlertDialog
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.local.dailyautomation.notification.RunNotificationManager

object RunControlBus {
    var pause: (() -> Unit)? = null
    var resume: (() -> Unit)? = null
    var stop: (() -> Unit)? = null
}

class RunControlsService : Service(), RunControlsView.Callbacks {
    private lateinit var windowManager: WindowManager
    private var view: RunControlsView? = null
    private var paused = false

    override fun onCreate() {
        super.onCreate()
        // Android requires every startForegroundService() path to promote immediately,
        // even when a later permission check decides to stop the service.
        val notification = RunNotificationManager(this).progress(0, "准备运行")
        startForeground(RunNotificationManager.ID, notification)
        if (!Settings.canDrawOverlays(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        windowManager = getSystemService(WindowManager::class.java)
        view = RunControlsView(this, this).also {
            windowManager.addView(it, WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL })
        }
    }

    override fun onStop() {
        if (!paused) { paused = true; RunControlBus.pause?.invoke(); view?.paused(true) }
        confirm("停止本次运行？", accepted = {
            RunControlBus.stop?.invoke(); stopSelf()
        }, rejected = {
            paused = false; RunControlBus.resume?.invoke(); view?.paused(false)
        })
    }

    override fun onTogglePause() {
        if (!paused) {
            paused = true
            RunControlBus.pause?.invoke()
            view?.paused(true)
        } else confirm("继续刚才的运行？", accepted = {
            paused = false
            RunControlBus.resume?.invoke()
            view?.paused(false)
        })
    }

    private fun confirm(message: String, accepted: () -> Unit, rejected: () -> Unit = {}) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("确定") { _, _ -> accepted() }
            .setNegativeButton("取消") { _, _ -> rejected() }.setOnCancelListener { rejected() }.create().also {
                it.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                it.show()
            }
    }

    override fun onDestroy() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
