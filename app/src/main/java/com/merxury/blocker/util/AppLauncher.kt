package com.merxury.blocker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.merxury.blocker.R

object AppLauncher {
    fun startApplication(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(context, context.getString(R.string.app_cannot_start), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, context.getString(R.string.starting_application), Toast.LENGTH_SHORT).show()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun showApplicationDetails(context: Context, packageName: String) {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", packageName, null)
        context.startActivity(intent)
    }
}