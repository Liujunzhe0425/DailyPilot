package com.local.dailyautomation.ui

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.local.dailyautomation.data.AssistantRepository
import com.local.dailyautomation.ui.adapters.ModuleAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.autojs.autojs6.databinding.ActivityModuleManagementBinding

class ModuleManagementActivity : AppCompatActivity() {
    private val repository by lazy { AssistantRepository(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityModuleManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val adapter = ModuleAdapter(
            onToggle = { id, enabled -> lifecycleScope.launch { repository.setModuleEnabled(id, enabled) } },
            onRun = { id -> lifecycleScope.launch {
                val runtime = com.local.dailyautomation.runtime.AutomationRuntime
                runtime.blockingPermission(this@ModuleManagementActivity)?.let { message ->
                    Toast.makeText(this@ModuleManagementActivity, message, Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@ModuleManagementActivity, SettingsActivity::class.java))
                    return@launch
                }
                runtime.start(this@ModuleManagementActivity, com.local.dailyautomation.domain.TriggerKind.RUN_ONE_MANUAL, listOf(id))
            } },
        )
        binding.moduleRecycler.layoutManager = LinearLayoutManager(this)
        binding.moduleRecycler.adapter = adapter
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.move(source.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                lifecycleScope.launch { repository.saveModuleOrder(adapter.ids()) }
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        }).attachToRecyclerView(binding.moduleRecycler)
        lifecycleScope.launch { repository.observeModules().collectLatest(adapter::submit) }
    }
}
