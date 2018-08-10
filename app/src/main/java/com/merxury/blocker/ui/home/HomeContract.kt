package com.merxury.blocker.ui.home

import android.content.Context
import android.support.annotation.StringRes
import com.merxury.blocker.base.BasePresenter
import com.merxury.blocker.base.BaseView
import com.merxury.libkit.entity.Application

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
        fun showAlert(@StringRes alertMessage: Int, confirmAction:() -> Unit)
        fun showError(@StringRes errorMessage:Int)
        fun showToastMessage(message: String?, length: Int)
    }

    interface Presenter : BasePresenter {
        var currentComparator: ApplicationComparatorType
        fun loadApplicationList(context: Context, isSystemApplication: Boolean)
        fun openApplicationDetails(application: Application)
        fun result(requestCode: Int, resultCode: Int)
        fun sortApplicationList(applications: List<Application>): List<Application>
        fun exportIfwRules()
        fun importIfwRules()
    }
}