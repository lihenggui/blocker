/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.helpandfeedback

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import timber.log.Timber

private const val PROJECT_HOME_URL = "https://github.com/lihenggui/blocker"
private const val GROUP_URL = "https://t.me/blockerandroid"
private const val RULE_REPO_URL = "https://github.com/lihenggui/blocker-general-rules"

@HiltViewModel
class SupportFeedbackViewModel @Inject constructor() : ViewModel() {

    fun openProjectHomepage(context: Context) = openUrl(context, PROJECT_HOME_URL)

    fun openGroupLink(context: Context) = openUrl(context, GROUP_URL)

    fun openRulesRepository(context: Context) = openUrl(context, RULE_REPO_URL)

    fun exportErrorLog() {
        // TODO
    }

    private fun openUrl(context: Context, url: String) {
        val chromeIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        val resolveInfo = getCustomTabsPackages(context)
        if (resolveInfo.isNotEmpty()) {
            Timber.i("Open url in Chrome Tabs $url")
            chromeIntent.launchUrl(context, Uri.parse(url))
        } else {
            val browseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (browseIntent.resolveActivity(context.packageManager) != null) {
                Timber.i("Open url in default browser $url")
                context.startActivity(browseIntent)
            } else {
                Timber.w("No browser to open url $url")
            }
        }
    }

    /**
     * Returns a list of packages that support Custom Tabs.
     */
    private fun getCustomTabsPackages(context: Context): ArrayList<ResolveInfo> {
        val pm = context.packageManager
        // Get default VIEW intent handler.
        val activityIntent = Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("http", "", null))

        // Get all apps that can handle VIEW intents.
        val resolvedActivityList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(activityIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(activityIntent, 0)
        }
        val packagesSupportingCustomTabs: ArrayList<ResolveInfo> = ArrayList()
        for (info in resolvedActivityList) {
            val serviceIntent = Intent()
            serviceIntent.action = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
            serviceIntent.setPackage(info.activityInfo.packageName)
            // Check if this package also resolves the Custom Tabs service.
            val service = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.resolveService(serviceIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.resolveService(serviceIntent, 0)
            }
            if (service != null) {
                packagesSupportingCustomTabs.add(info)
            }
        }
        return packagesSupportingCustomTabs
    }
}
