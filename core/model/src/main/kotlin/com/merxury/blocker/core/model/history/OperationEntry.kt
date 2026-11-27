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

package com.merxury.blocker.core.model.history

import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlState

/**
 * The state change of one component for one Android user.
 *
 * @property operationId the ID of the operation that recorded this change
 * @property key the component and Android user
 * @property type the kind of component declared in the app manifest
 * @property before the PackageManager and Intent Firewall state before the change
 * @property after the PackageManager and Intent Firewall state after the change
 */
data class OperationEntry(
    val operationId: Long,
    val key: ComponentKey,
    val type: ComponentType,
    val before: ControlState,
    val after: ControlState,
)
