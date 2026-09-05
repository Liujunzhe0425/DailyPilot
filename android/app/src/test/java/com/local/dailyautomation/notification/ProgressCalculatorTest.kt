package com.local.dailyautomation.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCalculatorTest {
    @Test fun progressIsMonotonicAndTerminalIsExact() {
        assertEquals(0, ProgressCalculator.calculate(3, 0, 0.0))
        val first = ProgressCalculator.calculate(3, 1, 0.0)
        assertEquals(33, first)
        assertEquals(first, ProgressCalculator.calculate(3, 0, 0.1, first))
        assertEquals(100, ProgressCalculator.calculate(3, 3, 1.0, terminal = true))
    }
}
