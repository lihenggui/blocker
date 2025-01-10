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

package com.merxury.blocker.core.model.rule

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import kotlinx.serialization.Serializable

@Serializable
data class ComponentRule(
    var packageName: String = "",
    var name: String = "",
    var state: Boolean = true,
    var type: ComponentType = ComponentType.RECEIVER,
    var method: ControllerType = ControllerType.PM,
) {
    fun toComponentInfo() = ComponentInfo(
        packageName = packageName,
        name = name,
        type = type,
        pmBlocked = if (method == ControllerType.PM) !state else false,
        ifwBlocked = if (method == ControllerType.IFW) !state else false,
    )
}
