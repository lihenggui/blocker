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

package com.merxury.core.ifw.editor

import com.merxury.core.ifw.model.SenderType
import java.util.UUID

sealed interface IfwEditorNode {
    val id: String
    val excluded: Boolean

    data class Group(
        override val id: String = newIfwEditorId(),
        val mode: IfwEditorGroupMode = IfwEditorGroupMode.ALL,
        val children: List<IfwEditorNode> = emptyList(),
        override val excluded: Boolean = false,
    ) : IfwEditorNode

    data class Condition(
        override val id: String = newIfwEditorId(),
        val kind: IfwEditorConditionKind = IfwEditorConditionKind.ACTION,
        val matcherMode: IfwEditorStringMatcherMode = IfwEditorStringMatcherMode.EXACT,
        val value: String = "",
        val senderType: SenderType = SenderType.SYSTEM,
        val portMode: IfwEditorPortMode = IfwEditorPortMode.EXACT,
        val exactPort: Int? = null,
        val minPort: Int? = null,
        val maxPort: Int? = null,
        override val excluded: Boolean = false,
    ) : IfwEditorNode
}

enum class IfwEditorGroupMode {
    ALL,
    ANY,
}

enum class IfwEditorConditionKind(
    val supportsStringMatcher: Boolean = false,
) {
    ACTION(supportsStringMatcher = true),
    CATEGORY,
    CALLER_TYPE,
    CALLER_PACKAGE,
    CALLER_PERMISSION,
    COMPONENT(supportsStringMatcher = true),
    COMPONENT_NAME(supportsStringMatcher = true),
    COMPONENT_PACKAGE(supportsStringMatcher = true),
    COMPONENT_FILTER,
    HOST(supportsStringMatcher = true),
    SCHEME(supportsStringMatcher = true),
    SCHEME_SPECIFIC_PART(supportsStringMatcher = true),
    PATH(supportsStringMatcher = true),
    DATA(supportsStringMatcher = true),
    MIME_TYPE(supportsStringMatcher = true),
    PORT,
}

enum class IfwEditorStringMatcherMode {
    EXACT,
    STARTS_WITH,
    CONTAINS,
    PATTERN,
    REGEX,
    IS_NULL,
    IS_NOT_NULL,
    ;

    val isNullMode: Boolean
        get() = this == IS_NULL || this == IS_NOT_NULL
}

enum class IfwEditorPortMode {
    EXACT,
    RANGE,
}

fun newIfwEditorId(): String = UUID.randomUUID().toString()

fun IfwEditorNode.Group.addNode(
    targetGroupId: String,
    node: IfwEditorNode,
): IfwEditorNode.Group {
    if (id == targetGroupId) {
        return copy(children = children + node)
    }
    return copy(
        children = children.map { child ->
            when (child) {
                is IfwEditorNode.Group -> child.addNode(targetGroupId, node)
                is IfwEditorNode.Condition -> child
            }
        },
    )
}

fun IfwEditorNode.Group.updateNode(updated: IfwEditorNode): IfwEditorNode.Group {
    if (id == updated.id && updated is IfwEditorNode.Group) return updated
    return copy(
        children = children.map { child ->
            when {
                child.id == updated.id -> updated
                child is IfwEditorNode.Group -> child.updateNode(updated)
                else -> child
            }
        },
    )
}

fun IfwEditorNode.Group.removeNode(nodeId: String): IfwEditorNode.Group = copy(
    children = children
        .filterNot { it.id == nodeId }
        .map { child ->
            if (child is IfwEditorNode.Group) {
                child.removeNode(nodeId)
            } else {
                child
            }
        },
)
