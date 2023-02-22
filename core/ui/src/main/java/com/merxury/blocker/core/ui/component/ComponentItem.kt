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

package com.merxury.blocker.core.ui.component

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo

data class ComponentItem(
    val name: String,
    val simpleName: String,
    val packageName: String,
    val type: ComponentType,
    val pmBlocked: Boolean,
    val ifwBlocked: Boolean = false,
    val isRunning: Boolean = false,
    val description: String? = null,
) {
    fun enabled() = !(pmBlocked || ifwBlocked)
}

fun ComponentInfo.toComponentItem(isRunning: Boolean = false) = ComponentItem(
    name = name,
    simpleName = simpleName,
    packageName = packageName,
    type = type,
    pmBlocked = pmBlocked,
    ifwBlocked = ifwBlocked,
    description = description,
    isRunning = isRunning,
)