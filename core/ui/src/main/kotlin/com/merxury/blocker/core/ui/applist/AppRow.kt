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

package com.merxury.blocker.core.ui.applist

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ServiceCounts
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.ui.coil.ApkIcon

/**
 * One app in a list of apps.
 *
 * @property isDisabled whether the app is disabled for its user, by its own manifest or by a
 * setting
 * @property isRunning whether a process of the app runs in its user; null when that cannot be read
 * @property services the app's service counts; null while they are not read or cannot be
 */
data class AppRow(
    val key: AppKey,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val icon: ApkIcon,
    val isDisabled: Boolean,
    val isRunning: Boolean?,
    val services: ServiceCounts?,
)

/**
 * The rows for [apps] of one user: system apps only when the user shows them, sorted as the user
 * chose, with the apps the user picked kept on top.
 *
 * @param running the package names running in that user, or null when that cannot be read
 * @param self Blocker in the user it runs in, which is left out: changing it from here could only
 * break the app in use. A copy of Blocker in another user is listed like any app.
 */
fun appRows(
    apps: List<InstalledApp>,
    running: Set<String>?,
    services: Map<AppKey, ServiceCounts>,
    preferences: UserPreferenceData,
    self: AppKey,
): List<AppRow> = apps
    .filter { it.key != self && (preferences.showSystemApps || !it.isSystem) }
    .sortedWith(appOrder(preferences.appSorting, preferences.appSortingOrder))
    .map { app ->
        AppRow(
            key = app.key,
            label = app.label,
            versionName = app.versionName,
            versionCode = app.versionCode,
            icon = ApkIcon(app.key.packageName, app.apkPath, app.versionCode, app.uid),
            isDisabled = !app.enabled,
            isRunning = running?.let { app.key.packageName in it },
            services = if (preferences.showServiceInfo) services[app.key] else null,
        )
    }
    .let { rows ->
        // Stable sorts, so the chosen order holds within each group.
        when (preferences.topAppType) {
            TopAppType.NONE -> rows
            TopAppType.RUNNING -> rows.sortedByDescending { it.isRunning == true }
            TopAppType.DISABLED -> rows.sortedByDescending { it.isDisabled }
        }
    }

private fun appOrder(sorting: AppSorting, order: SortingOrder): Comparator<InstalledApp> {
    val ascending: Comparator<InstalledApp> = when (sorting) {
        AppSorting.NAME -> compareBy { it.label.lowercase() }
        AppSorting.FIRST_INSTALL_TIME -> compareBy { it.firstInstallTime }
        AppSorting.LAST_UPDATE_TIME -> compareBy { it.lastUpdateTime }
    }
    return if (order == SortingOrder.ASCENDING) ascending else ascending.reversed()
}
