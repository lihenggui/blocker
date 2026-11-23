// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.work

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

/** Pins which lines of a MAT (.txt) export ImportMatRulesWorker keeps and how it splits them. */
class MatRuleLinesTest {

    @Test
    fun givenBlankLines_whenFiltering_thenTheyAreDropped() {
        val kept = filterMatLines(listOf("", "   ", "\t", "com.example/.Main"))

        assertThat(kept).containsExactly("com.example/.Main")
    }

    @Test
    fun givenLineWithoutSlash_whenFiltering_thenItIsDropped() {
        val kept = filterMatLines(listOf("com.example.Main", "com.example/com.example.Main"))

        assertThat(kept).containsExactly("com.example/com.example.Main")
    }

    @Test
    fun givenCommentLineWithoutSlash_whenFiltering_thenItIsDropped() {
        val kept = filterMatLines(listOf("# exported by MAT", "com.example/.Main"))

        assertThat(kept).containsExactly("com.example/.Main")
    }

    @Test
    fun givenCommentLineWithSlash_whenFiltering_thenItIsKeptBecauseCommentsAreNotRecognised() {
        val kept = filterMatLines(listOf("# see https://example.com", "com.example/.Main"))

        assertThat(kept).containsExactly("# see https://example.com", "com.example/.Main").inOrder()
    }

    @Test
    fun givenLineWithLeadingWhitespace_whenFiltering_thenItIsKeptUntrimmed() {
        val kept = filterMatLines(listOf("  com.example/.Main"))

        assertThat(kept).containsExactly("  com.example/.Main")
    }

    @Test
    fun givenPackageAndFullComponentName_whenParsing_thenSplitsOnSlash() {
        val component = parseMatLine("com.example/com.example.ui.Main")

        assertThat(component).isEqualTo(MatComponent("com.example", "com.example.ui.Main"))
    }

    @Test
    fun givenLeadingDotComponentName_whenParsing_thenDotIsKeptAndNotExpanded() {
        val component = parseMatLine("com.example/.ui.Main")

        assertThat(component).isEqualTo(MatComponent("com.example", ".ui.Main"))
    }

    @Test
    fun givenExtraTokensAfterSpace_whenParsing_thenTheyAreIgnored() {
        val component = parseMatLine("com.example/.Main  disabled by user")

        assertThat(component).isEqualTo(MatComponent("com.example", ".Main"))
    }

    @Test
    fun givenSecondSlashInComponentName_whenParsing_thenTextAfterItIsDropped() {
        val component = parseMatLine("com.example/Outer/Inner")

        assertThat(component).isEqualTo(MatComponent("com.example", "Outer"))
    }

    @Test
    fun givenEmptyComponentName_whenParsing_thenNameIsEmpty() {
        val component = parseMatLine("com.example/")

        assertThat(component).isEqualTo(MatComponent("com.example", ""))
    }

    @Test
    fun givenEmptyPackage_whenParsing_thenPackageIsEmpty() {
        val component = parseMatLine("/.Main")

        assertThat(component).isEqualTo(MatComponent("", ".Main"))
    }

    @Test
    fun givenSlashOnlyAfterFirstSpace_whenParsing_thenThrowsIndexOutOfBounds() {
        // The filter keeps this line because it contains "/", but the first token has none.
        // The worker lets the exception escape, which it reports as MISSING_ROOT_PERMISSION.
        val line = "com.example see/https"

        assertThat(filterMatLines(listOf(line))).containsExactly(line)
        assertThrows(IndexOutOfBoundsException::class.java) { parseMatLine(line) }
    }

    @Test
    fun givenLeadingSpaceBeforeSlash_whenParsing_thenThrowsIndexOutOfBounds() {
        assertThrows(IndexOutOfBoundsException::class.java) { parseMatLine(" com.example/.Main") }
    }
}
