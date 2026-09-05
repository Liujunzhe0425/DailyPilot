package com.local.dailyautomation.decision

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.domain.BatchState
import com.local.dailyautomation.domain.TriggerKind
import com.local.dailyautomation.runtime.AutomationRuntime
import com.local.dailyautomation.notification.RunNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DecisionOverlayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var overlay: DecisionOverlayView? = null
    private var timer: CountDownTimer? = null
    private var activeCycleId: String? = null
    private var settling = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, foregroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_SHOW) return START_NOT_STICKY
        val cycleId = intent.getStringExtra(EXTRA_CYCLE_ID) ?: return START_NOT_STICKY
        if (activeCycleId != null) return START_NOT_STICKY
        activeCycleId = cycleId
        if (!Settings.canDrawOverlays(this)) {
            settle(Decision.RUN_LATER)
            return START_NOT_STICKY
        }
        overlay = DecisionOverlayView(this, ::settle).also {
            it.updateCountdown(DECISION_TIMEOUT_MILLIS)
            it.show()
        }
        timer = object : CountDownTimer(DECISION_TIMEOUT_MILLIS, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                overlay?.updateCountdown(millisUntilFinished)
            }

            override fun onFinish() = settle(DecisionPolicy.timeoutDecision())
        }.start()
        return START_NOT_STICKY
    }

    private fun settle(decision: Decision) {
        val cycleId = activeCycleId ?: return
        if (settling) return
        if (decision == Decision.RUN_NOW) {
            val missing = AutomationRuntime.blockingPermission(this)
            if (missing != null) {
                timer?.cancel()
                timer = null
                overlay?.showError(missing)
                RunNotificationManager(this).failure("未能启动：$missing")
                return
            }
            settling = true
            timer?.cancel()
            timer = null
            overlay?.showStarting()
            scope.launch { startScheduledRun(cycleId) }
            return
        }
        settling = true
        activeCycleId = null
        timer?.cancel()
        timer = null
        overlay?.remove()
        overlay = null
        scope.launch {
            val state = if (decision == Decision.ALL_COMPLETED) BatchState.COMPLETED else BatchState.MANUAL_ONLY
            AssistantRepository(this@DecisionOverlayService)
                .markDecision(cycleId, decision.name, state.name)
            withContext(Dispatchers.Main) { finishDecision(cycleId, decision) }
        }
    }

    private suspend fun startScheduledRun(cycleId: String) {
        try {
            val repository = AssistantRepository(this@DecisionOverlayService)
            val modules = repository.getModules().filter { it.enabled }.sortedBy { it.sortOrder }.map { it.moduleId }
            check(modules.isNotEmpty()) { "没有已启用的模块" }
            AutomationRuntime.start(this@DecisionOverlayService, TriggerKind.SCHEDULED, modules, cycleId)
            repository.markDecision(cycleId, Decision.RUN_NOW.name, BatchState.RUNNING.name)
            withContext(Dispatchers.Main) {
                activeCycleId = null
                overlay?.remove()
                overlay = null
                finishDecision(cycleId, Decision.RUN_NOW)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to start scheduled automation for $cycleId", error)
            val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            withContext(Dispatchers.Main) {
                settling = false
                overlay?.showError("启动失败：$message")
            }
            RunNotificationManager(this).failure("定时任务启动失败：$message")
        }
    }

    private fun finishDecision(cycleId: String, decision: Decision) {
        sendBroadcast(
            Intent(ACTION_DECIDED)
                .setPackage(packageName)
                .putExtra(EXTRA_CYCLE_ID, cycleId)
                .putExtra(EXTRA_DECISION, decision.name),
        )
        stopSelf()
    }

    override fun onDestroy() {
        timer?.cancel()
        overlay?.remove()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun foregroundNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "自动运行确认",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("每日领航")
            .setContentText("等待运行选择")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_SHOW = "com.local.dailyautomation.action.SHOW_DECISION"
        const val ACTION_DECIDED = "com.local.dailyautomation.action.DECIDED"
        const val EXTRA_CYCLE_ID = "cycleId"
        const val EXTRA_DECISION = "decision"
        const val DECISION_TIMEOUT_MILLIS = 15_000L
        private const val CHANNEL_ID = "assistant_decision"
        private const val NOTIFICATION_ID = 41002
        private const val TAG = "DecisionOverlayService"

        fun show(context: Context, cycleId: String) {
            val intent = Intent(context, DecisionOverlayService::class.java)
                .setAction(ACTION_SHOW)
                .putExtra(EXTRA_CYCLE_ID, cycleId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
