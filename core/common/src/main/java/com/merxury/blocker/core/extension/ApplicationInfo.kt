/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.extension

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))

    else -> @Suppress("DEPRECATION") getPackageInfo(packageName, flags)
}

fun PackageManager.getApplicationInfoCompat(packageName: String, flags: Int): ApplicationInfo =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))

        else -> @Suppress("DEPRECATION") getApplicationInfo(packageName, flags)
    }

@SuppressLint("QueryPermissionsNeeded")
fun PackageManager.getInstalledPackagesCompat(flags: Int): List<PackageInfo> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))

    else -> @Suppress("DEPRECATION") getInstalledPackages(flags)
}
