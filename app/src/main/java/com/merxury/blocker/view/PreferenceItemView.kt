package com.merxury.blocker.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.merxury.blocker.R
import com.merxury.blocker.databinding.PreferenceItemViewBinding

class PreferenceItemView : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initAttrs(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, attributeSetId: Int) : super(
        context,
        attrs,
        attributeSetId
    ) {
        initAttrs(context, attrs)
    }

    private val binding: PreferenceItemViewBinding =
        PreferenceItemViewBinding.inflate(LayoutInflater.from(context), this, true)

    private fun initAttrs(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PreferenceItemView)
        val title = typedArray.getString(R.styleable.PreferenceItemView_item_title)
        val summary = typedArray.getString(R.styleable.PreferenceItemView_item_summary)
        typedArray.recycle()
        binding.title.text = title
        binding.summary.text = summary
    }

    fun setSummary(summary: String?) {
        binding.summary.text = summary
    }
}