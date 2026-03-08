/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.controllers.root.command

import android.content.ComponentName
import android.content.Context
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.utils.ContextUtils.userId
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.utils.PackageInfoDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class RootController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packageInfoDataSource: PackageInfoDataSource,
) : IController {

    override suspend fun switchComponent(
        component: ComponentInfo,
        state: ComponentState,
    ): Boolean {
        val packageName = component.packageName
        val componentName = component.name
        val comm: String = when (state) {
            ComponentState.ENABLED -> removeEscapeCharacter(
                String.format(
                    ENABLE_COMPONENT_TEMPLATE,
                    context.userId,
                    packageName,
                    componentName,
                ),
            )

            ComponentState.DISABLED -> removeEscapeCharacter(
                String.format(
                    DISABLE_COMPONENT_TEMPLATE,
                    context.userId,
                    packageName,
                    componentName,
                ),
            )
        }
        Timber.d("command:$comm, componentState is $state")
        return withContext(Dispatchers.IO) {
            val commandOutput = comm.exec()
            commandOutput.isSuccess
        }
    }

    override suspend fun enable(component: ComponentInfo): Boolean = switchComponent(
        component,
        ComponentState.ENABLED,
    )

    override suspend fun disable(component: ComponentInfo): Boolean = switchComponent(
        component,
        ComponentState.DISABLED,
    )

    private fun removeEscapeCharacter(comm: String): String = comm.replace("$", "\\$")

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean = packageInfoDataSource.checkComponentIsEnabled(
        ComponentName(packageName, componentName),
    )

    companion object {
        private const val DISABLE_COMPONENT_TEMPLATE = "pm disable --user %s %s/%s"
        private const val ENABLE_COMPONENT_TEMPLATE = "pm enable --user %s %s/%s"
    }
}
