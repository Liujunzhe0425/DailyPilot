package com.local.dailyautomation.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        AssistantRepository(applicationContext).deleteExpired(System.currentTimeMillis())
        Result.success()
    }.getOrElse { Result.retry() }
}
