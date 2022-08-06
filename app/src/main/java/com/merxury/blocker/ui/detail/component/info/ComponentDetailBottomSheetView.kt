package com.merxury.blocker.ui.detail.component.info

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatTextView
import com.merxury.blocker.R
import com.merxury.blocker.view.AViewGroup
import com.merxury.blocker.view.BottomSheetHeaderView
import com.merxury.blocker.view.HeightAnimatableViewFlipper
import com.merxury.blocker.view.IHeaderView

class ComponentDetailBottomSheetView(context: Context) : AViewGroup(context), IHeaderView {

    val header = BottomSheetHeaderView(context).apply {
        layoutParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        title.text = context.getString(R.string.component_info)
    }

    private val viewFlipper = HeightAnimatableViewFlipper(context).apply {
        layoutParams =
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setInAnimation(context, R.anim.anim_fade_in)
        setOutAnimation(context, R.anim.anim_fade_out)
    }

    val title = AppCompatTextView(
        ContextThemeWrapper(
            context,
            R.style.TextView_SansSerifCondensedMedium
        )
    ).apply {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).also {
            it.topMargin = 4.dp
        }
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }

    init {
        val padding = 16.dp
        setPadding(padding, padding, padding, 0)
        addView(header)
        addView(viewFlipper)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        header.measure(
            (measuredWidth - paddingStart - paddingEnd).toExactlyMeasureSpec(),
            header.defaultHeightMeasureSpec(this)
        )
        title.measure(
            (measuredWidth - paddingStart - paddingEnd).toExactlyMeasureSpec(),
            title.defaultHeightMeasureSpec(this)
        )
        viewFlipper.measure(
            (measuredWidth - paddingStart - paddingEnd).toExactlyMeasureSpec(),
            viewFlipper.defaultHeightMeasureSpec(this)
        )
        setMeasuredDimension(
            measuredWidth,
            paddingTop + paddingBottom + header.measuredHeight +
                    viewFlipper.measuredHeight + 16.dp
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        header.layout(0, 0)

    }

    override fun getHeaderView(): BottomSheetHeaderView {
        return header
    }
}