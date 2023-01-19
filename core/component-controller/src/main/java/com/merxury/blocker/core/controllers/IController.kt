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

package com.merxury.blocker.core.controllers

import android.content.pm.ComponentInfo

/**
 * Created by Mercury on 2018/1/13.
 * An Interface that defines what controller should do
 */

interface IController {

    /**
     * a method to change a component's state
     *
     * @param packageName   package name
     * @param componentName component name
     * @param state         PackageManager.COMPONENT_ENABLED_STATE_ENABLED: enable component
     * COMPONENT_ENABLED_STATE_DISABLED: disable component
     * @return true : changed component state successfully
     * false: cannot disable component
     */
    suspend fun switchComponent(packageName: String, componentName: String, state: Int): Boolean

    suspend fun enable(packageName: String, componentName: String): Boolean

    suspend fun disable(packageName: String, componentName: String): Boolean

    suspend fun batchEnable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int

    suspend fun batchDisable(
        componentList: List<ComponentInfo>,
        action: suspend (info: ComponentInfo) -> Unit,
    ): Int

    suspend fun checkComponentEnableState(packageName: String, componentName: String): Boolean
}
