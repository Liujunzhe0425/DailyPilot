package com.local.dailyautomation.runtime

import com.local.dailyautomation.domain.ProbeState
import com.google.gson.Gson

enum class ModulePhase { PROBE, EXECUTE, VERIFY, CLEANUP }

data class ModuleCommand(
    val runId: String,
    val moduleId: String,
    val phase: ModulePhase,
    val deadlineEpochMs: Long,
    val readOnly: Boolean,
    val responsePath: String? = null,
)

data class ModuleResponse(
    val moduleId: String,
    val phase: ModulePhase,
    val state: String,
    val message: String? = null,
    val retryable: Boolean = false,
    val evidencePath: String? = null,
) {
    val probeState: ProbeState?
        get() = ProbeState.entries.firstOrNull { it.name == state }
}

object ModuleProtocol {
    const val RESULT_PREFIX = "ASSISTANT_RESULT:"
    private val gson = Gson()

    fun encodeCommand(value: ModuleCommand): String = gson.toJson(value)

    fun decodeCommand(json: String): ModuleCommand = gson.fromJson(json, ModuleCommand::class.java)

    fun decodeResponse(value: String): ModuleResponse {
        require(value.startsWith(RESULT_PREFIX)) { "Script result is missing $RESULT_PREFIX" }
        val json = value.removePrefix(RESULT_PREFIX)
        return requireNotNull(gson.fromJson(json, ModuleResponse::class.java)) { "Script response is null" }
    }
}
