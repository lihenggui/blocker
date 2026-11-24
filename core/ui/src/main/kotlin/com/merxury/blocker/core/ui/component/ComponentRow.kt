/*
 * Copyright 2026 Blocker
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
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus

/**
 * One component as the cache holds it, for a row with one switch, see [ComponentListItem].
 *
 * @property enabled whether its switch shows on, see [isSwitchOn]
 * @property isRunning whether the service runs; false when that cannot be read
 */
data class ComponentRow(
    val id: ComponentId,
    val type: ComponentType,
    val enabled: Boolean,
    val isRunning: Boolean = false,
    val description: String? = null,
)

/**
 * The row for [this] component, from what the repository holds and nothing else.
 *
 * @param running the running services of the app, or null when they cannot be read
 */
fun ComponentStatus.toRow(description: String? = null, running: Set<ComponentId>? = null): ComponentRow = ComponentRow(
    id = declaration.id,
    type = declaration.type,
    enabled = isSwitchOn,
    isRunning = running?.contains(declaration.id) == true,
    description = description,
)
