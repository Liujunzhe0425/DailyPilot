package com.local.dailyautomation.domain

import java.time.ZoneId
import java.time.ZonedDateTime

object CycleCalculator {
    fun nextOccurrence(now: ZonedDateTime, minuteOfDay: Int, zone: ZoneId): ZonedDateTime {
        require(minuteOfDay in 0..1439) { "minuteOfDay must be between 0 and 1439" }
        val localNow = now.withZoneSameInstant(zone)
        val candidate = localNow.toLocalDate()
            .atStartOfDay(zone)
            .plusMinutes(minuteOfDay.toLong())
        return if (candidate.isAfter(localNow)) candidate else candidate.plusDays(1)
    }

    fun cycleId(scheduledAt: ZonedDateTime): String = scheduledAt.toLocalDate().toString()
}
