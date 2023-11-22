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
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import javax.inject.Inject

class ShizukuAppController @Inject constructor(
    @ApplicationContext private val context: Context,
) : IAppController {

    private var pm: IPackageManager? = null
    override suspend fun disable(packageName: String) {
        ensureInitialization()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pm?.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_DISABLED,
                0,
                0,
                context.packageName,
            )
        } else {
            pm?.setApplicationEnabledSetting(packageName, COMPONENT_ENABLED_STATE_DISABLED, 0, 0)
        }
    }

    override suspend fun enable(packageName: String) {
        ensureInitialization()
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
        ensureInitialization()
        pm?.deleteApplicationCacheFiles(packageName) { name, succeeded ->
            Timber.i("Clear cache for $name, succeeded: $succeeded")
            action(succeeded)
        }
    }

    override suspend fun clearData(packageName: String, action: (Boolean) -> Unit) {
        ensureInitialization()
        pm?.clearApplicationUserData(packageName) { name, succeeded ->
            Timber.i("Clear data for $name, succeeded: $succeeded")
            action(succeeded)
        }
    }

    override suspend fun uninstallApp(packageName: String, action: (Boolean) -> Unit) {
        ensureInitialization()
        pm?.deletePackage(
            packageName,
            { name, returnCode ->
                Timber.i("Uninstall $name, return code: $returnCode")
            },
            0,
        )
    }

    private fun ensureInitialization() {
        if (pm == null) {
            pm = IPackageManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("package"),
                ),
            )
        }
        if (pm == null) {
            Timber.e("Failed to get PackageManager from ShizukuBinderWrapper")
        }
    }
}
