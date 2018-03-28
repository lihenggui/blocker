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
    interface View : BaseView<Presenter> {
        fun showComponentList(components: List<ComponentInfo>)
        fun setLoadingIndicator(active: Boolean)
        fun showNoComponent()
        fun searchForComponent()
        fun showFilteringPopUpMenu()
        fun setSwitchEnableState(view: View, enabled: Boolean)
        fun showAlertDialog()
    }

    interface Presenter : BasePresenter, IController {
        var currentComparator: EComponentComparatorType
        fun loadComponents(pm: PackageManager, packageName: String, type: EComponentType)
        fun sortComponentList(components: List<ComponentInfo>, type: EComponentComparatorType): List<ComponentInfo>
    }
}