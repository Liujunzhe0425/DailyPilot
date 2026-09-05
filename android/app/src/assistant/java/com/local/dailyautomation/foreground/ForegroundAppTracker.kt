package com.local.dailyautomation.foreground

import android.content.Context
import android.content.Intent
import org.autojs.autojs.core.accessibility.AccessibilityService

class ForegroundAppTracker(private val context: Context) {
    @Volatile private var latestPackage: String? = null
    private var originalPackage: String? = null

    fun start(onChanged: (String) -> Unit = {}) = AccessibilityService.setForegroundPackageListener { packageName ->
        if (packageName != context.packageName) {
            latestPackage = packageName
            onChanged(packageName)
        }
    }

    fun captureOriginalPackage(): String? = (
        AccessibilityService.instance?.rootInActiveWindow?.packageName?.toString()?.takeIf { it != context.packageName }
            ?: latestPackage
        ).also { originalPackage = it }

    fun restoreOriginalOrHome(userSwitchedDuringPause: Boolean = false) {
        if (userSwitchedDuringPause) return
        val launch = originalPackage?.let(context.packageManager::getLaunchIntentForPackage)
        if (launch != null) context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun stop() = AccessibilityService.setForegroundPackageListener(null)
}
