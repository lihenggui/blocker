/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.rule.entity

import com.merxury.blocker.core.entity.EComponentType
import com.merxury.blocker.core.root.EControllerMethod

data class ComponentRule(
    var packageName: String = "",
    var name: String = "",
    var state: Boolean = true,
    var type: EComponentType = EComponentType.RECEIVER,
    var method: EControllerMethod = EControllerMethod.PM
)
