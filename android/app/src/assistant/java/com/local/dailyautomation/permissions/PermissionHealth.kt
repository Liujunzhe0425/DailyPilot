package com.local.dailyautomation.permissions

data class PermissionSnapshot(
    val accessibility: Boolean,
    val overlay: Boolean,
    val exactAlarm: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val xiaomiAutostart: Boolean,
)

enum class PermissionKind {
    ACCESSIBILITY,
    OVERLAY,
    EXACT_ALARM,
    NOTIFICATIONS,
    BATTERY_UNRESTRICTED,
    XIAOMI_AUTOSTART,
}

data class PermissionHealthResult(
    val automaticReady: Boolean,
    val manualReady: Boolean,
    val missing: Set<PermissionKind>,
)

object PermissionHealth {
    fun evaluate(snapshot: PermissionSnapshot): PermissionHealthResult {
        val missing = buildSet {
            if (!snapshot.accessibility) add(PermissionKind.ACCESSIBILITY)
            if (!snapshot.overlay) add(PermissionKind.OVERLAY)
            if (!snapshot.exactAlarm) add(PermissionKind.EXACT_ALARM)
            if (!snapshot.notifications) add(PermissionKind.NOTIFICATIONS)
            if (!snapshot.batteryUnrestricted) add(PermissionKind.BATTERY_UNRESTRICTED)
            if (!snapshot.xiaomiAutostart) add(PermissionKind.XIAOMI_AUTOSTART)
        }
        val manualReady = snapshot.accessibility && snapshot.overlay
        return PermissionHealthResult(
            automaticReady = missing.isEmpty(),
            manualReady = manualReady,
            missing = missing,
        )
    }
}
