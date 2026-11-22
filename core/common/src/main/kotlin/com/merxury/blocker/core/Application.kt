/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlin.time.Instant

/**
 * Created by Mercury on 2017/12/30.
 * An entity class that describe simplified application information
 */
data class Application(
    val packageName: String = "",
    val versionName: String? = "",
    val versionCode: Long = 0L,
    val isEnabled: Boolean = false,
    val label: String = "",
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val firstInstallTime: Instant? = null,
    val lastUpdateTime: Instant? = null,
    val packageInfo: PackageInfo? = null,
)

suspend fun PackageInfo.toApplication(pm: PackageManager): Application = Application(
    packageName = packageName,
    versionName = versionName,
    versionCode = getVersionCode(),
    isEnabled = applicationInfo?.enabled ?: false,
    label = applicationInfo?.loadLabel(pm).toString(),
    minSdkVersion = applicationInfo?.minSdkVersion ?: 0,
    targetSdkVersion = applicationInfo?.targetSdkVersion ?: 0,
    firstInstallTime = Instant.fromEpochMilliseconds(firstInstallTime),
    lastUpdateTime = Instant.fromEpochMilliseconds(lastUpdateTime),
    packageInfo = this,
)

@Suppress("DEPRECATION")
private fun PackageInfo.getVersionCode(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    longVersionCode
} else {
    versionCode.toLong()
}
