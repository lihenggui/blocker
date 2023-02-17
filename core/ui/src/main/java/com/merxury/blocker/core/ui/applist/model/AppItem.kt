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

package com.merxury.blocker.core.ui.applist.model

import android.content.pm.PackageInfo
import com.merxury.blocker.core.model.data.InstalledApp
import kotlinx.datetime.Instant

/**
 * Data representation for the installed application.
 * App icon will be loaded by PackageName.
 */
data class AppItem(
    val label: String,
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val isSystem: Boolean = false,
    val isRunning: Boolean = false,
    val isEnabled: Boolean = true,
    val firstInstallTime: Instant? = null,
    val lastUpdateTime: Instant? = null,
    val appServiceStatus: AppServiceStatus? = null,
    val packageInfo: PackageInfo? = null,
)

fun InstalledApp.toAppItem(
    packageInfo: PackageInfo? = null,
    appServiceStatus: AppServiceStatus? = null,
    isRunning: Boolean = false,
) = AppItem(
    packageName = packageName,
    versionName = versionName,
    versionCode = versionCode,
    minSdkVersion = minSdkVersion,
    targetSdkVersion = targetSdkVersion,
    firstInstallTime = firstInstallTime,
    lastUpdateTime = lastUpdateTime,
    isEnabled = isEnabled,
    isSystem = isSystem,
    label = label,
    appServiceStatus = appServiceStatus,
    isRunning = isRunning,
    packageInfo = packageInfo,
)
