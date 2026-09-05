package com.local.dailyautomation.unlock

import android.app.KeyguardManager
import android.content.Context
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.data.CycleEntity
import com.local.dailyautomation.decision.Decision
import com.local.dailyautomation.decision.DecisionOverlayService
import com.local.dailyautomation.decision.DecisionPolicy
import com.local.dailyautomation.domain.BatchState

class UnlockGate(
    context: Context,
    private val repository: AssistantRepository = AssistantRepository(context),
    private val completionChecker: CompletionChecker = CompletionChecker { false },
) {
    private val appContext = context.applicationContext
    private val keyguardManager = appContext.getSystemService(KeyguardManager::class.java)

    suspend fun onScheduledCycle(cycleId: String) {
        UnlockEventHub.install(appContext)
        repository.getCycle(cycleId)?.let { evaluate(it) }
    }

    suspend fun resumeWaitingCycles() {
        repository.waitingForUnlock().forEach { evaluate(it) }
    }

    private suspend fun evaluate(cycle: CycleEntity) {
        val allCompleted = completionChecker.allEnabledCompleted(cycle.cycleId)
        val result = DecisionPolicy.evaluate(
            locked = keyguardManager.isKeyguardLocked,
            decisionShownAt = cycle.decisionShownAt,
            allCompleted = allCompleted,
        )
        when {
            result.terminalDecision == Decision.ALL_COMPLETED -> repository.markDecision(
                cycle.cycleId,
                Decision.ALL_COMPLETED.name,
                BatchState.COMPLETED.name,
            )
            result.show && repository.claimDecisionOverlay(cycle.cycleId) -> {
                DecisionOverlayService.show(appContext, cycle.cycleId)
            }
        }
    }

    fun interface CompletionChecker {
        suspend fun allEnabledCompleted(cycleId: String): Boolean
    }
}
