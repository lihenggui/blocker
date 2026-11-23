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
import android.os.Process
import android.os.UserManager
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [PlatformPackages] over the app's own PackageManager, which needs no privilege and reaches only
 * the user Blocker runs in.
 */
class LocalPlatformPackages @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlatformPackages {

    private val pm: PackageManager = context.packageManager

    // A uid is userId * 100000 + appId on every Android version.
    private val ownUser = UserId(Process.myUid() / UIDS_PER_USER)

    override fun users(): List<DeviceUser> = listOf(
        DeviceUser(
            id = ownUser,
            // Reading the name needs a permission only system apps hold.
            name = null,
            kind = ownUserKind(),
            parent = null,
            running = true,
        ),
    )

    @SuppressLint("QueryPermissionsNeeded")
    override fun installedPackages(user: UserId): List<PackageInfo> {
        requireOwn(user)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }
    }

    // Lint does not accept a computed Int as a flag set.
    @SuppressLint("WrongConstant")
    override fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo {
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

    override fun applicationEnabledSetting(packageName: String, user: UserId): Int {
        requireOwn(user)
        return pm.getApplicationEnabledSetting(packageName)
    }

    override fun componentEnabledSetting(component: ComponentName, user: UserId): Int {
        requireOwn(user)
        return pm.getComponentEnabledSetting(component)
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
        const val UIDS_PER_USER = 100_000
    }
}
