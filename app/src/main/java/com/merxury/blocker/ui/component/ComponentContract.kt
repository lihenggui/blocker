package com.merxury.blocker.ui.component

import android.content.pm.ComponentInfo
import com.merxury.blocker.core.IController
import com.merxury.blocker.ui.base.BasePresenter
import com.merxury.blocker.ui.base.BaseView

/**
 * Created by Mercury on 2018/3/18.
 */
interface ComponentContract {
    interface View : BaseView<Presenter> {
        fun showComponentList(components: List<ComponentInfo>)
        fun setLoadingIndicator(active: Boolean)
        fun showNoComponent()
        fun searchForComponent(name: String)
        fun showFilteringPopUpMenu()
        fun showAlertDialog()
        fun refreshComponentSwitchState(componentName: String)
    }

    interface Presenter : BasePresenter, IController {
        var currentComparator: EComponentComparatorType
        fun loadComponents(packageName: String, type: EComponentType)
        fun sortComponentList(components: List<ComponentInfo>, type: EComponentComparatorType): List<ComponentInfo>
    }
}