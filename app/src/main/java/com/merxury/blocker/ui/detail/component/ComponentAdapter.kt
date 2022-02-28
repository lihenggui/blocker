package com.merxury.blocker.ui.detail.component

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.databinding.ComponentItemBinding

class ComponentAdapter :
    ListAdapter<ComponentData, ComponentAdapter.ComponentViewHolder>(DiffCallback()) {

    private val logger = XLog.tag("ComponentAdapter")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComponentViewHolder {
        val context = parent.context
        val binding = ComponentItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return ComponentViewHolder(context, binding)
    }

    override fun onBindViewHolder(holder: ComponentViewHolder, position: Int) {
        val component = currentList.getOrNull(position)
        if (component == null) {
            logger.e("Component info is null, position: $position")
            return
        }
        holder.bind(component)
    }

    private class DiffCallback : DiffUtil.ItemCallback<ComponentData>() {
        override fun areItemsTheSame(oldItem: ComponentData, newItem: ComponentData): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: ComponentData, newItem: ComponentData): Boolean {
            return oldItem == newItem
        }
    }

    inner class ComponentViewHolder(val context: Context, val binding: ComponentItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(component: ComponentData) {
            binding.componentName.text = component.simpleName
            binding.componentPackageName.text = component.name
            val isBlocked = component.ifwBlocked || component.pmBlocked
            binding.componentSwitch.isChecked = !isBlocked
        }
    }
}