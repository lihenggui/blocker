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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.utils.currentUserId
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * [PlatformPackages] over the app's own PackageManager, which needs no privilege and reaches only
 * the user Blocker runs in.
 */
class LocalPlatformPackages @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlatformPackages {

    private val pm: PackageManager = context.packageManager

    private val ownUser = currentUserId()

    override suspend fun users(): List<DeviceUser> = listOf(
        DeviceUser(
            id = ownUser,
            // Reading the name needs a permission only system apps hold.
            name = null,
            kind = ownUserKind(),
            parent = null,
            running = true,
            unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked,
        ),
    )

    @SuppressLint("QueryPermissionsNeeded")
    override suspend fun installedPackages(user: UserId): List<PackageInfo> {
        requireOwn(user)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }
    }

    // Lint does not accept a computed Int as a flag set.
    @SuppressLint("WrongConstant")
    override suspend fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo {
        requireOwn(user)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }
    }

    @SuppressLint("WrongConstant")
    override fun archiveInfo(apkPath: String, flags: Int): PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(apkPath, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        pm.getPackageArchiveInfo(apkPath, flags)
    }

    override suspend fun applicationEnabledSettings(packageNames: List<String>, user: UserId): List<Int?> {
        requireOwn(user)
        return packageNames.map { packageName ->
            try {
                pm.getApplicationEnabledSetting(packageName).takeIf { it in APP_SETTINGS }
            } catch (e: IllegalArgumentException) {
                // How PackageManager reports an app that is not installed.
                Timber.w(e, "No enabled setting for $packageName")
                null
            }
        }
    }

    override suspend fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId): List<Int?> {
        requireOwn(user)
        // Without this a missing app would look like an app whose settings are all unreadable.
        packageInfo(packageName, 0, user)
        return classNames.map { className ->
            try {
                pm.getComponentEnabledSetting(ComponentName(packageName, className))
                    .takeIf { it in COMPONENT_SETTINGS }
            } catch (e: IllegalArgumentException) {
                // Android 9 and 14 answer DEFAULT for an undeclared name; this covers a release that refuses.
                Timber.w(e, "No setting for $packageName/$className")
                null
            }
        }
    }

    override fun label(application: ApplicationInfo): String = application.loadLabel(pm).toString()

    private fun requireOwn(user: UserId) {
        if (user != ownUser) throw UserNotReachableException(user)
    }

    /**
     * An app can tell that it runs in a work profile from Android 11 and in another kind of
     * profile from Android 13. Before that a profile is reported as [UserKind.SECONDARY].
     */
    private fun ownUserKind(): UserKind {
        if (ownUser == UserId.SYSTEM) return UserKind.OWNER
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return UserKind.SECONDARY
        }
        return when {
            userManager.isManagedProfile -> UserKind.WORK_PROFILE

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && userManager.isProfile ->
                UserKind.OTHER_PROFILE

            else -> UserKind.SECONDARY
        }
    }

    private companion object {
        // The only values PackageManager stores for a component.
        val COMPONENT_SETTINGS = setOf(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        )

        val APP_SETTINGS = COMPONENT_SETTINGS + setOf(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
        )
    }
}
