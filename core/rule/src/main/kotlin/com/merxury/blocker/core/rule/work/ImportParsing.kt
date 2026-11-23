// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.IfwRules

/** A component named by one line of a MAT export. */
internal data class MatComponent(val packageName: String, val name: String)

/** Keeps the lines of a MAT export that [parseMatLine] is run on: non-blank lines containing "/". */
internal fun filterMatLines(lines: List<String>): List<String> = lines.filter { line ->
    line.trim().isNotEmpty() && line.contains("/")
}

/**
 * Splits the first space-separated token of [line] on "/" into package and component name.
 *
 * Throws [IndexOutOfBoundsException] if that token has no "/", which a kept line can still
 * lack when the "/" only appears after the first space.
 */
internal fun parseMatLine(line: String): MatComponent {
    val splitResult = line.split(" ").first().split("/")
    return MatComponent(packageName = splitResult[0], name = splitResult[1])
}

/** The component filters of an IFW backup file, as the import applies them. */
internal data class IfwImportComponents(
    val activities: List<ComponentInfo>,
    val broadcasts: List<ComponentInfo>,
    val services: List<ComponentInfo>,
)

/**
 * Takes the component filters of each component type from [rules]. Other conditions are ignored.
 *
 * Throws [IndexOutOfBoundsException] for a component filter name without "/".
 */
internal fun extractIfwImportComponents(rules: IfwRules): IfwImportComponents = IfwImportComponents(
    activities = rules.componentFiltersFor(IfwComponentType.ACTIVITY).map(::ifwComponentFromName),
    broadcasts = rules.componentFiltersFor(IfwComponentType.BROADCAST).map(::ifwComponentFromName),
    services = rules.componentFiltersFor(IfwComponentType.SERVICE).map(::ifwComponentFromName),
)

private fun ifwComponentFromName(name: String): ComponentInfo {
    val splitResult = name.split("/")
    return ComponentInfo(
        packageName = splitResult[0],
        name = splitResult[1],
        type = ACTIVITY,
    )
}
