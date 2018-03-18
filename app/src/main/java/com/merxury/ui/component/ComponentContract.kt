package com.merxury.ui.component

import android.content.Context
import com.merxury.ui.base.BasePresenter
import com.merxury.ui.base.BaseView

/**
 * Created by Mercury on 2018/3/18.
 */
interface ComponentContract {
    interface Presenter : BasePresenter {
        fun setLoadingIndicator(active: Boolean)
        fun showComponents()
        fun searchForComponent()
        fun showNoComponent()
        fun showFilteringPopUpMenu()
    }

    interface View : BaseView<Presenter> {
        fun loadComponentList(context: Context)
    }
}