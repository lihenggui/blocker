package com.merxury.blocker.ui.settings

import androidx.annotation.StringRes
import com.merxury.blocker.base.BasePresenter
import com.merxury.blocker.base.BaseView

interface SettingsContract : BaseView<SettingsContract.SettingsPresenter> {
    interface SettingsView {
        fun showExportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
        fun showImportResult(isSucceed: Boolean, successfulCount: Int, failedCount: Int)
        fun showResetResult(isSucceed: Boolean)
        fun showMessage(@StringRes res: Int)
        fun showDialog(@StringRes title: String, @StringRes message: String, action: () -> Unit)
        fun showDialog(title: String, message: String, file: String?, action: (file: String?) -> Unit)
    }

    interface SettingsPresenter : BasePresenter {
        fun exportAllRules()
        fun importAllRules()
        fun exportAllIfwRules()
        fun importAllIfwRules()
        fun importMatRules(filePath: String?)
        fun resetIFW()
    }
}