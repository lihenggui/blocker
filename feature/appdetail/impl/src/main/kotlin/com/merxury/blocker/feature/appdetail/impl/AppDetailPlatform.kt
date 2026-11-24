/*
 * Copyright 2026 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.feature.appdetail.impl

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.core.content.FileProvider
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.feature.appdetail.api.R.string
import timber.log.Timber

/*
 * Platform actions of the detail screen that only start another screen; they need a Context, so
 * they stay in the UI layer.
 */

/**
 * Opens [app] as the launcher does: through its launch intent in the user Blocker runs in, or
 * through the launcher service in a profile of Blocker's user.
 *
 * @return false when the app cannot be opened that way
 */
internal fun Context.launchApp(app: HeaderApp, mode: LaunchMode): Boolean = when (mode) {
    LaunchMode.OWN_USER -> packageManager.getLaunchIntentForPackage(app.packageName)?.let { intent ->
        startActivity(intent)
        true
    } ?: false

    LaunchMode.PROFILE -> launchInProfile(app)

    LaunchMode.NONE -> false
}

private fun Context.launchInProfile(app: HeaderApp): Boolean {
    val uid = app.uid ?: return false
    val user = UserHandle.getUserHandleForUid(uid)
    val launcherApps = getSystemService(LauncherApps::class.java) ?: return false
    return try {
        val activity = launcherApps.getActivityList(app.packageName, user).firstOrNull() ?: return false
        launcherApps.startMainActivity(activity.componentName, user, null, null)
        true
    } catch (e: SecurityException) {
        Timber.w(e, "Cannot open ${app.packageName} in $user")
        false
    } catch (e: ActivityNotFoundException) {
        Timber.w(e, "Cannot open ${app.packageName} in $user")
        false
    }
}

/** Shows [packageName] in LibChecker, which reads apps of the user Blocker runs in. */
internal fun Context.showInLibChecker(packageName: String) {
    val intent = Intent(Intent.ACTION_SHOW_APP_INFO).apply {
        putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
        setPackage(LIBCHECKER_PACKAGE_NAME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "LibChecker is not installed")
    }
}

/**
 * Offers [rule] to the apps that can send it by mail.
 *
 * @return the message to show when it cannot be shared, or null once the chooser opened
 */
@SuppressLint("WrongConstant", "QueryPermissionsNeeded")
internal fun Context.shareRules(rule: ZippedRule): String? {
    val zippedFile = rule.zippedFile ?: return getString(string.feature_appdetail_api_cannot_share_rule_report_issue)
    val uri = FileProvider.getUriForFile(this, "$packageName.FileProvider", zippedFile)
    val appPackageName = rule.packageName ?: getString(string.feature_appdetail_api_all_rules)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        clipData = ClipData.newRawUri("", uri)
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, getString(string.feature_appdetail_api_rules_sharing_title, appPackageName))
        putExtra(Intent.EXTRA_TEXT, getString(string.feature_appdetail_api_provide_additional_details))
        putExtra(Intent.EXTRA_EMAIL, arrayOf("mercuryleee@gmail.com"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooserIntent = Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    chooserIntent.resolveActivityInfo(packageManager, intent.flags)
        ?: return getString(string.feature_appdetail_api_cannot_share_rule_no_apps_found)
    startActivity(chooserIntent)
    return null
}
