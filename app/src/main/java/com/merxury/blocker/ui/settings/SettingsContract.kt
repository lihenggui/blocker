package com.merxury.blocker.ui.settings

import com.merxury.blocker.base.BasePresenter
import com.merxury.blocker.base.BaseView

interface SettingsContract : BaseView<SettingsContract.SettingsPresenter> {
    interface SettingsView {
        fun showExportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
        fun showImportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
    }

    interface SettingsPresenter : BasePresenter {
        fun exportAllRules(folder: String)
        fun importAllRules(folder: String)
        fun exportAllIFWRules(folder: String)
        fun importAllIFWRules(folder: String)
        fun resetIFW()
    }
}