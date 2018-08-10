package com.merxury.blocker.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.merxury.blocker.core.R

object AppLauncher {
    fun startApplication(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, context.getString(R.string.app_cannot_start), Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}