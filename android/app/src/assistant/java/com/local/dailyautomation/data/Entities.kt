package com.local.dailyautomation.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val automationEnabled: Boolean,
    val runMinuteOfDay: Int,
    val failureScreenshotsEnabled: Boolean,
) {
    init {
        require(id == SINGLETON_ID)
        require(runMinuteOfDay in 0..1439)
    }

    companion object {
        const val SINGLETON_ID = 1
    }
}

@Entity(tableName = "module_config")
data class ModuleConfigEntity(
    @PrimaryKey val moduleId: String,
    val enabled: Boolean,
    val sortOrder: Int,
)

@Entity(tableName = "cycle")
data class CycleEntity(
    @PrimaryKey val cycleId: String,
    val scheduledAt: Long,
    val decisionShownAt: Long?,
    val decision: String?,
    val state: String,
)

@Entity(tableName = "run", indices = [Index("cycleId"), Index("startedAt")])
data class RunEntity(
    @PrimaryKey val runId: String,
    val cycleId: String?,
    val trigger: String,
    val state: String,
    val startedAt: Long,
    val heartbeatAt: Long,
    val finishedAt: Long?,
    val originalPackage: String?,
)

@Entity(
    tableName = "module_run",
    primaryKeys = ["runId", "moduleId"],
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId")],
)
data class ModuleRunEntity(
    val runId: String,
    val moduleId: String,
    val outcome: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val retryCount: Int,
    val message: String?,
)

@Entity(
    tableName = "failure_screenshot",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("runId"), Index("createdAt")],
)
data class FailureScreenshotEntity(
    @PrimaryKey val path: String,
    val runId: String,
    val moduleId: String,
    val createdAt: Long,
)
