package com.merxury.blocker.ui.detail.component

import android.content.Context
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.ComponentItemBinding
import com.merxury.libkit.entity.EComponentType

class ComponentAdapter :
    ListAdapter<ComponentData, ComponentAdapter.ComponentViewHolder>(DiffCallback()) {

    private val logger = XLog.tag("ComponentAdapter")
    var contextMenuPosition = -1
    var onSwitchClick: ((ComponentData, Boolean) -> Unit)? = null
    var onCopyClick: ((ComponentData) -> Unit)? = null
    var onLaunchClick: ((ComponentData) -> Unit)? = null

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
            binding.root.setOnLongClickListener {
                contextMenuPosition = position
                false
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