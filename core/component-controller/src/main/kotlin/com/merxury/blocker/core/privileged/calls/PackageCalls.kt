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

package com.merxury.blocker.core.privileged.calls

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.privileged.PrivilegedAction
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.UserIdParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * Every package installed for [user], with flags 0, as `PackageManager.getInstalledPackages`
 * gives it. Throws IllegalArgumentException for a user that does not exist: the system would
 * answer that with an empty list.
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class InstalledPackages(val user: UserId) : PrivilegedCall<List<PackageInfo>> {
    override fun run(system: SystemServices): List<PackageInfo> {
        requireUser(system, user)
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            system.packageManager.getInstalledPackages(0L, user.value)
        } else {
            system.packageManager.getInstalledPackages(0, user.value)
        }
        return packages.list
    }
}

/**
 * [packageName] as installed for [user].
 *
 * @param flags `PackageManager.GET_*` and `MATCH_*` flags
 * @throws NameNotFoundException when it is not installed for [user]
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class GetPackageInfo(
    val packageName: String,
    val flags: Int,
    val user: UserId,
) : PrivilegedCall<PackageInfo> {
    override fun run(system: SystemServices): PackageInfo = packageInfo(system, packageName, flags, user)
        ?: throw NameNotFoundException("$packageName is not installed for user ${user.value}")
}

/**
 * The enabled setting of each app in [packageNames] for [user], in one call.
 *
 * The result has one entry per package name, in order: a `PackageManager.COMPONENT_ENABLED_STATE_*`
 * value, or null when that setting cannot be read, which is how an app that is not installed for
 * [user] shows. One such name does not fail the rest.
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ApplicationEnabledSettings(
    val packageNames: List<String>,
    val user: UserId,
) : PrivilegedCall<List<Int?>> {
    override fun run(system: SystemServices): List<Int?> = packageNames.map { packageName ->
        try {
            system.packageManager.getApplicationEnabledSetting(packageName, user.value).takeIf { it in APP_SETTINGS }
        } catch (_: IllegalArgumentException) {
            // The system's answer for an app the user does not have.
            null
        }
    }
}

/**
 * Sets the enabled setting of the whole app [packageName] for [user]. The shell may do this too.
 *
 * @param newState `COMPONENT_ENABLED_STATE_DEFAULT`, `_ENABLED`, `_DISABLED`, `_DISABLED_USER` or
 * `_DISABLED_UNTIL_USED`
 */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class SetApplicationEnabledSetting(
    val packageName: String,
    val newState: Int,
    val user: UserId,
) : PrivilegedAction {
    init {
        require(newState in APP_SETTINGS) { "Not an app enabled setting: $newState" }
    }

    override fun run(system: SystemServices) {
        system.packageManager.setApplicationEnabledSetting(
            packageName,
            newState,
            0,
            user.value,
            system.callerPackage,
        )
    }
}

/** Stops every process of [packageName] in [user], as "Force stop" in system settings does. */
@Parcelize
@TypeParceler<UserId, UserIdParceler>
internal data class ForceStop(
    val packageName: String,
    val user: UserId,
) : PrivilegedAction {
    override fun run(system: SystemServices) {
        system.activityManager.forceStopPackage(packageName, user.value)
    }
}

/** The package as installed for [user], or null when it is not installed for that user. */
internal fun packageInfo(system: SystemServices, packageName: String, flags: Int, user: UserId): PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    system.packageManager.getPackageInfo(packageName, flags.toLong(), user.value)
} else {
    system.packageManager.getPackageInfo(packageName, flags, user.value)
}

/** @throws IllegalArgumentException when [user] does not exist */
internal fun requireUser(system: SystemServices, user: UserId) {
    requireNotNull(system.userManager.getUserInfo(user.value)) { "No user ${user.value}" }
}

private val APP_SETTINGS = setOf(
    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
)
