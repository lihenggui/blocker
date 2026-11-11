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

package com.merxury.blocker.core.domain

import android.icu.lang.UCharacter

/** Matches Android SQLite's `LIKE '%' || keyword || '%'`, including ICU simple case folding. */
internal class SqlLikePattern(keyword: String) {
    private val literal = keyword.takeUnless { it.any { char -> char == '%' || char == '_' || char == '\u0000' } }
        ?.foldSqlLikeCase()
    private val pattern = ("%" + keyword + "%").substringBefore('\u0000').foldSqlLikeCase()

    fun matches(value: String): Boolean = matchesFolded(value.substringBefore('\u0000').foldSqlLikeCase())

    fun matchesFolded(value: String): Boolean {
        literal?.let { return value.contains(it) }
        var valueIndex = 0
        var patternIndex = 0
        var wildcardIndex = -1
        var retryIndex = 0
        while (valueIndex < value.length) {
            when {
                patternIndex < pattern.length && pattern[patternIndex] == '%' -> {
                    wildcardIndex = patternIndex++
                    retryIndex = valueIndex
                }

                patternIndex < pattern.length &&
                    (pattern[patternIndex] == '_' || pattern.codePointAt(patternIndex) == value.codePointAt(valueIndex)) -> {
                    patternIndex += if (pattern[patternIndex] == '_') 1 else Character.charCount(pattern.codePointAt(patternIndex))
                    valueIndex += Character.charCount(value.codePointAt(valueIndex))
                }

                wildcardIndex >= 0 -> {
                    retryIndex += Character.charCount(value.codePointAt(retryIndex))
                    valueIndex = retryIndex
                    patternIndex = wildcardIndex + 1
                }

                else -> return false
            }
        }
        while (patternIndex < pattern.length && pattern[patternIndex] == '%') patternIndex++
        return patternIndex == pattern.length
    }
}

internal fun String.foldSqlLikeCase(): String {
    if (all { it.code < 128 }) {
        if (none { it in 'A'..'Z' }) return this
        return buildString(length) {
            for (char in this@foldSqlLikeCase) append(if (char in 'A'..'Z') char + ('a' - 'A') else char)
        }
    }
    // Android registers SQLite's ICU LIKE implementation. Use simple code-point folding,
    // not String.lowercase()/full case folding, which can expand one character into several.
    return buildString(length) {
        var index = 0
        while (index < this@foldSqlLikeCase.length) {
            val codePoint = this@foldSqlLikeCase.codePointAt(index)
            appendCodePoint(UCharacter.foldCase(codePoint, true))
            index += Character.charCount(codePoint)
        }
    }
}
