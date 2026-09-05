package com.local.dailyautomation.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.dailyautomation.data.AssistantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != ACTION_QUICK_BOOT) return
        reschedule(context)
    }

    private fun reschedule(context: Context) {
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
        const val ACTION_QUICK_BOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
