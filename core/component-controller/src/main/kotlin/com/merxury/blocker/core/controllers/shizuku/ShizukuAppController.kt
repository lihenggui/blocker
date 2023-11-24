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
import android.content.pm.IPackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.os.Build
import com.merxury.blocker.core.controllers.IAppController
import dagger.hilt.android.qualifiers.ApplicationContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import javax.inject.Inject

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
        );
    }

    override suspend fun disable(packageName: String) {
        Timber.i("Disable $packageName")
        val userId = Shizuku.getUid()
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
    }

    override suspend fun enable(packageName: String) {
        Timber.i("Enable $packageName")
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
    }

    override suspend fun clearCache(packageName: String, action: (Boolean) -> Unit) {
        Timber.i("Clear cache for $packageName")
        pm?.deleteApplicationCacheFiles(packageName, null)
    }

    override suspend fun clearData(packageName: String, action: (Boolean) -> Unit) {
        Timber.i("Clear data for $packageName")
        val userId = Shizuku.getUid()
        pm?.clearApplicationUserData(packageName, null, userId)
    }

    override suspend fun uninstallApp(packageName: String, action: (Int) -> Unit) {
        pm?.deletePackage(
            packageName,
            { name, returnCode ->
                Timber.i("Uninstall $name, return code: $returnCode")
                action(returnCode)
            },
            0,
        )
    }

    override suspend fun forceStop(packageName: String, action: (Boolean) -> Unit) {
        Timber.i("Force stop $packageName")
    }
}
