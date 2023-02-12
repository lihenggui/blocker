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

package com.merxury.blocker.feature.search.model

import android.content.pm.PackageInfo
import com.merxury.blocker.core.model.data.InstalledApp
import kotlinx.datetime.Instant

data class InstalledAppItem(
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val firstInstallTime: Instant? = null,
    val lastUpdateTime: Instant? = null,
    val isEnabled: Boolean = true,
    val isSystem: Boolean = false,
    val label: String = "",
    val packageInfo: PackageInfo? = null,
)

fun InstalledApp.toInstalledAppItem(packageInfo: PackageInfo?) = InstalledAppItem(
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
    packageInfo = packageInfo,
)
