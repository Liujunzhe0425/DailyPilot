package com.local.dailyautomation.overlay

import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RunControlsServiceTest {
    @Test
    fun missingOverlayPermissionStopsWithoutForegroundServiceTimeoutCrash() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        ContextCompat.startForegroundService(context, Intent(context, RunControlsService::class.java))
        // Android raises ForegroundServiceDidNotStartInTimeException after this window
        // when any service path forgets startForeground(). Staying alive proves the regression.
        SystemClock.sleep(6_000L)
    }
}
