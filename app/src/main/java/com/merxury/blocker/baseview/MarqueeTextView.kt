package com.merxury.blocker.baseview

import android.content.Context
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet

class MarqueeTextView : AppCompatTextView {
    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    override fun isFocused(): Boolean {
        return true
    }
}
