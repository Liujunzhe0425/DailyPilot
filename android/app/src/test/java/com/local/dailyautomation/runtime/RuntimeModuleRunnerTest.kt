package com.local.dailyautomation.runtime

import com.local.dailyautomation.domain.ModuleOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeModuleRunnerTest {
    @Test fun `timeout becomes retryable module failure`() = runBlocking {
        val runner = RuntimeModuleRunner { _, _, _ ->
            withTimeout(1) {
                delay(50)
                RuntimeModuleResult(ModuleOutcome.SUCCEEDED)
            }
        }
        val result = runner.runSafely("run", "weihuda-sign", false)
        assertEquals(ModuleOutcome.FAILED, result.outcome)
        assertTrue(result.retryable)
        assertTrue(result.message.orEmpty().startsWith("TIMEOUT:"))
    }

    @Test fun `ordinary exception becomes non-retryable module failure`() = runBlocking {
        val runner = RuntimeModuleRunner { _, _, _ -> error("selector failed") }
        val result = runner.runSafely("run", "withdrawal-coupon", false)
        assertEquals(ModuleOutcome.FAILED, result.outcome)
        assertTrue(result.message.orEmpty().contains("selector failed"))
    }

    @Test fun `explicit cancellation still stops the batch`() {
        val runner = RuntimeModuleRunner { _, _, _ -> throw CancellationException("user stopped") }
        assertThrows(CancellationException::class.java) {
            runBlocking { runner.runSafely("run", "weihuda-sign", false) }
        }
    }
}
