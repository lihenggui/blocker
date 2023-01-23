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

package com.merxury.blocker.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import com.elvishew.xlog.XLog
import com.merxury.blocker.R

object BrowserUtil {
    private val logger = XLog.tag("BrowserUtil")

    fun openUrl(context: Context, url: String) {
        val chromeIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        val resolveInfo = getCustomTabsPackages(context)
        if (resolveInfo.isNotEmpty()) {
            logger.i("Open url in Chrome Tabs $url")
            chromeIntent.launchUrl(context, Uri.parse(url))
        } else {
            val browseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (browseIntent.resolveActivity(context.packageManager) != null) {
                logger.i("Open url in default browser $url")
                context.startActivity(browseIntent)
            } else {
                logger.w("No browser to open url $url")
                ToastUtil.showToast(context.getString(R.string.browser_required))
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
            serviceIntent.action = ACTION_CUSTOM_TABS_CONNECTION
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
