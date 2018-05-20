package com.merxury.blocker.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.merxury.blocker.core.R

class ApplicationUtils {
    companion object {
        private const val MARKET_URL = "market://details?id="
        fun startApplication(context: Context, packageName: String) {
            val intent: Intent? = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent == null) {
                showToastForShortTime(context, context.getString(R.string.app_cannot_start))
                return
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun showToastForShortTime(context: Context, info: String) {
            Toast.makeText(context, info, Toast.LENGTH_SHORT).show()
        }

        fun showToastForLongTime(context: Context, info: String) {
            Toast.makeText(context, info, Toast.LENGTH_LONG).show()
        }
    }
}