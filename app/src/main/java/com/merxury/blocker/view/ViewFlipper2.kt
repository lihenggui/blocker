package com.merxury.blocker.view

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.Animation
import android.widget.ViewFlipper
import androidx.core.view.isVisible

/**
 * Author: Absinthe
 * package com.absinthe.libraries.utils.view
 * Like [ViewFlipper], but uses [ViewPropertyAnimator] to animate child changes
 * instead of [Animation] which was causing [https://github.com/saket/cascade/issues/4].
 */
@Deprecated("Internal to cascade. Shouldn't be used.")  // Can't be made internal.
abstract class ViewFlipper2(context: Context) : ViewFlipper(context) {
    val displayedChildView: View?
        get() = getChildAt(displayedChild)

    internal fun setDisplayedChild(
        inView: View,
        inAnimator: (View) -> ViewPropertyAnimator,
        outAnimator: (View) -> ViewPropertyAnimator
    ) {
        val outView = displayedChildView
        super.setDisplayedChild(indexOfChild(inView))

        if (outView != null) {
            inAnimator(inView)
                .setListener(
                    onStart = { inView.isVisible = true }
                )
                .start()
            outAnimator(outView)
                .setListener(
                    onStart = { outView.isVisible = true },
                    onEnd = { outView.isVisible = false }
                )
                .start()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // OG ViewFlipper animates view bitmaps instead of actual views,
        // where the displayed View is always at 0,0. This effectively means
        // that touch events are *always* received by the displayed child.
        ev.offsetLocation(displayedChildView!!.translationX, 0f)
        return super.dispatchTouchEvent(ev)
    }
}

private fun ViewPropertyAnimator.setListener(
    onEnd: () -> Unit = {},
    onStart: () -> Unit = {}
): ViewPropertyAnimator {
    setListener(object : AnimatorListener {
        override fun onAnimationRepeat(animator: Animator) = Unit
        override fun onAnimationCancel(animator: Animator) = Unit
        override fun onAnimationEnd(animator: Animator) = onEnd()
        override fun onAnimationStart(animator: Animator) = onStart()
    })
    return this
}