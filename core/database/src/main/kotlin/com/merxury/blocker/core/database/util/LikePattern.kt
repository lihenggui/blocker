// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.util

/** Escapes `\`, `%` and `_` for a `LIKE` pattern that declares `ESCAPE '\'`. */
internal fun String.escapeLikeWildcards(): String = buildString(length) {
    for (char in this@escapeLikeWildcards) {
        if (char == '\\' || char == '%' || char == '_') append('\\')
        append(char)
    }
}
