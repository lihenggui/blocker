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
import com.merxury.blocker.core.controllers.IController
import com.merxury.blocker.core.model.ComponentState
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.utils.PackageInfoDataSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RootApiController @Inject constructor(
    private val rootApiClient: RootApiClient,
    private val packageInfoDataSource: PackageInfoDataSource,
) : IController {

    override suspend fun init() = rootApiClient.ensureAvailable()

    override suspend fun switchComponent(
        component: ComponentInfo,
        state: ComponentState,
    ): Boolean {
        val packageName = component.packageName
        val componentName = component.name
        Timber.d("Switch component: $packageName/$componentName, state: $state")
        return rootApiClient.execute(
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

    override suspend fun checkComponentEnableState(
        packageName: String,
        componentName: String,
    ): Boolean = packageInfoDataSource.checkComponentIsEnabled(
        ComponentName(packageName, componentName),
    )
}
