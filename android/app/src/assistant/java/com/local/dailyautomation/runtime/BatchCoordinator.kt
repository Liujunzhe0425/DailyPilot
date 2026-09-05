package com.local.dailyautomation.runtime

import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.data.ModuleRunEntity
import com.local.dailyautomation.data.RunEntity
import com.local.dailyautomation.domain.BatchReducer
import com.local.dailyautomation.domain.BatchState
import com.local.dailyautomation.domain.ModuleOutcome
import com.local.dailyautomation.domain.TriggerKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class RuntimeModuleResult(
    val outcome: ModuleOutcome,
    val message: String? = null,
    val retryable: Boolean = false,
)

fun interface RuntimeModuleRunner {
    suspend fun run(runId: String, moduleId: String, readOnly: Boolean): RuntimeModuleResult
}

internal suspend fun RuntimeModuleRunner.runSafely(runId: String, moduleId: String, readOnly: Boolean): RuntimeModuleResult = try {
    run(runId, moduleId, readOnly)
} catch (error: TimeoutCancellationException) {
    RuntimeModuleResult(ModuleOutcome.FAILED, "TIMEOUT:${error.message ?: moduleId}", true)
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    RuntimeModuleResult(ModuleOutcome.FAILED, "${error.javaClass.simpleName}:${error.message ?: "module-error"}", false)
}

class BatchCoordinator(
    private val repository: AssistantRepository,
    private val runner: RuntimeModuleRunner,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    @Volatile private var job: Job? = null
    @Volatile private var pausedAt: Long? = null
    @Volatile private var stopRequested = false

    suspend fun start(trigger: TriggerKind, moduleIds: List<String>, cycleId: String? = null): String = mutex.withLock {
        check(job?.isActive != true) { "A batch is already running" }
        require(moduleIds.isNotEmpty()) { "At least one module is required" }
        val runId = UUID.randomUUID().toString()
        val now = clock()
        repository.upsertRun(RunEntity(runId, cycleId, trigger.name, BatchState.RUNNING.name, now, now, null, null))
        stopRequested = false
        pausedAt = null
        job = scope.launch { execute(runId, moduleIds, trigger == TriggerKind.REFRESH_ONLY) }
        runId
    }

    fun pause(reason: String = "USER") {
        if (job?.isActive == true && pausedAt == null) pausedAt = clock()
    }

    fun requestResume(): Boolean {
        val started = pausedAt ?: return false
        if (clock() - started >= BatchReducer.PAUSE_TIMEOUT_MILLIS) {
            stopRequested = true
            return false
        }
        pausedAt = null
        return true
    }

    fun stopCurrent() {
        stopRequested = true
        job?.cancel(CancellationException("User stopped current batch"))
    }

    private suspend fun execute(runId: String, moduleIds: List<String>, readOnly: Boolean) {
        var terminal = BatchState.COMPLETED
        var activeModule: Pair<String, Long>? = null
        var hadFailure = false
        try {
            for (moduleId in moduleIds) {
                awaitRunnable(runId)
                if (stopRequested) break
                val started = clock()
                activeModule = moduleId to started
                repository.recordModuleResult(ModuleRunEntity(runId, moduleId, ModuleOutcome.NOT_PROBED.name, started, null, 0, null))
                var result = runner.runSafely(runId, moduleId, readOnly)
                var retries = 0
                if (result.retryable) {
                    retries = 1
                    awaitRunnable(runId)
                    if (!stopRequested) result = runner.runSafely(runId, moduleId, readOnly)
                }
                repository.recordModuleResult(
                    ModuleRunEntity(runId, moduleId, result.outcome.name, started, clock(), retries, result.message),
                )
                repository.heartbeat(runId, clock())
                hadFailure = hadFailure || result.outcome in setOf(ModuleOutcome.FAILED, ModuleOutcome.MANUAL_VERIFICATION_REQUIRED)
                activeModule = null
            }
            if (stopRequested) terminal = if (pausedAt != null) BatchState.EXPIRED else BatchState.STOPPED
            else if (hadFailure) terminal = BatchState.FAILED
        } catch (_: CancellationException) {
            terminal = BatchState.STOPPED
            activeModule?.let { (moduleId, started) ->
                repository.recordModuleResult(
                    ModuleRunEntity(runId, moduleId, ModuleOutcome.USER_STOPPED.name, started, clock(), 0, "user-stopped"),
                )
            }
        } finally {
            repository.finishRun(runId, terminal.name, clock())
            pausedAt = null
            stopRequested = false
        }
    }

    private suspend fun awaitRunnable(runId: String) {
        while (pausedAt != null) {
            val elapsed = clock() - requireNotNull(pausedAt)
            if (elapsed >= BatchReducer.PAUSE_TIMEOUT_MILLIS) {
                stopRequested = true
                return
            }
            repository.heartbeat(runId, clock())
            delay(250)
        }
    }
}
