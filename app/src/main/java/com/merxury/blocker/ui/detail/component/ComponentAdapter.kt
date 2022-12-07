/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.ui.detail.component

import android.content.Context
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.entity.EComponentType
import com.merxury.blocker.data.component.OnlineComponentData
import com.merxury.blocker.data.component.OnlineComponentDataRepository
import com.merxury.blocker.databinding.ComponentItemBinding
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComponentAdapter constructor(val lifecycleScope: LifecycleCoroutineScope) :
    ListAdapter<ComponentData, ComponentAdapter.ComponentViewHolder>(DiffCallback()) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ComponentAdapterEntryPoint {
        fun getDataRepository(): OnlineComponentDataRepository
    }

    private val logger = XLog.tag("ComponentAdapter")
    private var recyclerView: RecyclerView? = null
    var contextMenuPosition = -1
    var onSwitchClick: ((ComponentData, Boolean) -> Unit)? = null
    var onCopyClick: ((ComponentData) -> Unit)? = null
    var onLaunchClick: ((ComponentData) -> Unit)? = null
    var onDetailClick: ((ComponentData) -> Unit)? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

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
        holder.bind(component, position)
    }

    override fun onBindViewHolder(
        holder: ComponentViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            return super.onBindViewHolder(holder, position, payloads)
        }
        val componentData = payloads[0] as OnlineComponentData?
        if (componentData == null) {
            logger.e("Component info is null, position: $position")
            return
        }
        holder.bindOnlineData(componentData)
    }

    override fun onViewRecycled(holder: ComponentViewHolder) {
        holder.itemView.setOnClickListener(null)
        super.onViewRecycled(holder)
    }

    fun updateItem(component: ComponentData) {
        val name = component.name
        val index = currentList.indexOfFirst { it.name == name }
        if (index == -1) {
            logger.e("Can't find updated item in the list, name: $name")
            return
        }
        notifyItemChanged(index)
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
        RecyclerView.ViewHolder(binding.root), View.OnCreateContextMenuListener {

        init {
            binding.root.setOnCreateContextMenuListener(this)
        }

        fun bind(component: ComponentData, position: Int) {
            binding.componentName.text = component.simpleName
            binding.componentPackageName.text = component.name
            val isBlocked = component.ifwBlocked || component.pmBlocked
            binding.componentSwitch.apply {
                setOnCheckedChangeListener(null)
                isChecked = !isBlocked
                setOnCheckedChangeListener { _, isChecked ->
                    onSwitchClick?.invoke(component, isChecked)
                }
            }
            binding.runningIndicator.isVisible = component.isRunning
            binding.root.setOnClickListener {
                onDetailClick?.invoke(component)
            }
            binding.root.setOnLongClickListener {
                contextMenuPosition = position
                false
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context,
                    ComponentAdapterEntryPoint::class.java
                )
                val fetcher = entryPoint.getDataRepository()
                val onlineData = fetcher.getComponentData(context, component.name)
                withContext(Dispatchers.Main) {
                    if (recyclerView?.isComputingLayout == true) {
                        return@withContext
                    }
                    if (onlineData != null) {
                        notifyItemChanged(position, onlineData)
                    }
                }
            }
        }

        fun bindOnlineData(data: OnlineComponentData) {
            binding.componentDescription.apply {
                isVisible = true
                text = data.description
            }
        }

        override fun onCreateContextMenu(
            menu: ContextMenu?,
            view: View?,
            menuInfo: ContextMenu.ContextMenuInfo?
        ) {
            val item = currentList.getOrNull(contextMenuPosition)
            if (item == null) {
                logger.e("Can't find item in the list, position: $contextMenuPosition")
                return
            }
            menu?.add(Menu.NONE, 0, 0, R.string.copy_name)
                ?.setOnMenuItemClickListener {
                    onCopyClick?.invoke(item)
                    true
                }
            if (item.type == EComponentType.ACTIVITY) {
                menu?.add(Menu.NONE, 1, 1, R.string.launch_activity)
                    ?.setOnMenuItemClickListener {
                        onLaunchClick?.invoke(item)
                        true
                    }
            }
        }
    }
}
