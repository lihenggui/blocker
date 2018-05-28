package com.merxury.blocker.ui.component

import com.merxury.blocker.core.IController
import com.merxury.blocker.ui.base.BasePresenter
import com.merxury.blocker.ui.base.BaseView
import com.merxury.blocker.ui.strategy.entity.view.AppComponentInfo

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
        fun showAlertDialog()
        fun refreshComponentState(componentName: String)
        fun showAddComment(packageName: String, componentName: String)
        fun showVoteFail()
        fun showDisableAllAlert()
        fun showActionDone()
        fun showActionFail()
    }

    interface Presenter : BasePresenter, IController {
        var currentComparator: EComponentComparatorType
        fun loadComponents(packageName: String, type: EComponentType)
        fun sortComponentList(components: List<ComponentItemViewModel>, type: EComponentComparatorType): List<ComponentItemViewModel>
        fun checkComponentIsUpVoted(packageName: String, componentName: String): Boolean
        fun checkComponentIsDownVoted(packageName: String, componentName: String): Boolean
        fun voteForComponent(packageName: String, componentName: String, type: EComponentType)
        fun downVoteForComponent(packageName: String, componentName: String, type: EComponentType)
        fun writeComponentVoteState(packageName: String, componentName: String, like: Boolean)
        fun addToIFW(packageName: String, componentName: String, type: EComponentType)
        fun removeFromIFW(packageName: String, componentName: String, type: EComponentType)
        fun launchActivity(packageName: String, componentName: String)
        fun checkIFWState(packageName: String, componentName: String): Boolean
        fun getComponentViewModel(packageName: String, componentName: String): ComponentItemViewModel
        fun updateComponentViewModel(viewModel: ComponentItemViewModel)
        fun disableAllComponents(packageName: String, type: EComponentType)
        fun enableAllComponents(packageName: String, type: EComponentType)
    }

    interface ComponentOnlineDataPresenter : BasePresenter {
        val packageName: String
        fun getComponentData(): AppComponentInfo
        fun loadComponentData()
        fun refreshComponentData()
        fun sendDescription(packageName: String, componentName: String, type: EComponentType, description: String)
    }

    interface ComponentMainView {
        fun onComponentLoaded(appComponentInfo: AppComponentInfo)
        fun getComponentDataPresenter(): ComponentOnlineDataPresenter
    }

    interface ComponentItemListener {
        fun onComponentClick(name: String)
        fun onComponentLongClick(name: String)
        fun onSwitchClick(name: String, isChecked: Boolean)
        fun onUpVoteClick(name: String)
        fun onDownVoteClick(name: String)
    }
}