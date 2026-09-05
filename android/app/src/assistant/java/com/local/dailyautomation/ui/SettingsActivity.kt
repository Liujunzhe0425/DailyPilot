package com.local.dailyautomation.ui

import android.app.TimePickerDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.local.dailyautomation.data.AppConfigEntity
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.permissions.PermissionNavigator
import com.local.dailyautomation.permissions.PermissionKind
import com.local.dailyautomation.scheduler.DailyAlarmScheduler
import kotlinx.coroutines.launch
import org.autojs.autojs6.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private val repository by lazy { AssistantRepository(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch {
            val config = repository.getConfig() ?: AppConfigEntity(automationEnabled = false, runMinuteOfDay = 480, failureScreenshotsEnabled = true)
            binding.failureScreenshots.isChecked = config.failureScreenshotsEnabled
            binding.scheduleTime.text = "%02d:%02d".format(config.runMinuteOfDay / 60, config.runMinuteOfDay % 60)
            binding.failureScreenshots.setOnCheckedChangeListener { _, checked -> lifecycleScope.launch { repository.saveConfig((repository.getConfig() ?: config).copy(failureScreenshotsEnabled = checked)) } }
            binding.scheduleTime.setOnClickListener {
                TimePickerDialog(this@SettingsActivity, { _, hour, minute -> lifecycleScope.launch {
                    val updated = (repository.getConfig() ?: config).copy(runMinuteOfDay = hour * 60 + minute)
                    repository.saveConfig(updated); DailyAlarmScheduler(this@SettingsActivity).scheduleNext(updated); recreate()
                } }, config.runMinuteOfDay / 60, config.runMinuteOfDay % 60, true).show()
            }
        }
        binding.permissionAccessibility.setOnClickListener { PermissionNavigator(this).open(PermissionKind.ACCESSIBILITY) }
        binding.permissionOverlay.setOnClickListener { PermissionNavigator(this).open(PermissionKind.OVERLAY) }
        binding.permissionExactAlarm.setOnClickListener { PermissionNavigator(this).open(PermissionKind.EXACT_ALARM) }
        binding.permissionBattery.setOnClickListener { PermissionNavigator(this).open(PermissionKind.BATTERY_UNRESTRICTED) }
        binding.permissionAutostart.setOnClickListener { PermissionNavigator(this).open(PermissionKind.XIAOMI_AUTOSTART) }
        binding.cleanup.setOnClickListener { lifecycleScope.launch { repository.deleteExpired(Long.MAX_VALUE) } }
    }
}
