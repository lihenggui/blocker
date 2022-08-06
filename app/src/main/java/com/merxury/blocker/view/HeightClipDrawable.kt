package com.merxury.blocker.view

import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper

/**
 * Author: Absinthe
 * package com.absinthe.libraries.utils.view
 */
class HeightClipDrawable(delegate: Drawable) : DrawableWrapper(delegate) {
    var clippedHeight: Int? = null
        set(value) {
            field = value
            bounds = bounds
        }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, clippedHeight ?: bottom)
    }
}