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

package com.merxury.blocker.core.ui.ifwruleeditor

import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.model.IfwComponentType

data class RuleEditorUiState(
    val packageName: String,
    val componentName: String,
    val componentType: IfwComponentType,
    val blockMode: BlockMode = BlockMode.ALL,
    val log: Boolean = true,
    val blockEnabled: Boolean = true,
    val rootGroup: IfwEditorNode.Group = IfwEditorNode.Group(),
    val isAdvancedRule: Boolean = false,
)

enum class BlockMode {
    ALL,
    CONDITIONAL,
}
