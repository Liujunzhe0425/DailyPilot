package com.local.dailyautomation.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Build
import com.local.dailyautomation.ui.HistoryActivity
import org.autojs.autojs6.R

object ProgressCalculator {
    fun calculate(total: Int, completed: Int, stepFraction: Double, previous: Int = 0, terminal: Boolean = false): Int {
        if (terminal) return 100
        if (total <= 0) return previous.coerceIn(0, 99)
        val raw = ((completed + stepFraction.coerceIn(0.0, 0.99)) / total * 100).toInt()
        return maxOf(previous, raw.coerceIn(0, 99))
    }
}

class RunNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init { NotificationChannels.ensure(context) }

    fun progress(value: Int, text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, NotificationChannels.PROGRESS) else Notification.Builder(context)
        val notification = builder
            .setSmallIcon(R.drawable.autojs6_material)
            .setContentTitle("每日领航")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, value.coerceIn(0, 100), false)
            .build()
        manager.notify(ID, notification)
        if (value >= 100) Handler(Looper.getMainLooper()).postDelayed({ manager.cancel(ID) }, 3000)
        return notification
    }

    fun failure(summary: String) {
        val intent = Intent(context, HistoryActivity::class.java)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, NotificationChannels.PROGRESS) else Notification.Builder(context)
        manager.notify(ID, builder
            .setSmallIcon(R.drawable.autojs6_material)
            .setContentTitle("自动化运行需要查看")
            .setContentText(summary)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build())
    }

    fun cancel() = manager.cancel(ID)

    companion object { const val ID = 7201 }
}
