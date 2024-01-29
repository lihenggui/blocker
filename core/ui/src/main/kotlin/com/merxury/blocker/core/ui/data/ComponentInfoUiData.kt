/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.ui.data

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo

/**
 * A data representation of a component info and state that is used in the UI layer.
 * It is mainly used for showing UI state asynchronously when user trigger some action.
 */
data class ComponentInfoUiData(
    val name: String,
    val simpleName: String,
    val packageName: String,
    val type: ComponentType,
    val pmBlocked: Boolean,
    val switchUiState: Boolean = false,
    val dirty: Boolean = false,
    val exported: Boolean = false,
    val isRunning: Boolean = false,
    val ifwBlocked: Boolean = false,
    val description: String? = null,
) {
    fun toComponentInfo() = ComponentInfo(
        name = name,
        simpleName = simpleName,
        packageName = packageName,
        type = type,
        pmBlocked = pmBlocked,
        exported = exported,
        isRunning = isRunning,
        ifwBlocked = ifwBlocked,
        description = description,
    )
}

fun ComponentInfo.toComponentInfoUiData(switchUiState: Boolean, dirty: Boolean) =
    ComponentInfoUiData(
        name = name,
        simpleName = simpleName,
        packageName = packageName,
        type = type,
        pmBlocked = pmBlocked,
        switchUiState = switchUiState,
        dirty = dirty,
        exported = exported,
        isRunning = isRunning,
        ifwBlocked = ifwBlocked,
        description = description,
    )
