package com.local.dailyautomation.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHealthTest {
    @Test
    fun `missing exact alarm blocks automatic scheduling but not manual runs`() {
        val result = PermissionHealth.evaluate(PermissionSnapshot(true, true, false, true, true, true))

        assertFalse(result.automaticReady)
        assertTrue(result.manualReady)
        assertEquals(setOf(PermissionKind.EXACT_ALARM), result.missing)
    }

    @Test
    fun `manual run requires accessibility and overlay only`() {
        val result = PermissionHealth.evaluate(PermissionSnapshot(false, true, true, false, false, false))

        assertFalse(result.manualReady)
        assertFalse(result.automaticReady)
        assertTrue(PermissionKind.ACCESSIBILITY in result.missing)
        assertTrue(PermissionKind.NOTIFICATIONS in result.missing)
    }

    @Test
    fun `all required permissions enable automatic runs`() {
        val result = PermissionHealth.evaluate(PermissionSnapshot(true, true, true, true, true, true))

        assertTrue(result.manualReady)
        assertTrue(result.automaticReady)
        assertTrue(result.missing.isEmpty())
    }
}
