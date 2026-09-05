package com.local.dailyautomation.unlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UnlockEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT || intent.action == Intent.ACTION_USER_UNLOCKED) {
            UnlockEventHub.install(context)
            UnlockEventHub.candidate(context)
        }
    }
}
