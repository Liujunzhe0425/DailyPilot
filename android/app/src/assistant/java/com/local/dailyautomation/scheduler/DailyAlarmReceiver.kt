package com.local.dailyautomation.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.domain.BatchState
import com.local.dailyautomation.domain.CycleCalculator
import com.local.dailyautomation.unlock.UnlockGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyAlarmScheduler.ACTION_DAILY_ALARM) return
        val scheduledAt = intent.getLongExtra(DailyAlarmScheduler.EXTRA_SCHEDULED_AT, -1L)
        if (scheduledAt <= 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = AssistantRepository(context)
                val scheduled = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(scheduledAt),
                    ZoneId.systemDefault(),
                )
                val cycleId = CycleCalculator.cycleId(scheduled)
                repository.createCycle(cycleId, scheduledAt, BatchState.WAITING_UNLOCK.name)
                UnlockGate(context, repository).onScheduledCycle(cycleId)
                repository.getConfig()?.let { DailyAlarmScheduler(context).scheduleNext(it) }
                context.sendBroadcast(
                    Intent(DailyAlarmScheduler.ACTION_CYCLE_READY)
                        .setPackage(context.packageName)
                        .putExtra(DailyAlarmScheduler.EXTRA_CYCLE_ID, cycleId),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
