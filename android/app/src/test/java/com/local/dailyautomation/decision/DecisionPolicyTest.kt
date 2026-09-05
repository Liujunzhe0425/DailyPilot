package com.local.dailyautomation.decision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionPolicyTest {
    @Test fun `unlocked cycle shows once`() {
        val first = DecisionPolicy.evaluate(locked = false, decisionShownAt = null, allCompleted = false)
        assertTrue(first.show)
        assertNull(first.terminalDecision)

        val second = DecisionPolicy.evaluate(locked = false, decisionShownAt = 1L, allCompleted = false)
        assertFalse(second.show)
        assertNull(second.terminalDecision)
    }

    @Test fun `locked cycle waits`() {
        assertFalse(DecisionPolicy.evaluate(true, null, false).show)
    }

    @Test fun `second unlock in same cycle never shows again`() {
        assertFalse(DecisionPolicy.evaluate(false, 123L, false).show)
    }

    @Test fun `timeout is run later`() {
        assertEquals(Decision.RUN_LATER, DecisionPolicy.timeoutDecision())
    }

    @Test fun `all completed skips overlay`() {
        val result = DecisionPolicy.evaluate(false, null, true)
        assertFalse(result.show)
        assertEquals(Decision.ALL_COMPLETED, result.terminalDecision)
    }
}
