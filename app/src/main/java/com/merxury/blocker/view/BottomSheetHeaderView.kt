package com.merxury.blocker.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.TransitionDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.annotation.StyleableRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.TintTypedArray
import androidx.core.content.ContextCompat
import androidx.core.view.marginTop
import com.merxury.blocker.R
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Author: Absinthe
 */
class BottomSheetHeaderView(context: Context) : AViewGroup(context) {

    private val handler = View(context).apply {
        layoutParams = LayoutParams(36.dp, 4.dp)
        background = TransitionDrawable(
            arrayOf(
                ContextCompat.getDrawable(context, R.drawable.bg_dialog_handler),
                ContextCompat.getDrawable(context, R.drawable.bg_dialog_handler_activated)
            )
        )
        addView(this)
    }

    val title =
        AppCompatTextView(ContextThemeWrapper(context, R.style.TextView_SansSerifMedium)).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.topMargin = 16.dp
            }
            setPadding(16.dp, 0, 16.dp, 0)
            setTextAppearance(context.getResourceIdByAttr(com.google.android.material.R.attr.textAppearanceHeadline5))
            gravity = Gravity.CENTER_HORIZONTAL
            addView(this)
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        handler.autoMeasure()
        title.autoMeasure()
        setMeasuredDimension(
            measuredWidth,
            handler.marginTop + handler.measuredHeight + title.marginTop + title.measuredHeight
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        handler.layout(handler.toHorizontalCenter(this), 0)
        title.layout(0, handler.bottom + title.marginTop)
    }

    fun onHandlerActivated(activated: Boolean) {
        if (activated) {
            (handler.background as TransitionDrawable).startTransition(150)
        } else {
            (handler.background as TransitionDrawable).reverseTransition(150)
        }
    }
    @SuppressLint("RestrictedApi")
    private fun Context.getResourceIdByAttr(@AttrRes attr: Int): Int =
        obtainStyledAttributesCompat(attrs = intArrayOf(attr)).use { it.getResourceId(0, 0) }

    @SuppressLint("RestrictedApi")
    private fun Context.obtainStyledAttributesCompat(
        set: AttributeSet? = null,
        @StyleableRes attrs: IntArray,
        @AttrRes defStyleAttr: Int = 0,
        @StyleRes defStyleRes: Int = 0
    ): TintTypedArray =
        TintTypedArray.obtainStyledAttributes(this, set, attrs, defStyleAttr, defStyleRes)

    @OptIn(ExperimentalContracts::class)
    @SuppressLint("RestrictedApi")
    private inline fun <R> TintTypedArray.use(block: (TintTypedArray) -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        return try {
            block(this)
        } finally {
            recycle()
        }
    }
}