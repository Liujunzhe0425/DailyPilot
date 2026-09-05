package com.local.dailyautomation.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.local.dailyautomation.data.AppConfigEntity
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.data.ModuleConfigEntity
import com.local.dailyautomation.domain.ModuleCatalog
import com.local.dailyautomation.domain.TriggerKind
import com.local.dailyautomation.runtime.AutomationRuntime
import com.local.dailyautomation.scheduler.DailyAlarmScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.autojs.autojs6.databinding.ActivityDashboardBinding
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId

class DashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private val repository by lazy { AssistantRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch {
            seedIfNeeded()
            showLatestTodayStatus()
        }
        binding.automationMasterSwitch.setOnCheckedChangeListener { _, checked -> lifecycleScope.launch { updateMaster(checked) } }
        binding.runAll.setOnClickListener { runEnabled(TriggerKind.RUN_ALL_MANUAL) }
        binding.refreshStatus.setOnClickListener { refreshTodayStatus() }
        binding.manageModules.setOnClickListener { startActivity(Intent(this, ModuleManagementActivity::class.java)) }
        binding.openHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.openSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        lifecycleScope.launch {
            repository.observeConfig().collectLatest { config ->
                val value = config ?: defaultConfig()
                binding.automationMasterSwitch.isChecked = value.automationEnabled
                binding.dailyTime.text = formatTime(value.runMinuteOfDay)
                binding.nextRun.text = "定时：每天 ${formatTime(value.runMinuteOfDay)}"
            }
        }
        lifecycleScope.launch {
            repository.observeModules().collectLatest { modules ->
                binding.moduleList.text = modules.sortedBy { it.sortOrder }.joinToString("\n") {
                    "${if (it.enabled) "✓" else "○"} ${displayName(it.moduleId)}"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) lifecycleScope.launch { showLatestTodayStatus() }
    }

    private fun runEnabled(trigger: TriggerKind) = lifecycleScope.launch {
        AutomationRuntime.blockingPermission(this@DashboardActivity)?.let { message ->
            Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_LONG).show()
            startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java))
            return@launch
        }
        val ids = repository.getModules().filter { it.enabled }.sortedBy { it.sortOrder }.map { it.moduleId }
        if (ids.isNotEmpty()) AutomationRuntime.start(this@DashboardActivity, trigger, ids)
    }

    private fun refreshTodayStatus() = lifecycleScope.launch {
        AutomationRuntime.blockingPermission(this@DashboardActivity)?.let { message ->
            Toast.makeText(this@DashboardActivity, message, Toast.LENGTH_LONG).show()
            startActivity(Intent(this@DashboardActivity, SettingsActivity::class.java))
            return@launch
        }
        val modules = repository.getModules().sortedBy { it.sortOrder }
        if (modules.isEmpty()) return@launch
        binding.refreshStatus.isEnabled = false
        binding.todayStatus.text = modules.joinToString("\n") { "… ${displayName(it.moduleId)}：等待检查" }
        val runId = runCatching {
            AutomationRuntime.start(
                this@DashboardActivity,
                TriggerKind.REFRESH_ONLY,
                modules.map { it.moduleId },
            )
        }.getOrElse { error ->
            binding.refreshStatus.isEnabled = true
            binding.todayStatus.text = "检查未启动：${error.message ?: error.javaClass.simpleName}"
            return@launch
        }
        while (true) {
            val run = repository.allRuns().firstOrNull { it.runId == runId }
            val results = repository.moduleRuns(runId).associateBy { it.moduleId }
            binding.todayStatus.text = modules.joinToString("\n") { module ->
                val result = results[module.moduleId]
                val label = when {
                    result == null -> "等待检查"
                    result.finishedAt == null -> "检查中"
                    else -> statusLabel(result.outcome)
                }
                "${statusIcon(result?.outcome)} ${displayName(module.moduleId)}：$label"
            }
            if (run?.finishedAt != null) break
            delay(400)
        }
        binding.refreshStatus.isEnabled = true
    }

    private suspend fun showLatestTodayStatus() {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val modules = repository.getModules().sortedBy { it.sortOrder }
        val latest = linkedMapOf<String, String>()
        repository.runsStartedSince(startOfDay).forEach { run ->
            repository.moduleRuns(run.runId).sortedByDescending { it.startedAt }.forEach { result ->
                if (result.finishedAt != null && result.moduleId !in latest) latest[result.moduleId] = result.outcome
            }
        }
        binding.todayStatus.text = modules.joinToString("\n") { module ->
            val outcome = latest[module.moduleId]
            "${statusIcon(outcome)} ${displayName(module.moduleId)}：${outcome?.let(::statusLabel) ?: "尚未检查"}"
        }.ifBlank { "尚未检查" }
    }

    private fun statusLabel(outcome: String): String = when (outcome) {
        "ALREADY_COMPLETED", "SUCCEEDED" -> "今日已完成"
        "NOT_COMPLETED" -> "今日未完成"
        "MANUAL_VERIFICATION_REQUIRED" -> "需要人工登录/验证"
        "FAILED" -> "暂时无法确认"
        "USER_STOPPED", "PAUSE_TIMEOUT" -> "检查已停止"
        else -> "尚未确认"
    }

    private fun statusIcon(outcome: String?): String = when (outcome) {
        "ALREADY_COMPLETED", "SUCCEEDED" -> "✓"
        "NOT_COMPLETED" -> "○"
        "FAILED", "MANUAL_VERIFICATION_REQUIRED" -> "!"
        else -> "…"
    }

    private suspend fun seedIfNeeded() {
        if (repository.getModules().isEmpty()) {
            val json = assets.open("project/module-manifest.json").bufferedReader().use { it.readText() }
            repository.seedModules(ModuleCatalog.load(json).all().map { ModuleConfigEntity(it.id, it.enabled, it.sortOrder) })
        }
        if (repository.getConfig() == null) repository.saveConfig(defaultConfig())
    }

    private suspend fun updateMaster(enabled: Boolean) {
        val config = (repository.getConfig() ?: defaultConfig()).copy(automationEnabled = enabled)
        repository.saveConfig(config)
        DailyAlarmScheduler(this).scheduleNext(config)
    }

    private fun defaultConfig() = AppConfigEntity(automationEnabled = false, runMinuteOfDay = 8 * 60, failureScreenshotsEnabled = true)
    private fun formatTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
    private fun displayName(id: String) = when (id) { "weihuda-sign" -> "湖南大学微生活签到"; "withdrawal-coupon" -> "微信支付笔笔省免费券"; "xianyu-polish" -> "闲鱼免费每日擦亮"; else -> id }
}
