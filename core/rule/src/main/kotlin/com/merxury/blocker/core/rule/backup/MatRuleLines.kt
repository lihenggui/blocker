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

package com.merxury.blocker.core.rule.backup

import com.merxury.blocker.core.model.component.ComponentId

/** A component named by one line of a MAT export, as written. */
internal data class MatComponent(val packageName: String, val name: String)

/** Keeps the lines of a MAT export that [parseMatLine] is run on: non-blank lines containing "/". */
internal fun filterMatLines(lines: List<String>): List<String> = lines.filter { line ->
    line.trim().isNotEmpty() && line.contains("/")
}

/**
 * Splits the first space-separated token of [line] on "/" into package and component name, or
 * returns null when that token has no "/", as for a line with a leading space or one whose "/"
 * comes only after the first space. Text after a second "/" is dropped.
 */
internal fun parseMatLine(line: String): MatComponent? {
    val splitResult = line.split(" ").first().split("/")
    if (splitResult.size < 2) return null
    return MatComponent(packageName = splitResult[0], name = splitResult[1])
}

/**
 * The component this line names, with a class starting with `.` resolved against the package,
 * or null when the package or the class is empty.
 */
internal fun MatComponent.toComponentId(): ComponentId? = ComponentId.unflatten("$packageName/$name")

/** The components named by the lines of a MAT export, in order and each once, with how many kept lines are malformed. */
internal data class MatLines(val components: List<ComponentId>, val malformed: Int)

/** Parses a MAT export as [filterMatLines] and [parseMatLine] read it, counting malformed lines. */
internal fun parseMatLines(lines: List<String>): MatLines {
    var malformed = 0
    val components = filterMatLines(lines).mapNotNull { line ->
        parseMatLine(line)?.toComponentId().also { if (it == null) malformed++ }
    }
    return MatLines(components.distinct(), malformed)
}
