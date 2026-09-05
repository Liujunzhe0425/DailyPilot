package com.local.dailyautomation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CycleCalculatorTest {
    @Test
    fun `future time schedules today and past time schedules tomorrow`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 7, 24, 8, 0, 0, 0, zone)

        assertEquals(
            "2026-07-24T09:00+08:00[Asia/Shanghai]",
            CycleCalculator.nextOccurrence(now, 9 * 60, zone).toString(),
        )
        assertEquals(
            "2026-07-25T07:00+08:00[Asia/Shanghai]",
            CycleCalculator.nextOccurrence(now, 7 * 60, zone).toString(),
        )
    }

    @Test
    fun `exact scheduled minute advances to tomorrow`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val now = ZonedDateTime.of(2026, 7, 24, 8, 0, 0, 0, zone)

        assertEquals(
            "2026-07-25T08:00+08:00[Asia/Shanghai]",
            CycleCalculator.nextOccurrence(now, 8 * 60, zone).toString(),
        )
    }

    @Test
    fun `invalid minute is rejected and cycle id uses local date`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val scheduled = ZonedDateTime.of(2026, 7, 24, 23, 30, 0, 0, zone)

        assertThrows(IllegalArgumentException::class.java) {
            CycleCalculator.nextOccurrence(scheduled, 1440, zone)
        }
        assertEquals("2026-07-24", CycleCalculator.cycleId(scheduled))
    }
}
