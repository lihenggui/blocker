package com.merxury.blocker.ui.applist

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.AppListItemBinding
import com.merxury.blocker.util.AppIconCache
import com.merxury.blocker.util.AppStateCache
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.libkit.entity.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AppListAdapter(val lifecycleScope: LifecycleCoroutineScope) :
    RecyclerView.Adapter<AppListAdapter.AppListViewHolder>() {

    init {
        setHasStableIds(true)
    }

    private val logger = XLog.tag("AppListAdapter")
    private var list: MutableList<Application> = mutableListOf()
    private var loadIconJob: Job? = null
    private var loadServiceStatusJob: Job? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppListViewHolder {
        val context = parent.context
        val binding = AppListItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return AppListViewHolder(context, binding)
    }

    override fun onBindViewHolder(holder: AppListViewHolder, position: Int) {
        val application = list.getOrNull(position)
        if (application == null) {
            logger.e("Application info is null, position: $position")
            return
        }
        holder.bind(position, application)
    }

    override fun onBindViewHolder(
        holder: AppListViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        super.onBindViewHolder(holder, position, payloads)
        if (payloads.isEmpty()) return
        val serviceState = payloads.getOrNull(0) as? AppState ?: return
        holder.bind(serviceState)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemId(position: Int): Long {
        return list.getOrNull(position)?.packageName?.hashCode()?.toLong() ?: 0
    }

    // We need to refresh the whole list
    @SuppressLint("NotifyDataSetChanged")
    fun updateAppList(list: List<Application>) {
        this.list = list.toMutableList()
        notifyDataSetChanged()
    }

    fun release() {
        if (loadIconJob?.isActive == true) {
            loadIconJob?.cancel()
        }
        if (loadServiceStatusJob?.isActive == true) {
            loadServiceStatusJob?.cancel()
        }
    }

    inner class AppListViewHolder(
        private val context: Context,
        private val binding: AppListItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(position: Int, app: Application) {
            // Load icon
            binding.appIcon.setTag(R.id.app_item_icon_id, app.packageName)
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
                        binding.appIcon
                    )
                } catch (e: Exception) {
                    logger.e("Failed to load icon, packageName: ${app.packageName}", e)
                }
            }
            binding.appName.text = app.label
            binding.versionCode.text = app.versionName
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
                state.total
            )
        }

        private fun getRunningServiceInfo(position: Int, app: Application) {
            binding.serviceStatus.text =
                context.getString(R.string.acquiring_service_status_please_wait)
            // Load service status
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