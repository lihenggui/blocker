package com.merxury.blocker.ui.detail.component.info

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.blocker.view.BaseBottomSheetViewDialogFragment
import com.merxury.blocker.view.BottomSheetHeaderView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ComponentDetailDialogFragment: BaseBottomSheetViewDialogFragment<ComponentDetailBottomSheetView>() {
    private val viewModel: ComponentDetailViewModel by viewModels()
    private val logger = XLog.tag("ComponentDetailDialogFragment")
    private var componentData: ComponentData? = null
    override fun initRootView(): ComponentDetailBottomSheetView {
        logger.i("initRootView")
        return ComponentDetailBottomSheetView(requireContext())
    }

    override fun init() {
        componentData = arguments?.getParcelable(ARG_COMPONENT_DATA)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listenToUiUpdate()
        componentData?.let {
            viewModel.getOnlineData(requireContext(), it)
        }
    }

    override fun getHeaderView(): BottomSheetHeaderView {
        return root.getHeaderView()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        if (!isShowing) {
            isShowing = true
            super.show(manager, tag)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        isShowing = false
    }

    private fun listenToUiUpdate() {
        lifecycleScope.launchWhenStarted {
            viewModel.onlineData.collect {
                if (it == null) return@collect
            }
        }
    }

    companion object {
        const val ARG_COMPONENT_DATA = "arg_component_data"
        fun newInstance(component: ComponentData): ComponentDetailDialogFragment {
            return ComponentDetailDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_COMPONENT_DATA, component)
                }
            }
        }
        var isShowing = false
    }
}