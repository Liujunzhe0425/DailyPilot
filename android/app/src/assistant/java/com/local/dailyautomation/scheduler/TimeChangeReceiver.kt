package com.local.dailyautomation.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.dailyautomation.data.AssistantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AssistantRepository(context).getConfig()?.let { DailyAlarmScheduler(context).scheduleNext(it) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
        )
    }
}
