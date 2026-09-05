package com.local.dailyautomation.e2e

import com.local.dailyautomation.decision.Decision
import com.local.dailyautomation.decision.DecisionPolicy
import com.local.dailyautomation.domain.BatchEvent
import com.local.dailyautomation.domain.BatchReducer
import com.local.dailyautomation.domain.BatchSnapshot
import com.local.dailyautomation.domain.BatchState
import com.local.dailyautomation.runtime.ModuleFixtureRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchFlowTest {
    @Test fun lockedThenUnlockedPromptsOnlyOnce() {
        assertFalse(DecisionPolicy.evaluate(true, null, false).show)
        assertTrue(DecisionPolicy.evaluate(false, null, false).show)
        assertFalse(DecisionPolicy.evaluate(false, 1L, false).show)
        assertEquals(Decision.RUN_LATER, DecisionPolicy.timeoutDecision())
    }

    @Test fun pauseResumeStopAndTimeoutAreTerminalSafe() {
        val running = BatchSnapshot(BatchState.RUNNING)
        val paused = BatchReducer.reduce(running, BatchEvent.Pause(1000))
        assertEquals(BatchState.RUNNING, BatchReducer.reduce(paused, BatchEvent.Resume(2000)).state)
        assertEquals(BatchState.STOPPED, BatchReducer.reduce(paused, BatchEvent.Tick(181000)).state)
        assertEquals(BatchState.STOPPED, BatchReducer.reduce(running, BatchEvent.StopConfirmed).state)
    }

    @Test fun forbiddenBusinessStatesNeverBecomeRunnable() {
        assertEquals("UNKNOWN", ModuleFixtureRules.coupon(listOf("购物提现券 去完成 支付")))
        assertEquals("FORBIDDEN_UI", ModuleFixtureRules.xianyu(listOf("超级擦亮", "充值")))
    }
}
