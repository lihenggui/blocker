/*
 * Copyright 2023 The Android Open Source Project
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

package com.merxury.blocker.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.merxury.blocker.lint.designsystem.DesignSystemDetector.Companion.ISSUE
import com.merxury.blocker.lint.designsystem.DesignSystemDetector.Companion.METHOD_NAMES
import org.junit.Test

class DesignSystemDetectorTest {
    @Test
    fun whenCallingMaterial3Composables_thenSuggestsBlockerReplacements() {
        lint()
            .issues(ISSUE)
            .allowMissingSdk()
            .files(
                COMPOSABLE_STUB,
                MATERIAL3_STUB,
                @Suppress("LintImplTrimIndent")
                kotlin(
                    """
                    |import androidx.compose.material3.*
                    |import androidx.compose.runtime.Composable
                    |
                    |@Composable
                    |fun App() {
                    ${METHOD_NAMES.keys.joinToString("\n") { "|    $it()" }}
                    |}
                    """.trimMargin(),
                ).indented(),
            ).run()
            .expect(
                """
                src/test.kt:6: Error: Using MaterialTheme instead of BlockerTheme [DesignSystem]
                    MaterialTheme()
                    ~~~~~~~~~~~~~~~
                src/test.kt:7: Error: Using Button instead of BlockerButton [DesignSystem]
                    Button()
                    ~~~~~~~~
                src/test.kt:8: Error: Using OutlinedButton instead of BlockerOutlinedButton [DesignSystem]
                    OutlinedButton()
                    ~~~~~~~~~~~~~~~~
                src/test.kt:9: Error: Using TextButton instead of BlockerTextButton [DesignSystem]
                    TextButton()
                    ~~~~~~~~~~~~
                src/test.kt:10: Error: Using FilterChip instead of BlockerFilterChip [DesignSystem]
                    FilterChip()
                    ~~~~~~~~~~~~
                src/test.kt:11: Error: Using ElevatedFilterChip instead of BlockerFilterChip [DesignSystem]
                    ElevatedFilterChip()
                    ~~~~~~~~~~~~~~~~~~~~
                src/test.kt:12: Error: Using DropdownMenu instead of BlockerDropdownMenu [DesignSystem]
                    DropdownMenu()
                    ~~~~~~~~~~~~~~
                src/test.kt:13: Error: Using NavigationBar instead of BlockerNavigationSuiteScaffold [DesignSystem]
                    NavigationBar()
                    ~~~~~~~~~~~~~~~
                src/test.kt:14: Error: Using NavigationBarItem instead of BlockerNavigationSuiteScaffold [DesignSystem]
                    NavigationBarItem()
                    ~~~~~~~~~~~~~~~~~~~
                src/test.kt:15: Error: Using NavigationRail instead of BlockerNavigationSuiteScaffold [DesignSystem]
                    NavigationRail()
                    ~~~~~~~~~~~~~~~~
                src/test.kt:16: Error: Using NavigationRailItem instead of BlockerNavigationSuiteScaffold [DesignSystem]
                    NavigationRailItem()
                    ~~~~~~~~~~~~~~~~~~~~
                src/test.kt:17: Error: Using TabRow instead of BlockerTabRow [DesignSystem]
                    TabRow()
                    ~~~~~~~~
                src/test.kt:18: Error: Using Tab instead of BlockerTab [DesignSystem]
                    Tab()
                    ~~~~~
                src/test.kt:19: Error: Using IconToggleButton instead of BlockerIconToggleButton [DesignSystem]
                    IconToggleButton()
                    ~~~~~~~~~~~~~~~~~~
                src/test.kt:20: Error: Using FilledIconToggleButton instead of BlockerIconToggleButton [DesignSystem]
                    FilledIconToggleButton()
                    ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test.kt:21: Error: Using FilledTonalIconToggleButton instead of BlockerIconToggleButton [DesignSystem]
                    FilledTonalIconToggleButton()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test.kt:22: Error: Using OutlinedIconToggleButton instead of BlockerIconToggleButton [DesignSystem]
                    OutlinedIconToggleButton()
                    ~~~~~~~~~~~~~~~~~~~~~~~~~~
                src/test.kt:23: Error: Using CenterAlignedTopAppBar instead of BlockerTopAppBar [DesignSystem]
                    CenterAlignedTopAppBar()
                    ~~~~~~~~~~~~~~~~~~~~~~~~
                src/test.kt:24: Error: Using SmallTopAppBar instead of BlockerTopAppBar [DesignSystem]
                    SmallTopAppBar()
                    ~~~~~~~~~~~~~~~~
                src/test.kt:25: Error: Using MediumTopAppBar instead of BlockerTopAppBar [DesignSystem]
                    MediumTopAppBar()
                    ~~~~~~~~~~~~~~~~~
                src/test.kt:26: Error: Using LargeTopAppBar instead of BlockerTopAppBar [DesignSystem]
                    LargeTopAppBar()
                    ~~~~~~~~~~~~~~~~
                src/test.kt:27: Error: Using Switch instead of BlockerSwitch [DesignSystem]
                    Switch()
                    ~~~~~~~~
                22 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun whenCallingMaterialButton_thenSuggestsBlockerButton() {
        lint()
            .issues(ISSUE)
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package androidx.compose.material

                    fun Button() = Unit
                    """.trimIndent(),
                ).indented(),
                kotlin(
                    """
                    import androidx.compose.material.Button

                    fun app() = Button()
                    """.trimIndent(),
                ).indented(),
            ).run()
            .expectErrorCount(1)
            .expectContains("Using Button instead of BlockerButton [DesignSystem]")
    }

    @Test
    fun whenReferencingMaterialIconThroughIcons_thenReportsOnce() {
        lint()
            .issues(ISSUE)
            .allowMissingSdk()
            .files(
                MATERIAL_ICONS_STUB,
                kotlin(
                    """
                    import androidx.compose.material.icons.Icons

                    fun app() = Icons.Filled.Home
                    """.trimIndent(),
                ).indented(),
            ).run()
            .expectErrorCount(1)
            .expectContains("Using Icons instead of BlockerIcons [DesignSystem]")
    }

    @Test
    fun givenImportedMaterialIconGroup_whenReferencingItsIcon_thenSuggestsBlockerIcons() {
        lint()
            .issues(ISSUE)
            .allowMissingSdk()
            .files(
                MATERIAL_ICONS_STUB,
                kotlin(
                    """
                    import androidx.compose.material.icons.Icons.Filled

                    fun app() = Filled.Home
                    """.trimIndent(),
                ).indented(),
            ).run()
            .expectErrorCount(1)
            .expectContains("Using Icons instead of BlockerIcons [DesignSystem]")
    }

    @Test
    fun whenUsingUnrelatedButtonAndIcons_thenReportsNoIssues() {
        lint()
            .issues(ISSUE)
            .allowMissingSdk()
            .files(
                kotlin(
                    """
                    package example.widgets

                    fun Button() = Unit

                    object Icons {
                        object Filled {
                            val Home = Unit
                        }
                    }
                    """.trimIndent(),
                ).indented(),
                kotlin(
                    """
                    import example.widgets.Button
                    import example.widgets.Icons

                    fun app() {
                        Button()
                        Icons.Filled.Home
                    }
                    """.trimIndent(),
                ).indented(),
            ).run()
            .expectClean()
    }

    @Test
    fun givenDesignSystemWrapper_whenCallingMaterialButton_thenReportsNoIssues() {
        lint()
            .issues(ISSUE)
            .allowMissingSdk()
            .files(
                COMPOSABLE_STUB,
                MATERIAL3_STUB,
                kotlin(
                    """
                    package com.merxury.blocker.core.designsystem.component

                    import androidx.compose.material3.Button

                    fun BlockerButton() = Button()
                    """.trimIndent(),
                ).indented(),
            ).run()
            .expectClean()
    }

    private companion object {
        private val COMPOSABLE_STUB: TestFile =
            kotlin(
                """
                package androidx.compose.runtime

                annotation class Composable
                """.trimIndent(),
            ).indented()

        private val MATERIAL3_STUB: TestFile =
            kotlin(
                """
                |package androidx.compose.material3
                |
                |import androidx.compose.runtime.Composable
                |
                ${METHOD_NAMES.keys.joinToString("\n") { "|@Composable fun $it() = Unit" }}
                """.trimMargin(),
            ).indented()

        private val MATERIAL_ICONS_STUB: TestFile =
            kotlin(
                """
                package androidx.compose.material.icons

                object Icons {
                    object Filled {
                        val Home = Unit
                    }
                }
                """.trimIndent(),
            ).indented()
    }
}
