package com.merxury.blocker.view

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.animation.doOnEnd
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentManager
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.merxury.blocker.R
import com.merxury.blocker.util.UiUtils

/**
 * Author: Absinthe
 * Package: com.absinthe.libraries.utils.base
 */
abstract class BaseBottomSheetViewDialogFragment<T : View> :
    BottomSheetDialogFragment(), View.OnLayoutChangeListener {

    var animationDuration = 350L
    var maxPeekSize: Int = 0

    private val TAG = "BaseBottomSheetViewDialogFragment"
    private var _root: T? = null
    private var isHandlerActivated = false
    private var animator: ValueAnimator = ObjectAnimator()
    private val behavior by lazy { BottomSheetBehavior.from(root.parent as View) }
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_DRAGGING -> {
                    if (!isHandlerActivated) {
                        isHandlerActivated = true
                        getHeaderView().onHandlerActivated(true)
                    }
                }
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    if (isHandlerActivated) {
                        isHandlerActivated = false
                        getHeaderView().onHandlerActivated(false)
                    }
                }
                BottomSheetBehavior.STATE_EXPANDED -> {
                    if (isHandlerActivated) {
                        isHandlerActivated = false
                        getHeaderView().onHandlerActivated(false)
                    }
                    bottomSheet.background = createMaterialShapeDrawable(bottomSheet)
                }
                else -> {
                }
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }
    }

    val root get() = _root!!

    abstract fun initRootView(): T
    abstract fun init()
    abstract fun getHeaderView(): BottomSheetHeaderView

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        object : BottomSheetDialog(requireContext(), theme) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()

                window?.let {
                    it.attributes?.windowAnimations = R.style.DialogAnimation
                    WindowCompat.setDecorFitsSystemWindows(it, false)
                    UiUtils.setSystemBarStyle(it)
                    WindowInsetsControllerCompat(it, it.decorView)
                        .isAppearanceLightNavigationBars = !UiUtils.isDarkMode()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        it.attributes.blurBehindRadius = 64
                        it.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    }
                }

                findViewById<View>(com.google.android.material.R.id.container)?.fitsSystemWindows = false
                findViewById<View>(com.google.android.material.R.id.coordinator)?.fitsSystemWindows = false
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _root = initRootView()
        init()
        return _root
    }

    override fun onStart() {
        super.onStart()
        behavior.addBottomSheetCallback(bottomSheetCallback)
        root.addOnLayoutChangeListener(this)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            root.post {
                Class.forName(behavior::class.java.name).apply {
                    getDeclaredMethod("setStateInternal", Int::class.java).apply {
                        isAccessible = true
                        invoke(behavior, BottomSheetBehavior.STATE_EXPANDED)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        behavior.removeBottomSheetCallback(bottomSheetCallback)
    }

    override fun onDetach() {
        animator.cancel()
        super.onDetach()
    }

    override fun onDestroyView() {
        animator.cancel()
        root.removeOnLayoutChangeListener(this)
        _root = null
        super.onDestroyView()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching {
            super.show(manager, tag)
        }.onFailure {
            Log.e(TAG, it.toString())
        }
    }

    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        if ((bottom - top) != (oldBottom - oldTop)) {
            enqueueAnimation {
                animateHeight(from = oldBottom - oldTop, to = bottom - top, onEnd = { })
            }
        }
    }

    private fun createMaterialShapeDrawable(bottomSheet: View): MaterialShapeDrawable {
        // Create a ShapeAppearanceModel with the same shapeAppearanceOverlay used in the style
        val shapeAppearanceModel =
            ShapeAppearanceModel.builder(context, 0, R.style.CustomShapeAppearanceBottomSheetDialog)
                .build()

        // Create a new MaterialShapeDrawable (you can't use the original MaterialShapeDrawable in the BottomSheet)
        val currentMaterialShapeDrawable = bottomSheet.background as MaterialShapeDrawable
        return MaterialShapeDrawable(shapeAppearanceModel).apply {
            // Copy the attributes in the new MaterialShapeDrawable
            initializeElevationOverlay(context)
            fillColor = currentMaterialShapeDrawable.fillColor
            tintList = currentMaterialShapeDrawable.tintList
            elevation = currentMaterialShapeDrawable.elevation
            strokeWidth = currentMaterialShapeDrawable.strokeWidth
            strokeColor = currentMaterialShapeDrawable.strokeColor
        }
    }

    private fun animateHeight(from: Int, to: Int, onEnd: () -> Unit) {
        animator.cancel()
        animator = ObjectAnimator.ofFloat(0f, 1f).apply {
            duration = animationDuration
            interpolator = FastOutSlowInInterpolator()
            Log.d(TAG, "animateHeight: $from -> $to")

            addUpdateListener {
                val scale = it.animatedValue as Float
                val newHeight = ((to - from) * scale + from).toInt()
                setClippedHeight(newHeight)
            }
            doOnEnd { onEnd() }
            start()
        }
    }

    private fun enqueueAnimation(action: () -> Unit) {
        if (!animator.isRunning) action()
        else animator.doOnEnd { action() }
    }

    private fun setClippedHeight(newHeight: Int) {
        if (newHeight <= maxPeekSize || maxPeekSize == 0) {
            behavior.peekHeight = newHeight
        }
    }
}
