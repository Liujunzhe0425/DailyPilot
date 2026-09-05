package com.local.dailyautomation.runtime

import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.view.Display
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.data.FailureScreenshotEntity
import kotlinx.coroutines.suspendCancellableCoroutine
import org.autojs.autojs.core.accessibility.AccessibilityService
import java.util.concurrent.Executor
import kotlin.coroutines.resume

object FailureEvidenceCapture {
    suspend fun capture(repository: AssistantRepository, runId: String, moduleId: String): String? {
        if (repository.getConfig()?.failureScreenshotsEnabled != true || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val service = AccessibilityService.instance ?: return null
        val file = repository.newFailureScreenshotFile(runId, moduleId)
        val saved = suspendCancellableCoroutine { continuation ->
            service.takeScreenshot(Display.DEFAULT_DISPLAY, Executor(Runnable::run), object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val colorSpace = result.colorSpace ?: ColorSpace.get(ColorSpace.Named.SRGB)
                    val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, colorSpace)
                    val ok = bitmap?.let { value -> file.outputStream().use { value.compress(Bitmap.CompressFormat.PNG, 100, it) } } == true
                    result.hardwareBuffer.close()
                    if (continuation.isActive) continuation.resume(ok)
                }
                override fun onFailure(errorCode: Int) { if (continuation.isActive) continuation.resume(false) }
            })
        }
        if (!saved) { file.delete(); return null }
        repository.recordFailureScreenshot(FailureScreenshotEntity(file.absolutePath, runId, moduleId, System.currentTimeMillis()))
        return file.absolutePath
    }
}
