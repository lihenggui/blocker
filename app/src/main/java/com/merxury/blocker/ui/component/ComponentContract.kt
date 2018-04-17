package com.merxury.blocker.ui.component

import android.content.pm.ComponentInfo
import com.merxury.blocker.core.IController
import com.merxury.blocker.ui.base.BasePresenter
import com.merxury.blocker.ui.base.BaseView
import com.merxury.blocker.ui.strategy.entity.view.AppComponentInfo

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

    interface ComponentDataPresenter : BasePresenter {
        fun getComponentData(packageName: String): AppComponentInfo
        fun loadComponentData(packageName: String)
        fun refreshComponentData(packageName: String)
    }

    interface ComponentMainView {
        fun onComponentLoaded(appComponentInfo: AppComponentInfo)
        fun getComponentDataPresenter(): ComponentDataPresenter
    }

    interface ComponentItemListener {
        fun onComponentClick(component: ComponentInfo)
        fun onComponentLongClick(component: ComponentInfo)
        fun onSwitchClick(component: ComponentInfo, isChecked: Boolean)
        fun onUpVoteClick(component: ComponentInfo)
        fun onDownVoteClick(component: ComponentInfo)
    }
}