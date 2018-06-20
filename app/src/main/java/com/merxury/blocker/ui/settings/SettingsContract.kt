package com.merxury.blocker.ui.settings

import com.merxury.blocker.base.BasePresenter
import com.merxury.blocker.base.BaseView

interface SettingsContract : BaseView<SettingsContract.SettingsPresenter> {
    interface SettingsView {
        fun showExportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
        fun showImportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
    }

    interface SettingsPresenter : BasePresenter {
        fun exportAllRules()
        fun importAllRules()
        fun exportAllIfwRules(folder: String)
        fun importAllIfwRules(folder: String)
        fun importMatRules()
        fun resetIFW()
    }
}