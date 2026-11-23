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

package com.merxury.blocker.core.system

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser

/**
 * The raw PackageManager calls [PlatformSystemPackageReader] is built on, with the user made
 * explicit. An implementation decides which users it can reach: the app's own PackageManager
 * reaches one, a privileged service reaches all of them.
 *
 * Calls block and may throw [RuntimeException] when the system does not answer.
 */
interface PlatformPackages {
    fun users(): List<DeviceUser>

    @Throws(UserNotReachableException::class)
    fun installedPackages(user: UserId): List<PackageInfo>

    /** @param flags `PackageManager.GET_*` and `MATCH_*` flags */
    @Throws(NameNotFoundException::class, UserNotReachableException::class)
    fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo

    /** Parses the APK at [apkPath] in this process, or returns null when it cannot be parsed. */
    fun archiveInfo(apkPath: String, flags: Int): PackageInfo?

    /**
     * @return a `PackageManager.COMPONENT_ENABLED_STATE_*` value
     * @throws NameNotFoundException when the app is not installed for [user]
     */
    @Throws(NameNotFoundException::class, UserNotReachableException::class)
    fun applicationEnabledSetting(packageName: String, user: UserId): Int

    /**
     * The PackageManager setting of each of [classNames], components of [packageName], for [user],
     * in one request: through a privileged service every request is a round trip, and an app can
     * declare thousands of components.
     *
     * @return one entry per class name, in the same order: `COMPONENT_ENABLED_STATE_DEFAULT`,
     * `COMPONENT_ENABLED_STATE_ENABLED` or `COMPONENT_ENABLED_STATE_DISABLED`, or null when that
     * class's setting cannot be read, for example because the system rejects the name or holds a
     * value a component cannot have. A null never stands for "no override".
     * @throws NameNotFoundException when the app is not installed for [user]
     */
    @Throws(NameNotFoundException::class, UserNotReachableException::class)
    fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId): List<Int?>

    fun label(application: ApplicationInfo): String
}

/** [user] exists or may exist, but this [PlatformPackages] cannot read it. */
class UserNotReachableException(val user: UserId) : Exception("User ${user.value} is not reachable")
