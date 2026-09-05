package com.local.dailyautomation.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BatchReducerTest {
    @Test fun `legal lifecycle including pause and resume`() {
        var value = BatchSnapshot(BatchState.SCHEDULED)
        value = BatchReducer.reduce(value, BatchEvent.Locked)
        value = BatchReducer.reduce(value, BatchEvent.Unlocked)
        value = BatchReducer.reduce(value, BatchEvent.RunApproved)
        value = BatchReducer.reduce(value, BatchEvent.Pause(1_000))
        value = BatchReducer.reduce(value, BatchEvent.Resume(2_000))
        value = BatchReducer.reduce(value, BatchEvent.Finished)
        assertEquals(BatchState.COMPLETED, value.state)
    }

    @Test fun `pause expires at exactly three minutes`() {
        val paused = BatchSnapshot(BatchState.PAUSED, 1_000)
        assertEquals(BatchState.PAUSED, BatchReducer.reduce(paused, BatchEvent.Tick(180_999)).state)
        assertEquals(BatchState.STOPPED, BatchReducer.reduce(paused, BatchEvent.Tick(181_000)).state)
    }

    @Test fun `illegal transition is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            BatchReducer.reduce(BatchSnapshot(BatchState.SCHEDULED), BatchEvent.Finished)
        }
    }
}
