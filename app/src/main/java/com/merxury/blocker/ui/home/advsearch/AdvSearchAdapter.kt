package com.merxury.blocker.ui.home.advsearch

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.merxury.blocker.databinding.SearchAppComponentBinding
import com.merxury.blocker.databinding.SearchAppHeaderBinding
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.libkit.entity.Application

class AdvSearchAdapter(val lifecycleScope: LifecycleCoroutineScope) :
    ListAdapter<Pair<Application, List<ComponentData>>> {
    inner class SearchHeaderViewHolder(
        private val context: Context,
        private val binding: SearchAppHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

    }

    inner class SearchItemViewHolder(
        private val context: Context,
        private val binding: SearchAppComponentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

    }
}