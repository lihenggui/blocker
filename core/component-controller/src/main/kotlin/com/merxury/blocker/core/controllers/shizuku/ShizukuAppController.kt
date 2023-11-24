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

package com.merxury.blocker.core.controllers.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.IPackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.merxury.blocker.core.controllers.IAppController
import dagger.hilt.android.qualifiers.ApplicationContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import javax.inject.Inject

private const val SHELL_UID = 2000

class ShizukuAppController @Inject constructor(
    @ApplicationContext private val context: Context,
) : IAppController {

    private val pm: IPackageManager? by lazy {
        addHiddenApiExemptions()
        Timber.d("Get package manager service from ShizukuBinderWrapper")
        IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("package"),
            ),
        )
    }

    private fun addHiddenApiExemptions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            return
        }
        Timber.i("Add hidden API exemptions")
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/content/pm/IPackageManager;",
        )
    }

    override suspend fun disable(packageName: String): Boolean {
        Timber.i("Disable $packageName")
        val userId = Shizuku.getUid()
        if (userId == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pm?.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_DISABLED,
                0,
                userId,
                context.packageName,
            )
        } else {
            pm?.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_DISABLED,
                userId,
                0,
            )
        }
        return true
    }

    override suspend fun enable(packageName: String): Boolean {
        Timber.i("Enable $packageName")
        val userId = Shizuku.getUid()
        if (userId == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pm?.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_ENABLED,
                0,
                0,
                context.packageName,
            )
        } else {
            pm?.setApplicationEnabledSetting(packageName, COMPONENT_ENABLED_STATE_ENABLED, 0, 0)
        }
        return true
    }

    override suspend fun clearCache(packageName: String): Boolean {
        Timber.i("Clear cache for $packageName")
        val userId = Shizuku.getUid()
        if (userId == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }
        pm?.deleteApplicationCacheFiles(packageName, null)
        return true
    }

    override suspend fun clearData(packageName: String): Boolean {
        Timber.i("Clear data for $packageName")
        val userId = Shizuku.getUid()
        if (userId == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }
        pm?.clearApplicationUserData(packageName, null, userId)
        return true
    }

    override suspend fun uninstallApp(packageName: String): Boolean {
        pm?.deletePackage(
            packageName,
            null,
            0,
        )
        return true
    }

    override suspend fun forceStop(packageName: String): Boolean {
        Timber.i("Force stop $packageName")
        return true
    }

    private fun openAppDetails(packageName: String) {
        Timber.i("Open app details for $packageName")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
