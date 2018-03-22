package com.merxury.ui.component

import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.merxury.core.IController
import com.merxury.ui.base.BasePresenter
import com.merxury.ui.base.BaseView

/**
 * Created by Mercury on 2018/3/18.
 */
interface ComponentContract {
    interface ControllerAttachedView {
        fun getController(): IController
    }

    interface View : BaseView<Presenter> {
        fun showComponentList(components: List<ComponentInfo>)
        fun setLoadingIndicator(active: Boolean)
        fun showNoComponent()
        fun searchForComponent()
        fun showFilteringPopUpMenu()
    }

    interface Presenter : BasePresenter {
        fun loadComponents(pm: PackageManager, packageName: String, type: EComponentType)
    }
}