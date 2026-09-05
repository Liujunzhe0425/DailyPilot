package com.local.dailyautomation.runtime

import android.content.Context
import android.content.Intent
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.domain.ModuleOutcome
import com.local.dailyautomation.domain.TriggerKind
import com.local.dailyautomation.overlay.RunControlBus
import com.local.dailyautomation.overlay.RunControlsService
import com.local.dailyautomation.foreground.ForegroundAppTracker
import com.local.dailyautomation.notification.ProgressCalculator
import com.local.dailyautomation.notification.RunNotificationManager
import com.local.dailyautomation.permissions.PermissionNavigator
import com.local.dailyautomation.ui.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutomationRuntime {
    private var coordinator: BatchCoordinator? = null
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun coordinator(context: Context): BatchCoordinator = synchronized(this) {
        coordinator ?: create(context.applicationContext).also { coordinator = it }
    }

    private fun create(context: Context): BatchCoordinator {
        val bridge = AutoJsEngineBridge(context)
        val repository = AssistantRepository(context)
        val runner = RuntimeModuleRunner { runId, moduleId, readOnly ->
            val deadline = System.currentTimeMillis() + PHASE_TIMEOUT_MILLIS
            val probe = bridge.execute(ModuleCommand(runId, moduleId, ModulePhase.PROBE, deadline, true))
            val result = when (probe.state) {
                "COMPLETED_TODAY" -> RuntimeModuleResult(ModuleOutcome.ALREADY_COMPLETED, probe.message)
                "MANUAL_VERIFICATION_REQUIRED" -> RuntimeModuleResult(ModuleOutcome.MANUAL_VERIFICATION_REQUIRED, probe.message)
                "NOT_COMPLETED" -> if (readOnly) RuntimeModuleResult(ModuleOutcome.NOT_COMPLETED, probe.message) else {
                    val execute = bridge.execute(ModuleCommand(runId, moduleId, ModulePhase.EXECUTE, System.currentTimeMillis() + PHASE_TIMEOUT_MILLIS, false))
                    if (execute.state == "MANUAL_VERIFICATION_REQUIRED") RuntimeModuleResult(ModuleOutcome.MANUAL_VERIFICATION_REQUIRED, execute.message)
                    else {
                        val verify = bridge.execute(ModuleCommand(runId, moduleId, ModulePhase.VERIFY, System.currentTimeMillis() + PHASE_TIMEOUT_MILLIS, true))
                        if (verify.state in setOf("COMPLETED_TODAY", "SUCCEEDED")) RuntimeModuleResult(ModuleOutcome.SUCCEEDED, verify.message)
                        else RuntimeModuleResult(ModuleOutcome.FAILED, verify.message, verify.retryable)
                    }
                }
                else -> RuntimeModuleResult(ModuleOutcome.FAILED, probe.message, probe.retryable)
            }
            result.also {
                if (!readOnly && it.outcome in setOf(ModuleOutcome.FAILED, ModuleOutcome.MANUAL_VERIFICATION_REQUIRED)) {
                    FailureEvidenceCapture.capture(repository, runId, moduleId)
                }
            }
        }
        return BatchCoordinator(repository, runner, CoroutineScope(SupervisorJob() + Dispatchers.IO)).also { batch ->
            RunControlBus.pause = { batch.pause() }
            RunControlBus.resume = { batch.requestResume() }
            RunControlBus.stop = { batch.stopCurrent() }
        }
    }

    suspend fun start(context: Context, trigger: TriggerKind, moduleIds: List<String>, cycleId: String? = null): String {
        val missingPermission = blockingPermission(context)
        check(missingPermission == null) { missingPermission ?: "自动化运行权限不完整" }
        val batch = coordinator(context)
        val tracker = ForegroundAppTracker(context)
        tracker.captureOriginalPackage()
        var userSwitched = false
        if (trigger != TriggerKind.REFRESH_ONLY) {
            tracker.start { packageName ->
                if (packageName !in TARGET_PACKAGES) {
                    monitorScope.launch {
                        delay(750)
                        val activePackage = org.autojs.autojs.core.accessibility.AccessibilityService.instance
                            ?.rootInActiveWindow?.packageName?.toString()
                        if (activePackage == packageName && activePackage !in TARGET_PACKAGES) {
                            userSwitched = true
                            batch.pause("USER_APP_SWITCH")
                        }
                    }
                }
            }
        }
        val runId = batch.start(trigger, moduleIds, cycleId)
        val showRunControls = trigger != TriggerKind.REFRESH_ONLY
        if (showRunControls) ContextCompat.startForegroundService(context, Intent(context, RunControlsService::class.java))
        monitorScope.launch {
            val repository = AssistantRepository(context)
            val notifications = RunNotificationManager(context)
            var previous = 0
            while (true) {
                val run = repository.allRuns().firstOrNull { it.runId == runId } ?: break
                val results = repository.moduleRuns(runId)
                val completed = results.count { it.finishedAt != null }
                previous = ProgressCalculator.calculate(moduleIds.size, completed, 0.0, previous, run.finishedAt != null && run.state == "COMPLETED")
                notifications.progress(previous, "已完成 $completed/${moduleIds.size} 个模块")
                if (run.finishedAt != null) {
                    if (showRunControls) context.stopService(Intent(context, RunControlsService::class.java))
                    tracker.stop()
                    if (run.state != "COMPLETED") notifications.failure("本次运行：${run.state}")
                    if (trigger in setOf(TriggerKind.REFRESH_ONLY, TriggerKind.RUN_ALL_MANUAL, TriggerKind.RUN_ONE_MANUAL)) {
                        restoreDashboard(context)
                    } else if (run.state == "COMPLETED" && !userSwitched) tracker.restoreOriginalOrHome(false)
                    break
                }
                delay(300)
            }
        }
        return runId
    }

    private const val PHASE_TIMEOUT_MILLIS = 45_000L
    private val TARGET_PACKAGES = setOf(
        "com.android.systemui", "com.miui.home", "com.miui.personalassistant", "com.tencent.mm", "com.taobao.idlefish",
    )

    private suspend fun restoreDashboard(context: Context) {
        val accessibility = org.autojs.autojs.core.accessibility.AccessibilityService.instance
        suspend fun dashboardReached(): Boolean {
            repeat(5) {
                delay(120)
                if (accessibility?.rootInActiveWindow?.packageName?.toString() == context.packageName) return true
            }
            return false
        }
        if (accessibility != null && accessibility.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)) {
            repeat(8) {
                delay(250)
                val title = accessibility.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText("每日领航")
                    ?.firstOrNull { it.text?.toString() == "每日领航" }
                var candidate = title
                repeat(6) {
                    if (candidate?.isClickable == true && candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                        // Xiaomi reports ACTION_CLICK success as soon as the
                        // gesture is queued. During the recents animation that
                        // event may be dropped, so only finish after the target
                        // package actually becomes foreground.
                        if (dashboardReached()) return
                    }
                    if (candidate?.isClickable == true) {
                        val bounds = Rect().also { candidate?.getBoundsInScreen(it) }
                        if (!bounds.isEmpty) {
                            val path = Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
                            val gesture = GestureDescription.Builder()
                                .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
                                .build()
                            if (accessibility.dispatchGesture(gesture, null, null) && dashboardReached()) return
                        }
                    }
                    candidate = candidate?.parent
                }
            }
        }
        context.startActivity(
            Intent(context, DashboardActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    fun blockingPermission(context: Context): String? = when {
        !Settings.canDrawOverlays(context) -> "请先开启悬浮窗权限"
        !PermissionNavigator.isAccessibilityEnabled(context) -> "请先开启无障碍服务"
        else -> null
    }
}
