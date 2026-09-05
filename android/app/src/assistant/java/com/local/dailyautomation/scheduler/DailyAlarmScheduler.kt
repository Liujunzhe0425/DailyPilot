package com.local.dailyautomation.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.local.dailyautomation.data.AppConfigEntity
import com.local.dailyautomation.domain.CycleCalculator
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyAlarmScheduler(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val healthPreferences = appContext.getSharedPreferences(HEALTH_PREFERENCES, Context.MODE_PRIVATE)

    fun scheduleNext(config: AppConfigEntity): ScheduleResult {
        if (!config.automationEnabled) {
            cancel()
            return ScheduleResult.Disabled
        }
        if (!canScheduleExactAlarms()) {
            healthPreferences.edit().putLong(KEY_EXACT_ALARM_BLOCKED_AT, clock.millis()).apply()
            cancel()
            return ScheduleResult.ExactAlarmPermissionRequired
        }

        val zone = zoneProvider()
        val next = CycleCalculator.nextOccurrence(ZonedDateTime.now(clock), config.runMinuteOfDay, zone)
        val scheduledAt = next.toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            scheduledAt,
            alarmPendingIntent(scheduledAt),
        )
        healthPreferences.edit().remove(KEY_EXACT_ALARM_BLOCKED_AT).apply()
        return ScheduleResult.Scheduled(scheduledAt, CycleCalculator.cycleId(next))
    }

    fun cancel() {
        alarmManager.cancel(alarmPendingIntent(0L))
    }

    fun canScheduleExactAlarms(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager.canScheduleExactAlarms()

    private fun alarmPendingIntent(scheduledAt: Long): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        REQUEST_CODE,
        Intent(appContext, DailyAlarmReceiver::class.java)
            .setAction(ACTION_DAILY_ALARM)
            .putExtra(EXTRA_SCHEDULED_AT, scheduledAt),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    sealed interface ScheduleResult {
        data object Disabled : ScheduleResult
        data object ExactAlarmPermissionRequired : ScheduleResult
        data class Scheduled(val scheduledAt: Long, val cycleId: String) : ScheduleResult
    }

    companion object {
        const val ACTION_DAILY_ALARM = "com.local.dailyautomation.action.DAILY_ALARM"
        const val ACTION_CYCLE_READY = "com.local.dailyautomation.action.CYCLE_READY"
        const val EXTRA_SCHEDULED_AT = "scheduledAt"
        const val EXTRA_CYCLE_ID = "cycleId"
        const val HEALTH_PREFERENCES = "assistant_permission_health"
        const val KEY_EXACT_ALARM_BLOCKED_AT = "exact_alarm_blocked_at"
        private const val REQUEST_CODE = 41001
    }
}
