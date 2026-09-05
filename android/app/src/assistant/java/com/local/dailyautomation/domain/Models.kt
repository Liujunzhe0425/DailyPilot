package com.local.dailyautomation.domain

enum class ProbeState {
    COMPLETED_TODAY,
    NOT_COMPLETED,
    MANUAL_VERIFICATION_REQUIRED,
    UNKNOWN,
}

enum class ModuleOutcome {
    ALREADY_COMPLETED,
    NOT_COMPLETED,
    SUCCEEDED,
    FAILED,
    MANUAL_VERIFICATION_REQUIRED,
    USER_STOPPED,
    PAUSE_TIMEOUT,
    NOT_PROBED,
}

enum class BatchState {
    SCHEDULED,
    WAITING_UNLOCK,
    AWAITING_DECISION,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    STOPPED,
    EXPIRED,
    MANUAL_ONLY,
}

enum class TriggerKind {
    SCHEDULED,
    RUN_ALL_MANUAL,
    RUN_ONE_MANUAL,
    REFRESH_ONLY,
    RECOVERY,
}

data class ModuleDescriptor(
    val id: String,
    val displayName: String,
    val targetPackage: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val script: String,
)
