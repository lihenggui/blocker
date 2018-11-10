package com.merxury.blocker.ui.component

import android.support.annotation.StringRes
import com.merxury.blocker.base.BasePresenter
import com.merxury.blocker.base.BaseView
import com.merxury.blocker.core.IController

/**
 * Created by Mercury on 2018/3/18.
 */
interface ComponentContract {
    interface View : BaseView<Presenter> {
        fun showComponentList(components: MutableList<ComponentItemViewModel>)
        fun setLoadingIndicator(active: Boolean)
        fun showNoComponent()
        fun searchForComponent(name: String)
        fun showFilteringPopUpMenu()
        fun showAlertDialog(message: String?)
        fun refreshComponentState(componentName: String)
        fun showDisableAllAlert()
        fun showActionDone()
        fun showActionFail()
        fun showImportFail()
        fun showAlert(@StringRes alertMessage: Int, confirmAction: () -> Unit)
        fun showError(@StringRes errorMessage: Int)
        fun showToastMessage(message: String?, length: Int)
    }

    interface Presenter : BasePresenter, IController {
        var currentComparator: EComponentComparatorType
        fun loadComponents(packageName: String, type: EComponentType)
        fun sortComponentList(components: List<ComponentItemViewModel>, type: EComponentComparatorType): List<ComponentItemViewModel>
        fun addToIFW(packageName: String, componentName: String, type: EComponentType)
        fun removeFromIFW(packageName: String, componentName: String, type: EComponentType)
        fun launchActivity(packageName: String, componentName: String)
        fun checkIFWState(packageName: String, componentName: String): Boolean
        fun getComponentViewModel(packageName: String, componentName: String): ComponentItemViewModel
        fun updateComponentViewModel(viewModel: ComponentItemViewModel)
        fun disableAllComponents(packageName: String, type: EComponentType)
        fun enableAllComponents(packageName: String, type: EComponentType)
        fun exportRule(packageName: String)
        fun importRule(packageName: String)
        fun isServiceRunning(componentName: String): Boolean
    }

    interface ComponentItemListener {
        fun onComponentClick(name: String)
        fun onComponentLongClick(name: String)
        fun onSwitchClick(name: String, isChecked: Boolean)
    }
}