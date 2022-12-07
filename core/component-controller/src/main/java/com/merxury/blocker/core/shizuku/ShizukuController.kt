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

package com.merxury.blocker.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import com.merxury.blocker.core.IController
import com.merxury.blocker.core.utils.ApplicationUtil
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

class ShizukuController(val context: Context) : IController {
    private var pm: IPackageManager? = null

    override suspend fun switchComponent(
        packageName: String,
        componentName: String,
        state: Int
    ): Boolean {
        if (pm == null) {
            pm = IPackageManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("package")
                )
            )
        }
        // 0 means kill the application
        pm?.setComponentEnabledSetting(ComponentName(packageName, componentName), state, 0, 0)
        return true
    }

    override suspend fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(
            packageName,
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        )
    }

    override suspend fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(
            packageName,
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        )
    }

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit
    ): Int {
        var successCount = 0
        componentList.forEach {
            if (enable(it.packageName, it.name)) {
                successCount++
            }
            action(it)
        }
        return successCount
    }

    override suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit
    ): Int {
        var successCount = 0
        componentList.forEach {
            if (disable(it.packageName, it.name)) {
                successCount++
            }
            action(it)
        }
        return successCount
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String
    ): Boolean {
        return ApplicationUtil.checkComponentIsEnabled(
            context.packageManager,
            ComponentName(packageName, componentName)
        )
    }
}
