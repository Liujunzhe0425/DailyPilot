package com.local.dailyautomation.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.local.dailyautomation.data.AssistantRepository
import kotlinx.coroutines.launch
import org.autojs.autojs6.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lifecycleScope.launch {
            val repository = AssistantRepository(this@HistoryActivity)
            binding.historyList.text = repository.allRuns().joinToString("\n\n") {
                "${it.trigger} · ${it.state}\n${java.text.DateFormat.getDateTimeInstance().format(it.startedAt)}"
            }.ifBlank { "暂无运行记录" }
            binding.clearHistory.setOnClickListener {
                lifecycleScope.launch { repository.deleteExpired(Long.MAX_VALUE); recreate() }
            }
        }
    }
}
