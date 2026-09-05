package com.local.dailyautomation.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

class AssistantRepository(
    private val context: Context,
    private val database: AssistantDatabase = AssistantDatabase.get(context),
) {
    private val screenshotDirectory = File(context.filesDir, SCREENSHOT_DIRECTORY).apply { mkdirs() }

    fun observeConfig(): Flow<AppConfigEntity?> = database.appConfigDao().observe()

    suspend fun getConfig(): AppConfigEntity? = database.appConfigDao().get()

    fun observeModules(): Flow<List<ModuleConfigEntity>> = database.moduleConfigDao().observeAll()

    suspend fun getModules(): List<ModuleConfigEntity> = database.moduleConfigDao().getAll()

    suspend fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        check(database.moduleConfigDao().setEnabled(moduleId, enabled) == 1) { "Unknown module: $moduleId" }
    }

    suspend fun saveConfig(config: AppConfigEntity) = database.appConfigDao().upsert(config)

    suspend fun saveModuleOrder(ids: List<String>) = database.withTransaction {
        require(ids.distinct().size == ids.size) { "Module order contains duplicate IDs" }
        val current = database.moduleConfigDao().getAll().associateBy(ModuleConfigEntity::moduleId)
        require(ids.toSet() == current.keys) { "Module order must contain every configured module exactly once" }
        database.moduleConfigDao().upsertAll(ids.mapIndexed { index, id -> current.getValue(id).copy(sortOrder = index) })
    }

    suspend fun seedModules(modules: List<ModuleConfigEntity>) = database.moduleConfigDao().upsertAll(modules)

    suspend fun createCycle(
        cycleId: String,
        scheduledAt: Long = System.currentTimeMillis(),
        state: String = "SCHEDULED",
    ): Boolean = database.cycleDao().insert(
        CycleEntity(cycleId, scheduledAt, null, null, state),
    ) != -1L

    suspend fun getCycle(cycleId: String): CycleEntity? = database.cycleDao().get(cycleId)

    suspend fun waitingForUnlock(): List<CycleEntity> = database.cycleDao().waitingForUnlock()

    suspend fun claimDecisionOverlay(cycleId: String, shownAt: Long = System.currentTimeMillis()): Boolean =
        database.cycleDao().claimDecisionOverlay(cycleId, shownAt) == 1

    suspend fun markDecision(
        cycleId: String,
        decision: String,
        state: String = "RUNNING",
    ) {
        check(database.cycleDao().markDecision(cycleId, decision, state) == 1) {
            "Unknown cycle: $cycleId"
        }
    }

    suspend fun upsertRun(run: RunEntity) = database.runDao().upsert(run)

    suspend fun heartbeat(runId: String, at: Long) {
        check(database.runDao().heartbeat(runId, at) == 1) { "Unknown run: $runId" }
    }

    suspend fun finishRun(runId: String, state: String, finishedAt: Long) {
        check(database.runDao().finish(runId, state, finishedAt) == 1) { "Unknown run: $runId" }
    }

    suspend fun latestActiveRun(): RunEntity? = database.runDao().latestActive()

    suspend fun allRuns(): List<RunEntity> = database.runDao().getAll()

    suspend fun runsStartedSince(since: Long): List<RunEntity> = database.runDao().startedSince(since)

    suspend fun moduleRuns(runId: String): List<ModuleRunEntity> = database.moduleRunDao().forRun(runId)

    suspend fun recordModuleResult(result: ModuleRunEntity) = database.moduleRunDao().upsert(result)

    suspend fun recordFailureScreenshot(screenshot: FailureScreenshotEntity) {
        require(isPrivateScreenshot(File(screenshot.path))) { "Screenshot must stay in app-private storage" }
        database.failureScreenshotDao().upsert(screenshot)
    }

    fun newFailureScreenshotFile(runId: String, moduleId: String): File {
        val safeRun = runId.replace(UNSAFE_FILE_CHARS, "_")
        val safeModule = moduleId.replace(UNSAFE_FILE_CHARS, "_")
        return File(screenshotDirectory, "${safeRun}_${safeModule}_${UUID.randomUUID()}.png")
    }

    suspend fun deleteExpired(now: Long): RetentionResult = database.withTransaction {
        val screenshotCutoff = now - SCREENSHOT_RETENTION_MILLIS
        val expiredScreenshots = database.failureScreenshotDao().olderThan(screenshotCutoff)
        expiredScreenshots.forEach { record ->
            File(record.path).takeIf(::isPrivateScreenshot)?.delete()
        }
        val screenshotsDeleted = database.failureScreenshotDao().deleteOlderThan(screenshotCutoff)
        val runsDeleted = database.runDao().deleteStartedBefore(now - LOG_RETENTION_MILLIS)
        RetentionResult(runsDeleted, screenshotsDeleted)
    }

    private fun isPrivateScreenshot(file: File): Boolean = runCatching {
        file.canonicalPath.startsWith(screenshotDirectory.canonicalPath + File.separator)
    }.getOrDefault(false)

    data class RetentionResult(val runsDeleted: Int, val screenshotsDeleted: Int)

    companion object {
        const val SCREENSHOT_DIRECTORY = "failure-screenshots"
        val LOG_RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(30)
        val SCREENSHOT_RETENTION_MILLIS: Long = TimeUnit.DAYS.toMillis(7)
        private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
