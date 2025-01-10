/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.ui.home.applist

import android.content.Context
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.databinding.AppListItemBinding
import com.merxury.blocker.util.AppIconCache
import com.merxury.blocker.util.AppStateCache
import com.merxury.blocker.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppListAdapter(val lifecycleScope: LifecycleCoroutineScope) :
    ListAdapter<Application, AppListAdapter.AppListViewHolder>(DiffCallback()) {

    init {
        setHasStableIds(true)
    }

    private val logger = XLog.tag("AppListAdapter")
    private var loadIconJob: Job? = null
    private var loadServiceStatusJob: Job? = null
    var onItemClick: ((Application) -> Unit)? = null
    var contextMenuPosition = -1
    var onClearCacheClicked: ((Application) -> Unit)? = null
    var onClearDataClicked: ((Application) -> Unit)? = null
    var onForceStopClicked: ((Application) -> Unit)? = null
    var onUninstallClicked: ((Application) -> Unit)? = null
    var onEnableClicked: ((Application) -> Unit)? = null
    var onDisableClicked: ((Application) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppListViewHolder {
        val context = parent.context
        val binding = AppListItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return AppListViewHolder(context, binding)
    }

    override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
        val application = currentList.getOrNull(position)
        if (application == null) {
            logger.e("Application info is null, position: $position")
            return
        }
        holder.bind(position, application)
    }

    override fun onBindViewHolder(
        holder: AppListViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        super.onBindViewHolder(holder, position, payloads)
        if (payloads.isEmpty()) return
        val serviceState = payloads.getOrNull(0) as? AppState ?: return
        holder.bind(serviceState)
    }

    override fun getItemCount(): Int {
        return currentList.size
    }

    override fun getItemId(position: Int): Long {
        return currentList.getOrNull(position)?.packageName?.hashCode()?.toLong() ?: 0
    }

    fun updateAppList(list: List<Application>) {
        submitList(list)
    }

    fun release() {
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
        if (loadServiceStatusJob?.isActive == true) {
            loadServiceStatusJob?.cancel()
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Application>() {
        override fun areItemsTheSame(oldItem: Application, newItem: Application): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: Application, newItem: Application): Boolean {
            return oldItem == newItem
        }
    }

    inner class AppListViewHolder(
        private val context: Context,
        private val binding: AppListItemBinding,
    ) :
        RecyclerView.ViewHolder(binding.root), View.OnCreateContextMenuListener {

        init {
            binding.root.setOnCreateContextMenuListener(this)
        }

        override fun onCreateContextMenu(
            menu: ContextMenu?,
            view: View?,
            menuInfo: ContextMenu.ContextMenuInfo?,
        ) {
            val item = currentList.getOrNull(contextMenuPosition)
            if (item == null) {
                logger.e("Can't find item in the list, position: $contextMenuPosition")
                return
            }
            menu?.add(Menu.NONE, 0, 0, R.string.clear_cache)
                ?.setOnMenuItemClickListener {
                    onClearCacheClicked?.invoke(item)
                    true
                }
            menu?.add(Menu.NONE, 1, 1, R.string.clear_data)
                ?.setOnMenuItemClickListener {
                    onClearDataClicked?.invoke(item)
                    true
                }
            menu?.add(Menu.NONE, 2, 2, R.string.force_stop)
                ?.setOnMenuItemClickListener {
                    onForceStopClicked?.invoke(item)
                    true
                }
            menu?.add(Menu.NONE, 3, 3, R.string.uninstall)
                ?.setOnMenuItemClickListener {
                    onUninstallClicked?.invoke(item)
                    true
                }
            menu?.add(Menu.NONE, 4, 4, R.string.enable)
                ?.setOnMenuItemClickListener {
                    onEnableClicked?.invoke(item)
                    true
                }
            menu?.add(Menu.NONE, 5, 5, R.string.disable)
                ?.setOnMenuItemClickListener {
                    onDisableClicked?.invoke(item)
                    true
                }
        }

        fun bind(position: Int, app: Application) {
            binding.root.setOnClickListener {
                onItemClick?.invoke(app)
            }
            binding.root.setOnLongClickListener {
                contextMenuPosition = position
                false
            }
            // Load icon
            binding.appIcon.setTag(
                R.id.app_item_icon_id,
                app.packageName,
            )
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
                        binding.appIcon,
                    )
                } catch (e: Exception) {
                    logger.e("Failed to load icon, packageName: ${app.packageName}", e)
                }
            }
            binding.appName.text = app.label
            binding.versionName.text = app.versionName
            if (PreferenceUtil.getShowServiceInfo(context)) {
                binding.serviceStatus.visibility = View.VISIBLE
                getRunningServiceInfo(position, app)
            } else {
                binding.serviceStatus.visibility = View.GONE
            }
        }

        fun bind(state: AppState) {
            binding.serviceStatus.text = context.getString(
                R.string.service_status_template,
                state.running,
                state.blocked,
                state.total,
            )
        }

        private fun getRunningServiceInfo(position: Int, app: Application) {
            binding.serviceStatus.text =
                context.getString(R.string.acquiring_service_status_please_wait)
            // Load service status
            val cachedState = AppStateCache.getInCache(app.packageName)
            if (cachedState != null) {
                bind(cachedState)
                return
            }
            // Can't find in cache, load service status directly
            lifecycleScope.launch(Dispatchers.IO) {
                loadServiceStatusJob = launch {
                    val result = AppStateCache.get(context, app.packageName)
                    binding.root.post {
                        notifyItemChanged(position, result)
                    }
                }
            }
        }
    }
}
