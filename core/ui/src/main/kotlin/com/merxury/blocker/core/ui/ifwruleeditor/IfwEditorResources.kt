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

import androidx.annotation.StringRes
import com.merxury.blocker.core.ui.R
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.model.SenderType

internal val IfwEditorNode.Group.titleRes: Int
    @StringRes get() = when {
        excluded -> R.string.core_ui_ifw_group_excluded_title
        mode == IfwEditorGroupMode.ALL -> R.string.core_ui_ifw_group_all_title
        else -> R.string.core_ui_ifw_group_any_title
    }

internal val IfwEditorNode.Group.summaryRes: Int
    @StringRes get() = when {
        excluded -> R.string.core_ui_ifw_group_excluded_summary
        mode == IfwEditorGroupMode.ALL -> R.string.core_ui_ifw_group_all_summary
        else -> R.string.core_ui_ifw_group_any_summary
    }

internal val IfwEditorConditionKind.labelRes: Int
    @StringRes get() = when (this) {
        IfwEditorConditionKind.ACTION -> R.string.core_ui_ifw_condition_action
        IfwEditorConditionKind.CATEGORY -> R.string.core_ui_ifw_condition_category
        IfwEditorConditionKind.CALLER_TYPE -> R.string.core_ui_ifw_condition_caller_type
        IfwEditorConditionKind.CALLER_PACKAGE -> R.string.core_ui_ifw_condition_caller_package
        IfwEditorConditionKind.CALLER_PERMISSION -> R.string.core_ui_ifw_condition_caller_permission
        IfwEditorConditionKind.COMPONENT -> R.string.core_ui_ifw_condition_component
        IfwEditorConditionKind.COMPONENT_NAME -> R.string.core_ui_ifw_condition_component_name
        IfwEditorConditionKind.COMPONENT_PACKAGE -> R.string.core_ui_ifw_condition_component_package
        IfwEditorConditionKind.COMPONENT_FILTER -> R.string.core_ui_ifw_condition_component_filter
        IfwEditorConditionKind.HOST -> R.string.core_ui_ifw_condition_host
        IfwEditorConditionKind.SCHEME -> R.string.core_ui_ifw_condition_scheme
        IfwEditorConditionKind.SCHEME_SPECIFIC_PART -> R.string.core_ui_ifw_condition_scheme_specific_part
        IfwEditorConditionKind.PATH -> R.string.core_ui_ifw_condition_path
        IfwEditorConditionKind.DATA -> R.string.core_ui_ifw_condition_data
        IfwEditorConditionKind.MIME_TYPE -> R.string.core_ui_ifw_condition_mime_type
        IfwEditorConditionKind.PORT -> R.string.core_ui_ifw_condition_port
    }

internal val IfwEditorConditionKind.descriptionRes: Int
    @StringRes get() = when (this) {
        IfwEditorConditionKind.ACTION -> R.string.core_ui_ifw_condition_action_desc
        IfwEditorConditionKind.CATEGORY -> R.string.core_ui_ifw_condition_category_desc
        IfwEditorConditionKind.CALLER_TYPE -> R.string.core_ui_ifw_condition_caller_type_desc
        IfwEditorConditionKind.CALLER_PACKAGE -> R.string.core_ui_ifw_condition_caller_package_desc
        IfwEditorConditionKind.CALLER_PERMISSION -> R.string.core_ui_ifw_condition_caller_permission_desc
        IfwEditorConditionKind.COMPONENT -> R.string.core_ui_ifw_condition_component_desc
        IfwEditorConditionKind.COMPONENT_NAME -> R.string.core_ui_ifw_condition_component_name_desc
        IfwEditorConditionKind.COMPONENT_PACKAGE -> R.string.core_ui_ifw_condition_component_package_desc
        IfwEditorConditionKind.COMPONENT_FILTER -> R.string.core_ui_ifw_condition_component_filter_desc
        IfwEditorConditionKind.HOST -> R.string.core_ui_ifw_condition_host_desc
        IfwEditorConditionKind.SCHEME -> R.string.core_ui_ifw_condition_scheme_desc
        IfwEditorConditionKind.SCHEME_SPECIFIC_PART -> R.string.core_ui_ifw_condition_scheme_specific_part_desc
        IfwEditorConditionKind.PATH -> R.string.core_ui_ifw_condition_path_desc
        IfwEditorConditionKind.DATA -> R.string.core_ui_ifw_condition_data_desc
        IfwEditorConditionKind.MIME_TYPE -> R.string.core_ui_ifw_condition_mime_type_desc
        IfwEditorConditionKind.PORT -> R.string.core_ui_ifw_condition_port_desc
    }

