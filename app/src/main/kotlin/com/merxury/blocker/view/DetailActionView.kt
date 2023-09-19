/*
 * Copyright 2023 Blocker
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
        attributeSetId,
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
