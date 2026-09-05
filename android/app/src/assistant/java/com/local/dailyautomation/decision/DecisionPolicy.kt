package com.local.dailyautomation.decision

enum class Decision {
    RUN_NOW,
    RUN_LATER,
    ALL_COMPLETED,
}

data class DecisionPolicyResult(
    val show: Boolean,
    val terminalDecision: Decision?,
)

object DecisionPolicy {
    fun evaluate(
        locked: Boolean,
        decisionShownAt: Long?,
        allCompleted: Boolean,
    ): DecisionPolicyResult = when {
        allCompleted -> DecisionPolicyResult(false, Decision.ALL_COMPLETED)
        decisionShownAt != null -> DecisionPolicyResult(false, null)
        locked -> DecisionPolicyResult(false, null)
        else -> DecisionPolicyResult(true, null)
    }

    fun timeoutDecision(): Decision = Decision.RUN_LATER
}
