/*
 * Copyright 2023 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

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
            file,
        )
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri)
        val chooserIntent =
            Intent.createChooser(emailIntent, context.getString(R.string.send_email))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val resInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    chooserIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(
                    chooserIntent,
                    PackageManager.MATCH_ALL,
                )
            }
            resInfoList.forEach {
                val packageName = it.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        context.startActivity(chooserIntent)
    }
}
