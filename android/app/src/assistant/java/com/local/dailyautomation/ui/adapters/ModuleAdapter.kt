package com.local.dailyautomation.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.local.dailyautomation.data.ModuleConfigEntity
import org.autojs.autojs6.databinding.ItemModuleBinding
import java.util.Collections

class ModuleAdapter(private val onToggle: (String, Boolean) -> Unit, private val onRun: (String) -> Unit) : RecyclerView.Adapter<ModuleAdapter.Holder>() {
    private val items = mutableListOf<ModuleConfigEntity>()
    fun submit(values: List<ModuleConfigEntity>) { items.clear(); items.addAll(values.sortedBy { it.sortOrder }); notifyDataSetChanged() }
    fun move(from: Int, to: Int) { Collections.swap(items, from, to); notifyItemMoved(from, to) }
    fun ids() = items.map { it.moduleId }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemModuleBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])
    inner class Holder(private val binding: ItemModuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ModuleConfigEntity) {
            binding.moduleName.text = item.moduleId
            binding.moduleToggle.setOnCheckedChangeListener(null); binding.moduleToggle.isChecked = item.enabled
            binding.moduleToggle.setOnCheckedChangeListener { _, checked -> onToggle(item.moduleId, checked) }
            binding.moduleTodayState.text = "今日状态：待检查"
            binding.moduleLastRun.text = "最近运行：暂无"
            binding.moduleRunNow.setOnClickListener { onRun(item.moduleId) }
        }
    }
}
