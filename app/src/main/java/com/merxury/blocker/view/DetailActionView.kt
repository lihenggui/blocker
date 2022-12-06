package com.merxury.blocker.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.merxury.blocker.R
import com.merxury.blocker.databinding.DetailActionItemBinding

class DetailActionView : ConstraintLayout {
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

    private val binding = DetailActionItemBinding.inflate(LayoutInflater.from(context), this, true)

    private fun initAttrs(context: Context, attrs: AttributeSet?) {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DetailActionView)
        val title = typedArray.getString(R.styleable.DetailActionView_detail_title)
        val icon = typedArray.getResourceId(R.styleable.DetailActionView_detail_icon, 0)
        typedArray.recycle()
        binding.title.text = title
        binding.icon.setImageResource(icon)
    }

    var title: String
        get() = binding.title.text.toString()
        set(value) {
            binding.title.text = value
        }

    var icon: Int
        get() = binding.icon.id
        set(value) {
            binding.icon.setImageResource(value)
        }
}
