package com.local.dailyautomation.runtime

import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.domain.BatchReducer

enum class RecoveryAction { NONE, RESTART_CURRENT_MODULE_AT_PROBE, END_INTERRUPTED }

class RecoveryCoordinator(private val repository: AssistantRepository) {
    suspend fun recoverInterruptedRun(now: Long): RecoveryAction {
        val run = repository.latestActiveRun() ?: return RecoveryAction.NONE
        return if (now - run.heartbeatAt < BatchReducer.PAUSE_TIMEOUT_MILLIS) {
            RecoveryAction.RESTART_CURRENT_MODULE_AT_PROBE
        } else {
            repository.finishRun(run.runId, "INTERRUPTED", now)
            RecoveryAction.END_INTERRUPTED
        }
    }
}
