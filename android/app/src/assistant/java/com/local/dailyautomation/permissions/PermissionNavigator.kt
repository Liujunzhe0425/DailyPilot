package com.local.dailyautomation.permissions

import android.app.Activity
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import org.autojs.autojs.core.accessibility.AccessibilityServiceUsher

class PermissionNavigator(private val activity: Activity) {
    fun open(kind: PermissionKind) {
        val preferred = intentFor(kind)
        runCatching { activity.startActivity(preferred) }
            .recoverCatching { activity.startActivity(applicationDetailsIntent()) }
            .getOrThrow()
    }

    fun intentFor(kind: PermissionKind): Intent = when (kind) {
        PermissionKind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        PermissionKind.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            packageUri(),
        )
        PermissionKind.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri())
        } else {
            applicationDetailsIntent()
        }
        PermissionKind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
        PermissionKind.BATTERY_UNRESTRICTED -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri(),
        )
        PermissionKind.XIAOMI_AUTOSTART -> xiaomiAutostartIntent()
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun snapshot(xiaomiAutostartConfirmed: Boolean): PermissionSnapshot {
        val alarmManager = activity.getSystemService(AlarmManager::class.java)
        val powerManager = activity.getSystemService(PowerManager::class.java)
        return PermissionSnapshot(
            accessibility = isAccessibilityEnabled(activity),
            overlay = Settings.canDrawOverlays(activity),
            exactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
            notifications = NotificationManagerCompat.from(activity).areNotificationsEnabled(),
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(activity.packageName),
            xiaomiAutostart = xiaomiAutostartConfirmed,
        )
    }

    private fun xiaomiAutostartIntent(): Intent {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity",
            ),
        )
        return candidates.asSequence()
            .map { component -> Intent().setComponent(component).putExtra("extra_pkgname", activity.packageName) }
            .firstOrNull { intent -> intent.resolveActivity(activity.packageManager) != null }
            ?: applicationDetailsIntent()
    }

    private fun applicationDetailsIntent() = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        packageUri(),
    )

    private fun packageUri() = Uri.parse("package:${activity.packageName}")

    companion object {
        fun isAccessibilityEnabled(context: Context): Boolean {
            if (Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0,
                ) != 1
            ) return false
            val expected = ComponentName(context, AccessibilityServiceUsher::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { flattened ->
                flattened.equals(expected.flattenToString(), ignoreCase = true) ||
                    flattened.equals(expected.flattenToShortString(), ignoreCase = true)
            }
        }
    }
}
