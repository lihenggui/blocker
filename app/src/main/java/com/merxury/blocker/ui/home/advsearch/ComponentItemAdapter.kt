package com.merxury.blocker.ui.home.advsearch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.databinding.SearchAppComponentBinding
import com.merxury.blocker.ui.detail.component.ComponentData

class ComponentItemAdapter :
    ListAdapter<ComponentData, ComponentItemAdapter.ComponentItemViewHolder>(DiffCallback()) {
    private val logger = XLog.tag("ComponentItemAdapter")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComponentItemViewHolder {
        val context = parent.context
        val binding = SearchAppComponentBinding.inflate(LayoutInflater.from(context), parent, false)
        return ComponentItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComponentItemViewHolder, position: Int) {
        val component = currentList.getOrNull(position)
        if (component == null) {
            logger.e("Component in position $position is null")
            return
        }
        holder.bind(component)
    }

    override fun getItemId(position: Int): Long {
        return currentList.getOrNull(position)?.name?.hashCode()?.toLong() ?: 0
    }

    private class DiffCallback : DiffUtil.ItemCallback<ComponentData>() {
        override fun areItemsTheSame(oldItem: ComponentData, newItem: ComponentData): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: ComponentData, newItem: ComponentData): Boolean {
            return oldItem == newItem
        }
    }

    inner class ComponentItemViewHolder(private val binding: SearchAppComponentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(componentData: ComponentData) {
            logger.i("Binding component ${componentData.name}")
            binding.componentName.text = componentData.name
            binding.componentSwitch.isEnabled =
                !componentData.pmBlocked || !componentData.ifwBlocked
        }
    }
}