internal val IfwEditorConditionKind.valueHintRes: Int
    @StringRes get() = when (this) {
        IfwEditorConditionKind.ACTION -> R.string.core_ui_ifw_value_hint_action
        IfwEditorConditionKind.CATEGORY -> R.string.core_ui_ifw_value_hint_category
        IfwEditorConditionKind.CALLER_TYPE -> R.string.core_ui_ifw_sender_type
        IfwEditorConditionKind.CALLER_PACKAGE -> R.string.core_ui_ifw_value_hint_package_name
        IfwEditorConditionKind.CALLER_PERMISSION -> R.string.core_ui_ifw_value_hint_permission
        IfwEditorConditionKind.COMPONENT -> R.string.core_ui_ifw_value_hint_component
        IfwEditorConditionKind.COMPONENT_NAME -> R.string.core_ui_ifw_value_hint_component_name
        IfwEditorConditionKind.COMPONENT_PACKAGE -> R.string.core_ui_ifw_value_hint_package_name
        IfwEditorConditionKind.COMPONENT_FILTER -> R.string.core_ui_ifw_value_hint_component_filter
        IfwEditorConditionKind.HOST -> R.string.core_ui_ifw_value_hint_host
        IfwEditorConditionKind.SCHEME -> R.string.core_ui_ifw_value_hint_scheme
        IfwEditorConditionKind.SCHEME_SPECIFIC_PART -> R.string.core_ui_ifw_value_hint_scheme_specific_part
        IfwEditorConditionKind.PATH -> R.string.core_ui_ifw_value_hint_path
        IfwEditorConditionKind.DATA -> R.string.core_ui_ifw_value_hint_data
        IfwEditorConditionKind.MIME_TYPE -> R.string.core_ui_ifw_value_hint_mime_type
        IfwEditorConditionKind.PORT -> R.string.core_ui_ifw_port_exact_value
    }

internal val IfwEditorStringMatcherMode.labelRes: Int
    @StringRes get() = when (this) {
        IfwEditorStringMatcherMode.EXACT -> R.string.core_ui_ifw_match_exact
        IfwEditorStringMatcherMode.STARTS_WITH -> R.string.core_ui_ifw_match_starts_with
        IfwEditorStringMatcherMode.CONTAINS -> R.string.core_ui_ifw_match_contains
        IfwEditorStringMatcherMode.PATTERN -> R.string.core_ui_ifw_match_pattern
        IfwEditorStringMatcherMode.REGEX -> R.string.core_ui_ifw_match_regex
        IfwEditorStringMatcherMode.IS_NULL -> R.string.core_ui_ifw_match_is_empty
        IfwEditorStringMatcherMode.IS_NOT_NULL -> R.string.core_ui_ifw_match_is_not_empty
    }

internal val SenderType.labelRes: Int
    @StringRes get() = when (this) {
        SenderType.SIGNATURE -> R.string.core_ui_ifw_sender_signature
        SenderType.SYSTEM -> R.string.core_ui_ifw_sender_system
        SenderType.SYSTEM_OR_SIGNATURE -> R.string.core_ui_ifw_sender_system_or_signature
        SenderType.USER_ID -> R.string.core_ui_ifw_sender_user_id
    }
