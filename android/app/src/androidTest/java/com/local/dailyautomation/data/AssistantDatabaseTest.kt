package com.local.dailyautomation.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AssistantDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: AssistantDatabase
    private lateinit var repository: AssistantRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AssistantDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AssistantRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun retentionDeletesOnlyExpiredRunsAndScreenshots(): Unit = runBlocking {
        val now = 1_800_000_000_000L
        val oldRun = run("old", now - TimeUnit.DAYS.toMillis(31))
        val recentRun = run("recent", now - TimeUnit.DAYS.toMillis(29))
        val oldShotRun = run("old-shot-run", now - 100)
        val recentShotRun = run("recent-shot-run", now - 50)
        for (run in listOf(oldRun, recentRun, oldShotRun, recentShotRun)) {
            repository.upsertRun(run)
        }

        val oldFile = repository.newFailureScreenshotFile(oldShotRun.runId, "weihuda-sign").apply { writeBytes(byteArrayOf(1)) }
        val recentFile = repository.newFailureScreenshotFile(recentShotRun.runId, "xianyu-polish").apply { writeBytes(byteArrayOf(2)) }
        repository.recordFailureScreenshot(FailureScreenshotEntity(oldFile.path, oldShotRun.runId, "weihuda-sign", now - TimeUnit.DAYS.toMillis(8)))
        repository.recordFailureScreenshot(FailureScreenshotEntity(recentFile.path, recentShotRun.runId, "xianyu-polish", now - TimeUnit.DAYS.toMillis(6)))

        repository.deleteExpired(now)

        assertEquals(listOf("recent-shot-run", "old-shot-run", "recent"), database.runDao().getAll().map { it.runId })
        assertEquals(listOf(recentFile.path), database.failureScreenshotDao().getAll().map { it.path })
        assertFalse(oldFile.exists())
        assertTrue(recentFile.exists())
        assertTrue(recentFile.canonicalPath.startsWith(context.filesDir.canonicalPath))
        recentFile.delete()
        Unit
    }

    private fun run(id: String, startedAt: Long) = RunEntity(
        runId = id,
        cycleId = null,
        trigger = "TEST",
        state = "COMPLETED",
        startedAt = startedAt,
        heartbeatAt = startedAt,
        finishedAt = startedAt,
        originalPackage = null,
    )
}
