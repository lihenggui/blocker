package com.merxury.blocker.util

import android.content.Context
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import com.merxury.blocker.R

class DialogUtil {
    fun showWarningDialogWithMessage(context: Context?, e: Throwable) {
        context?.apply {
            val errorInfo = StringUtil.getStackTrace(e)
            AlertDialog.Builder(this)
                    .setTitle(getString(R.string.oops))
                    .setMessage(getString(R.string.no_root_error_message, errorInfo))
                    .setPositiveButton(R.string.close) { dialog: DialogInterface, _: Int -> dialog.dismiss() }
                    .show()
        }
    }
}
