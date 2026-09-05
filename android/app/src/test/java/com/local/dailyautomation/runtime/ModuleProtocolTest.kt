package com.local.dailyautomation.runtime

import com.local.dailyautomation.domain.ProbeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ModuleProtocolTest {
    @Test fun `command round trips`() {
        val command = ModuleCommand("run", "weihuda-sign", ModulePhase.PROBE, 1234L, true)
        assertEquals(command, ModuleProtocol.decodeCommand(ModuleProtocol.encodeCommand(command)))
    }

    @Test fun `probe response preserves evidence`() {
        val json = ModuleProtocol.RESULT_PREFIX + """{"moduleId":"weihuda-sign","phase":"PROBE","state":"COMPLETED_TODAY","message":"text:已签","retryable":false}"""
        val response = ModuleProtocol.decodeResponse(json)
        assertEquals(ProbeState.COMPLETED_TODAY, response.probeState)
        assertEquals("text:已签", response.message)
        assertFalse(response.retryable)
    }

    @Test fun `missing result prefix is rejected clearly`() {
        assertThrows(IllegalArgumentException::class.java) { ModuleProtocol.decodeResponse("null") }
    }
}
