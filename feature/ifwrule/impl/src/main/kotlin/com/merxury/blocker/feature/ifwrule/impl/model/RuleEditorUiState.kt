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

package com.merxury.blocker.feature.ifwrule.impl.model

import com.merxury.core.ifw.model.IfwComponentType

sealed interface RuleEditorScreenUiState {
    data object Loading : RuleEditorScreenUiState
    data class Success(val editor: RuleEditorUiState) : RuleEditorScreenUiState
    data class Error(val message: String) : RuleEditorScreenUiState
}

data class RuleEditorUiState(
    val packageName: String,
    val componentName: String,
    val componentType: IfwComponentType,
    val blockMode: BlockMode = BlockMode.ALL,
    val log: Boolean = true,
    val blockEnabled: Boolean = true,
    val combineMode: CombineMode = CombineMode.ALL_MATCH,
    val conditions: List<ConditionUiState> = emptyList(),
    val isAdvancedRule: Boolean = false,
)

enum class BlockMode { ALL, CONDITIONAL }
enum class CombineMode { ALL_MATCH, ANY_MATCH }

sealed interface ConditionUiState {
    val id: String

    data class ActionFilter(
        override val id: String,
        val matchMode: MatchMode = MatchMode.EXACT,
        val value: String = "",
    ) : ConditionUiState

    data class SourceControl(
        override val id: String,
        val option: SourceOption = SourceOption.ALLOW_SYSTEM_ONLY,
    ) : ConditionUiState

    data class CallerApp(
        override val id: String,
        val packageNames: List<String> = emptyList(),
    ) : ConditionUiState

    data class CallerPermission(
        override val id: String,
        val permission: String = "",
        val mode: PermissionMode = PermissionMode.REQUIRE,
    ) : ConditionUiState

    data class CategoryFilter(
        override val id: String,
        val name: String = "",
    ) : ConditionUiState

    data class LinkFilter(
        override val id: String,
        val host: String = "",
        val path: String = "",
        val pathMatchMode: MatchMode = MatchMode.EXACT,
        val scheme: String = "",
    ) : ConditionUiState

    data class DataFilter(
        override val id: String,
        val matchMode: MatchMode = MatchMode.EXACT,
        val value: String = "",
    ) : ConditionUiState

    data class MimeTypeFilter(
        override val id: String,
        val matchMode: MatchMode = MatchMode.EXACT,
        val value: String = "",
    ) : ConditionUiState

    data class PortFilter(
        override val id: String,
        val portMode: PortMode = PortMode.EXACT,
        val equals: Int? = null,
        val min: Int? = null,
        val max: Int? = null,
    ) : ConditionUiState

    data class ComponentPattern(
        override val id: String,
        val patternType: ComponentPatternType = ComponentPatternType.COMPONENT,
        val matchMode: MatchMode = MatchMode.EXACT,
        val value: String = "",
    ) : ConditionUiState
}

enum class SourceOption {
    ALLOW_SYSTEM_ONLY,
    ALLOW_SIGNATURE_ONLY,
    ALLOW_SYSTEM_OR_SIGNATURE,
    BLOCK_SYSTEM,
}

enum class PermissionMode { REQUIRE, BLOCK_WITH }
enum class PortMode { EXACT, RANGE }
enum class ComponentPatternType { COMPONENT, NAME, PACKAGE }

enum class MatchMode {
    EXACT,
    STARTS_WITH,
    CONTAINS,
    PATTERN,
    REGEX,
    IS_NULL,
}
