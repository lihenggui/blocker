package com.merxury.blocker.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.merxury.blocker.R
import java.io.File

object ShareUtil {
    fun shareFileToEmail(context: Context, file: File) {
        val emailIntent = Intent(Intent.ACTION_SEND)
            .setType("vnd.android.cursor.dir/email")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf("mercuryleee@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.report_subject_template))
            .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.report_content_template))
        val uri = FileProvider.getUriForFile(
            context,
            "com.merxury.blocker.provider",
            file
        )
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri)
        val chooserIntent =
            Intent.createChooser(emailIntent, context.getString(R.string.send_email))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // TODO: Deprecated in API 33, will fix later
            val resInfoList = context.packageManager.queryIntentActivities(
                chooserIntent,
                PackageManager.MATCH_ALL
            )
            resInfoList.forEach {
                val packageName = it.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        context.startActivity(chooserIntent)
    }
}