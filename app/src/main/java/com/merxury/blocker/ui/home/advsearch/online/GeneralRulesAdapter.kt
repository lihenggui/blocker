/*
 * Copyright 2022 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.ui.home.advsearch.online

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.merxury.blocker.R
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.model.OnlineSourceType
import com.merxury.blocker.databinding.GeneralRulesCardItemBinding
import timber.log.Timber

class GeneralRulesAdapter : RecyclerView.Adapter<GeneralRulesAdapter.ViewHolder>() {

    private val list = mutableListOf<GeneralRule>()
    var onItemClickListener: ((GeneralRule) -> Unit)? = null
    var onSearchClickListener: ((GeneralRule) -> Unit)? = null

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val binding = GeneralRulesCardItemBinding.inflate(
            LayoutInflater.from(context),
            parent,
            false
        )
        return ViewHolder(context, binding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list.getOrNull(position)
        if (item == null) {
            Timber.e("Item at position $position is null")
            return
        }
        holder.bind(item)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(data: List<GeneralRule>) {
        list.clear()
        list.addAll(data)
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val context: Context,
        private val binding: GeneralRulesCardItemBinding
    ) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GeneralRule) {
            binding.name.text = item.name
            binding.company.text = item.company
            if (item.description.isNullOrEmpty()) {
                binding.descriptionTitle.visibility = View.GONE
                binding.description.visibility = View.GONE
            } else {
                binding.descriptionTitle.visibility = View.VISIBLE
                binding.description.visibility = View.VISIBLE
                binding.description.text = item.description
            }
            binding.rules.text = item.searchKeyword.joinToString("\n")
            binding.safeToBlock.text = if (item.safeToBlock == true) {
                context.getString(R.string.yes)
            } else {
                context.getString(R.string.no)
            }
            if (item.sideEffect == null) {
                binding.sideEffectTitle.visibility = View.GONE
                binding.sideEffect.visibility = View.GONE
            } else {
                binding.sideEffectTitle.visibility = View.VISIBLE
                binding.sideEffect.visibility = View.VISIBLE
                binding.sideEffect.text = item.sideEffect
            }
            if (item.contributors.isEmpty()) {
                binding.contributorTitle.visibility = View.GONE
                binding.contributor.visibility = View.GONE
            } else {
                binding.contributorTitle.visibility = View.VISIBLE
                binding.contributor.visibility = View.VISIBLE
                binding.contributor.text = item.contributors.joinToString()
            }

            if (!item.iconUrl.isNullOrEmpty()) {
                val baseUrl = getOnlineSourceType(context).baseUrl
                val iconUrl = baseUrl + item.iconUrl
                binding.icon.load(iconUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_android)
                }
            }

            binding.searchButton.setOnClickListener {
                onSearchClickListener?.invoke(item)
            }
        }
    }

    // TODO use DataStore instead
    private fun getOnlineSourceType(context: Context): OnlineSourceType {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val value = pref.getString(
            context.getString(R.string.key_pref_online_source_type), "GITLAB"
        ).orEmpty()
        return try {
            OnlineSourceType.valueOf(value)
        } catch (e: Exception) {
            OnlineSourceType.GITHUB
        }
    }
}
