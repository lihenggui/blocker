package com.merxury.ui.home

import android.content.Context
import com.merxury.entity.Application
import com.merxury.ui.base.BasePresenter
import com.merxury.ui.base.BaseView

/**
 * @author Mercury
 * This interface specifies the contract between the view and the presenter
 */

interface HomeContract {
    interface View : BaseView<Presenter> {
        var isActive: Boolean
        fun setLoadingIndicator(active: Boolean)
        fun searchForApplication(name: String)
        fun showApplicationList(applications: List<Application>)
        fun showNoApplication()
        fun showFilteringPopUpMenu()
        fun showApplicationDetailsUi(application: Application)
    }

    interface Presenter : BasePresenter {
        var currentComparator: ApplicationComparatorType
        fun loadApplicationList(context: Context, isSystemApplication: Boolean)
        fun openApplicationDetails(application: Application)
        fun result(requestCode: Int, resultCode: Int)
    }
}