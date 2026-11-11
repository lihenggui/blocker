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

package com.merxury.blocker.core.controllers.root.api

import android.content.ComponentName
import android.os.Build
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.controllers.utils.controlComponentBatches
import com.merxury.blocker.core.exception.RootUnavailableException
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.root.RootCommandExecutor
import com.merxury.blocker.core.utils.PackageInfoDataSource
import com.merxury.blocker.core.utils.RootAvailabilityChecker
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RootApiController @Inject constructor(
    private val rootChecker: RootAvailabilityChecker,
    private val rootCommandExecutor: RootCommandExecutor,
    private val packageInfoDataSource: PackageInfoDataSource,
) : IController {

    override suspend fun init() {
        if (!rootChecker.isRootAvailable()) {
            throw RootUnavailableException()
        }
    }

    override suspend fun switchComponent(
        component: ComponentInfo,
        state: ComponentState,
    ): Boolean {
        val packageName = component.packageName
        val componentName = component.name
        Timber.d("Switch component: $packageName/$componentName, state: $state")
        return rootCommandExecutor.execute(
            SetComponentEnabledSettingCommand(
                packageName = packageName,
                componentName = componentName,
                state = state.pmValue,
            ),
        ).value
    }

    override suspend fun enable(component: ComponentInfo): Boolean = switchComponent(
        component,
        ComponentState.ENABLED,
    )

    override suspend fun disable(component: ComponentInfo): Boolean = switchComponent(
        component,
        ComponentState.DISABLED,
    )

    override suspend fun batchEnable(componentList: List<ComponentInfo>, action: suspend (ComponentInfo) -> Unit): Int = batchControl(componentList, ComponentState.ENABLED, action)

    override suspend fun batchDisable(componentList: List<ComponentInfo>, action: suspend (ComponentInfo) -> Unit): Int = batchControl(componentList, ComponentState.DISABLED, action)

    private suspend fun batchControl(
        components: List<ComponentInfo>,
        state: ComponentState,
        action: suspend (ComponentInfo) -> Unit,
    ): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (state == ComponentState.ENABLED) super.batchEnable(components, action) else super.batchDisable(components, action)
        }
        return controlComponentBatches(
            components,
            applyBatch = { batch ->
                rootCommandExecutor.execute(
                    SetComponentsEnabledSettingCommand(batch.map { ComponentName(it.packageName, it.name) }, state.pmValue),
                ).value
            },
            onApplied = action,
        )
    }

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean = packageInfoDataSource.checkComponentIsEnabled(
        ComponentName(packageName, componentName),
    )
}
