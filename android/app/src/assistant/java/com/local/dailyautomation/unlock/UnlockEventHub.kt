package com.local.dailyautomation.unlock

import android.app.KeyguardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.autojs.autojs.core.accessibility.AccessibilityService

object UnlockEventHub {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableUnlocked = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unlocked = mutableUnlocked.asSharedFlow()
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            AccessibilityService.setWindowStateCandidateListener {
                candidate(appContext)
            }
            installed = true
        }
    }

    fun candidate(context: Context) {
        val appContext = context.applicationContext
        if (appContext.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        mutableUnlocked.tryEmit(Unit)
        scope.launch { UnlockGate(appContext).resumeWaitingCycles() }
    }
}
