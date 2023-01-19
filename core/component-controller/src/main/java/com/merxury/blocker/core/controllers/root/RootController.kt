/*
 * Copyright 2022 Blocker
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

/**
 * Created by Mercury on 2017/12/31.
 * A class that controls the state of application components
 */

package com.merxury.blocker.core.controllers.root

import android.content.ComponentName
import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class RootController @Inject constructor(
    private val context: Context,
) : IController {

    override suspend fun switchComponent(
        packageName: String,
        componentName: String,
        state: Int,
    ): Boolean {
        val comm: String = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> removeEscapeCharacter(
                String.format(
                    ENABLE_COMPONENT_TEMPLATE,
                    packageName,
                    componentName,
                ),
            )

            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> removeEscapeCharacter(
                String.format(
                    DISABLE_COMPONENT_TEMPLATE,
                    packageName,
                    componentName,
                ),
            )

            else -> return false
        }
        Timber.d("command:$comm, componentState is $state")
        return withContext(Dispatchers.IO) {
            try {
                val commandOutput = comm.exec().orEmpty()
                Timber.d("Command output: $commandOutput")
                return@withContext !commandOutput.contains(FAILED_EXCEPTION_MSG)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    override suspend fun enable(packageName: String, componentName: String): Boolean {
        return switchComponent(
            packageName,
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
    }

    override suspend fun disable(packageName: String, componentName: String): Boolean {
        return switchComponent(
            packageName,
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        )
    }

    override suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        var succeededCount = 0
        componentList.forEach {
            if (enable(it.packageName, it.name)) {
                succeededCount++
            }
            action(it)
        }
        return succeededCount
    }

    override suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int {
        var succeededCount = 0
        componentList.forEach {
            if (disable(it.packageName, it.name)) {
                succeededCount++
            }
            action(it)
        }
        return succeededCount
    }

    private fun removeEscapeCharacter(comm: String): String {
        return comm.replace("$", "\\$")
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean {
        return ApplicationUtil.checkComponentIsEnabled(
            context.packageManager,
            ComponentName(packageName, componentName),
        )
    }

    companion object {
        private const val DISABLE_COMPONENT_TEMPLATE = "pm disable %s/%s"
        private const val ENABLE_COMPONENT_TEMPLATE = "pm enable %s/%s"
        private const val FAILED_EXCEPTION_MSG = "java.lang.IllegalArgumentException"
    }
}
