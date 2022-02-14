package com.merxury.blocker.ui.applist

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.AppListItemBinding
import com.merxury.libkit.entity.Application
import kotlinx.coroutines.Job
import me.zhanghai.android.appiconloader.AppIconLoader

class AppListAdapter(val lifecycleScope: LifecycleCoroutineScope) :
    RecyclerView.Adapter<AppListAdapter.AppListViewHolder>() {

    init {
        setHasStableIds(true)
    }

    private val logger = XLog.tag("AppListAdapter")
    private var list: MutableList<Application> = mutableListOf()
    private var appState: MutableList<AppState> = mutableListOf()
    private var loadIconJob: Job? = null

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
        holder.bind(application)
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

    class AppListViewHolder(private val context: Context, private val binding: AppListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(app: Application) {
            val loader = AppIconLoader(
                context.resources.getDimensionPixelSize(R.dimen.app_icon_size),
                false,
                context
            )
            binding.appName.text = app.label
            app.packageInfo?.applicationInfo?.let {
                binding.appIcon.setImageBitmap(loader.loadIcon(it))
            }
        }
    }
}