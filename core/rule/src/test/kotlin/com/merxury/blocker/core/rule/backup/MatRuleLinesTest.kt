// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.rule.backup

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.ComponentId
import org.junit.Test

/** Pins which lines of a MAT (.txt) export MatRuleImporter keeps and how it splits them. */
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
    fun givenSlashOnlyAfterFirstSpace_whenParsing_thenNoComponent() {
        // The filter keeps this line because it contains "/", but the first token has none.
        val line = "com.example see/https"

        assertThat(filterMatLines(listOf(line))).containsExactly(line)
        assertThat(parseMatLine(line)).isNull()
    }

    @Test
    fun givenLeadingSpaceBeforeSlash_whenParsing_thenNoComponent() {
        assertThat(parseMatLine(" com.example/.Main")).isNull()
    }

    @Test
    fun givenMalformedLines_whenParsingTheExport_thenTheyAreCountedAndTheRestResolved() {
        val parsed = parseMatLines(
            listOf(
                "# exported by MAT",
                "com.example/.Main",
                " com.example/.Leading",
                "com.example see/https",
                "/.NoPackage",
                "com.example/",
                "com.example/com.example.Sync extra",
                "com.example/.Main",
            ),
        )

        assertThat(parsed.malformed).isEqualTo(4)
        assertThat(parsed.components).containsExactly(
            ComponentId("com.example", "com.example.Main"),
            ComponentId("com.example", "com.example.Sync"),
        ).inOrder()
    }
}
