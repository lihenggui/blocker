package com.merxury.blocker.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import android.widget.ViewFlipper
import androidx.core.animation.doOnEnd
import androidx.core.graphics.withTranslation
import androidx.core.view.doOnLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Author: Abinsthe
 * package com.absinthe.libraries.utils.view
 *
 * A [ViewFlipper] that wraps its height to the currently
 * displayed child and smoothly animates height changes.
 *
 * See [show], [goForward] and [goBack].
 */
open class HeightAnimatableViewFlipper(context: Context) : ViewFlipper2(context) {
    var animationDuration = 350L
    var animationInterpolator = FastOutSlowInInterpolator()

    private var clipBounds2: Rect? = null // Because View#clipBounds creates a new Rect on every call.
    private var animator: ValueAnimator = ObjectAnimator()

    fun show(view: View) {
        enqueueAnimation {
            if (childCount == 1) {
                return@enqueueAnimation
            }

            val prevView = displayedChildView!!
            setDisplayedChild(
                view,
                inAnimator = {
                    it.alpha = 0f
                    it.animate()
                        .alpha(1f)
                        .setDuration(animationDuration)
                        .setInterpolator(animationInterpolator)
                },
                outAnimator = {
                    it.alpha = 1f
                    it.animate()
                        .alpha(0f)
                        .setDuration(animationDuration)
                        .setInterpolator(animationInterpolator)
                }
            )

            doOnLayout {
                animateHeight(
                    from = prevView.height + verticalPadding,
                    to = view.height + verticalPadding,
                    onEnd = { /*removeView(prevView)*/ }
                )
            }
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun enqueueAnimation(action: () -> Unit) {
        if (!animator.isRunning) action()
        else animator.doOnEnd { action() }
    }

    private fun animateHeight(from: Int, to: Int, onEnd: () -> Unit) {
        animator.cancel()
        animator = ObjectAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            interpolator = FastOutSlowInInterpolator()

            addUpdateListener {
                val scale = it.animatedValue as Float
                val newHeight = ((to - from) * scale + from).toInt()
                setClippedHeight(newHeight)
            }
            doOnEnd { onEnd() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private fun setClippedHeight(newHeight: Int) {
        clipBounds2 = (clipBounds2 ?: Rect()).also {
            it.set(0, 0, right - left, top + newHeight)
        }
        background()?.clippedHeight = newHeight
        invalidate()
    }

    private fun background(): HeightClipDrawable? {
        return background as HeightClipDrawable?
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (childCount > 1) {
            // When Views are animating, they'll overlap with each other. Re-draw this
            // layout's background behind each child so that they don't cross-draw.
            canvas.withTranslation(x = child.translationX) {
                background()?.draw(canvas)
            }
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (clipBounds2 != null && !clipBounds2!!.contains(ev)) return false
        return super.dispatchTouchEvent(ev)
    }
}

private fun Rect.contains(ev: MotionEvent): Boolean {
    return contains(ev.x.toInt(), ev.y.toInt())
}

private val View.verticalPadding: Int
    get() = paddingTop + paddingBottom