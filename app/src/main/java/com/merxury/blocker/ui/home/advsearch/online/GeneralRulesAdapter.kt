package com.merxury.blocker.ui.home.advsearch.online

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.databinding.GeneralRulesCardItemBinding

class GeneralRulesAdapter : RecyclerView.Adapter<GeneralRulesAdapter.ViewHolder>() {

    private val list = mutableListOf<GeneralRule>()
    private val logger = XLog.tag("GeneralRulesAdapter")
    var onItemClickListener: ((GeneralRule) -> Unit)? = null

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val binding =
            GeneralRulesCardItemBinding.inflate(LayoutInflater.from(context), parent, false)
        return ViewHolder(context, binding)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list.getOrNull(position)
        if (item == null) {
            logger.e("Item at position $position is null")
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
            binding.root.setOnClickListener {
                onItemClickListener?.invoke(item)
            }
            binding.name.text = context.getString(R.string.name_with_semicolon_template, item.name)
            binding.company.text =
                context.getString(R.string.company_with_semicolon_template, item.company)
            binding.description.text =
                context.getString(R.string.description_with_semicolon_template, item.description)
            binding.rules.text = context.getString(
                R.string.rule_with_semicolon_template,
                item.searchKeyword.toString()
            )
            binding.safeToBlock.text = context.getString(
                R.string.safe_to_block_with_semicolon_template,
                item.safeToBlock.toString()
            )
            binding.sideEffect.text = context.getString(
                R.string.side_effect_with_semicolon_template,
                item.sideEffect.toString()
            )
            if (!item.iconUrl.isNullOrEmpty()) {
                val url = "https://gitee.com/Merxury/blocker-general-rules/raw/main"
                val iconUrl = url + item.iconUrl
                Glide.with(context)
                    .load(iconUrl)
                    .into(binding.icon)
            }
        }
    }
}