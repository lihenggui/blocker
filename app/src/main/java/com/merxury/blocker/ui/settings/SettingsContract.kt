package com.merxury.blocker.ui.settings

import com.merxury.blocker.base.BasePresenter
import com.merxury.blocker.base.BaseView

interface SettingsContract : BaseView<SettingsContract.SettingsPresenter> {
    interface SettingsView {
        fun showExportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
        fun showImportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
        fun showResetResult(isSucceed: Boolean)
    }

    interface SettingsPresenter : BasePresenter {
        fun exportAllRules()
        fun importAllRules()
        fun exportAllIfwRules()
        fun importAllIfwRules()
        fun importMatRules()
        fun resetIFW()
    }
}