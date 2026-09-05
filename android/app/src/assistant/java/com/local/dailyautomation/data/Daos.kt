package com.local.dailyautomation.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config WHERE id = 1")
    fun observe(): Flow<AppConfigEntity?>

    @Query("SELECT * FROM app_config WHERE id = 1")
    suspend fun get(): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: AppConfigEntity)
}

@Dao
interface ModuleConfigDao {
    @Query("SELECT * FROM module_config ORDER BY sortOrder")
    fun observeAll(): Flow<List<ModuleConfigEntity>>

    @Query("SELECT * FROM module_config ORDER BY sortOrder")
    suspend fun getAll(): List<ModuleConfigEntity>

    @Query("UPDATE module_config SET enabled = :enabled WHERE moduleId = :moduleId")
    suspend fun setEnabled(moduleId: String, enabled: Boolean): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(modules: List<ModuleConfigEntity>)
}

@Dao
interface CycleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(cycle: CycleEntity): Long

    @Query("SELECT * FROM cycle WHERE cycleId = :cycleId")
    suspend fun get(cycleId: String): CycleEntity?

    @Query("SELECT * FROM cycle WHERE state = 'WAITING_UNLOCK' AND decisionShownAt IS NULL AND decision IS NULL")
    suspend fun waitingForUnlock(): List<CycleEntity>

    @Query("UPDATE cycle SET decisionShownAt = :shownAt, state = 'AWAITING_DECISION' WHERE cycleId = :cycleId AND decisionShownAt IS NULL AND decision IS NULL")
    suspend fun claimDecisionOverlay(cycleId: String, shownAt: Long): Int

    @Query("UPDATE cycle SET decision = :decision, state = :state WHERE cycleId = :cycleId")
    suspend fun markDecision(cycleId: String, decision: String, state: String): Int
}

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: RunEntity)

    @Query("SELECT * FROM run WHERE runId = :runId")
    suspend fun get(runId: String): RunEntity?

    @Query("UPDATE run SET heartbeatAt = :at WHERE runId = :runId")
    suspend fun heartbeat(runId: String, at: Long): Int

    @Query("UPDATE run SET state = :state, finishedAt = :finishedAt, heartbeatAt = :finishedAt WHERE runId = :runId")
    suspend fun finish(runId: String, state: String, finishedAt: Long): Int

    @Query("SELECT * FROM run WHERE finishedAt IS NULL AND state = 'RUNNING' ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestActive(): RunEntity?

    @Query("DELETE FROM run WHERE startedAt < :cutoff")
    suspend fun deleteStartedBefore(cutoff: Long): Int

    @Query("SELECT * FROM run ORDER BY startedAt DESC")
    suspend fun getAll(): List<RunEntity>

    @Query("SELECT * FROM run WHERE startedAt >= :since ORDER BY startedAt DESC")
    suspend fun startedSince(since: Long): List<RunEntity>
}

@Dao
interface ModuleRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: ModuleRunEntity)

    @Query("SELECT * FROM module_run WHERE runId = :runId ORDER BY startedAt")
    suspend fun forRun(runId: String): List<ModuleRunEntity>
}

@Dao
interface FailureScreenshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(screenshot: FailureScreenshotEntity)

    @Query("SELECT * FROM failure_screenshot WHERE createdAt < :cutoff")
    suspend fun olderThan(cutoff: Long): List<FailureScreenshotEntity>

    @Query("DELETE FROM failure_screenshot WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT * FROM failure_screenshot ORDER BY createdAt DESC")
    suspend fun getAll(): List<FailureScreenshotEntity>
}
