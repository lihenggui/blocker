package com.merxury.blocker.ui.home.advsearch

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.SearchAppHeaderBinding
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.blocker.util.AppIconCache
import com.merxury.blocker.util.unsafeLazy
import com.merxury.libkit.entity.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AdvSearchAdapter(val lifecycleScope: LifecycleCoroutineScope) :
    ListAdapter<Pair<Application, List<ComponentData>>, AdvSearchAdapter.SearchHeaderViewHolder>(
        DiffCallback()
    ) {
    private val logger = XLog.tag("AdvSearchAdapter")
    private var loadIconJob: Job? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchHeaderViewHolder {
        val context = parent.context
        val binding = SearchAppHeaderBinding.inflate(LayoutInflater.from(context), parent, false)
        return SearchHeaderViewHolder(context, binding)
    }

    override fun onBindViewHolder(holder: SearchHeaderViewHolder, position: Int) {
        val item = currentList.getOrNull(position)
        if (item == null) {
            logger.e("onBindViewHolder: item at position $position is null")
            return
        }
        holder.bind(item)
    }

    fun release() {
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Pair<Application, List<ComponentData>>>() {
        override fun areItemsTheSame(
            oldItem: Pair<Application, List<ComponentData>>,
            newItem: Pair<Application, List<ComponentData>>
        ): Boolean {
            return oldItem.first.packageName == newItem.first.packageName
        }

        override fun areContentsTheSame(
            oldItem: Pair<Application, List<ComponentData>>,
            newItem: Pair<Application, List<ComponentData>>
        ): Boolean {
            return oldItem.first == newItem.first &&
                    oldItem.second == newItem.second
        }
    }

    inner class SearchHeaderViewHolder(
        private val context: Context,
        private val binding: SearchAppHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val itemAdapter by unsafeLazy { ComponentItemAdapter() }
        fun bind(list: Pair<Application, List<ComponentData>>) {
            val app = list.first
            val components = list.second
            binding.appName.text = app.label
            binding.componentList.apply {
                adapter = itemAdapter
            }
            itemAdapter.submitList(components)
            binding.icon.setTag(R.id.app_item_icon_id, app.packageName)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val appInfo = app.packageInfo?.applicationInfo ?: run {
                        logger.e("Application info is null, packageName: ${app.packageName}")
                        return@launch
                    }
                    loadIconJob = AppIconCache.loadIconBitmapAsync(
                        context,
                        appInfo,
                        appInfo.uid / 100000,
                        binding.icon
                    )
                } catch (e: Exception) {
                    logger.e("Failed to load icon, packageName: ${app.packageName}", e)
                }
            }
        }
    }
}