package com.merxury.blocker.ui.detail.component

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.ComponentControllerProxy
import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.databinding.ComponentItemBinding
import com.merxury.ifw.IntentFirewallImpl

class ComponentAdapter(val lifecycleScope: LifecycleCoroutineScope) :
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
            binding.componentName.text = component.name
            binding.componentPackageName.text = component.packageName
            val pmState = ComponentControllerProxy.getInstance(EControllerMethod.PM, context)
                .checkComponentEnableState(component.packageName, component.name)
            val ifwState = IntentFirewallImpl.getInstance(context, component.packageName)
                .getComponentEnableState(component.packageName, component.name)
            logger.i("Component ${component.simpleName} pm state: $pmState, ifw state: $ifwState")
            binding.componentSwitch.isChecked = pmState && ifwState
        }
    }
}