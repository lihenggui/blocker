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

package com.merxury.blocker.core.testing.controller

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.core.ifw.ComponentTypeResolver

class FakeComponentTypeResolver : ComponentTypeResolver {
    private val typeMap = mutableMapOf<String, ComponentType>()

    fun setComponentType(packageName: String, componentName: String, type: ComponentType) {
        typeMap["$packageName/$componentName"] = type
    }

    override suspend fun getComponentType(
        packageName: String,
        componentName: String,
    ): ComponentType = typeMap["$packageName/$componentName"] ?: PROVIDER
}
