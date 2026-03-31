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

package com.merxury.blocker.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.testing.util.DefaultRoborazziOptions
import com.merxury.blocker.core.testing.util.captureMultiTheme
import com.merxury.blocker.core.ui.ifwruleeditor.BlockMode
import com.merxury.blocker.core.ui.ifwruleeditor.ConditionEditorCard
import com.merxury.blocker.core.ui.ifwruleeditor.ConditionPickerSheet
import com.merxury.blocker.core.ui.ifwruleeditor.GroupEditorCard
import com.merxury.blocker.core.ui.ifwruleeditor.IfwRuleTreeEditor
import com.merxury.blocker.core.ui.ifwruleeditor.RuleEditorUiState
import com.merxury.core.ifw.editor.IfwEditorConditionKind
import com.merxury.core.ifw.editor.IfwEditorGroupMode
import com.merxury.core.ifw.editor.IfwEditorNode
import com.merxury.core.ifw.editor.IfwEditorPortMode
import com.merxury.core.ifw.editor.IfwEditorStringMatcherMode
import com.merxury.core.ifw.model.IfwComponentType
import com.merxury.core.ifw.model.SenderType
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class, qualifiers = "480dpi")
@LooperMode(LooperMode.Mode.PAUSED)
class IfwRuleEditorScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun treeEditor_multipleThemes() {
        composeTestRule.captureMultiTheme("IfwRuleEditor", "TreeEditor") {
            Surface {
                TreeEditorExample()
            }
        }
    }

    @Test
    fun treeEditor_hugeFont() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
            ) {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.Companion.FontScale(2f),
                ) {
                    BlockerTheme {
                        Surface {
                            TreeEditorExample()
                        }
                    }
                }
            }
        }

        composeTestRule.onRoot()
            .captureRoboImage(
                "src/test/screenshots/IfwRuleEditor/TreeEditor_fontScale2.png",
                roborazziOptions = DefaultRoborazziOptions,
            )
    }

    @Test
    fun portConditionCard_multipleThemes() {
        composeTestRule.captureMultiTheme("IfwRuleEditor", "PortConditionCard") {
            Surface {
                PortConditionCardExample()
            }
        }
    }

    @Test
    fun groupEditorCard_multipleThemes() {
        composeTestRule.captureMultiTheme("IfwRuleEditor", "GroupEditorCard") {
            Surface {
                GroupEditorCardExample()
            }
        }
    }

    @Test
    fun conditionPickerSheet_multipleThemes() {
        composeTestRule.captureMultiTheme("IfwRuleEditor", "ConditionPickerSheet") {
            ConditionPickerSheet(
                onDismiss = {},
                onSelect = {},
            )
        }
    }

    @Composable
    private fun TreeEditorExample() {
        IfwRuleTreeEditor(
            rootGroup = conditionalEditorState().rootGroup,
            onChange = {},
        )
    }

    @Composable
    private fun PortConditionCardExample() {
        ConditionEditorCard(
            condition = IfwEditorNode.Condition(
                kind = IfwEditorConditionKind.PORT,
                portMode = IfwEditorPortMode.RANGE,
                minPort = 1024,
                maxPort = 65535,
            ),
            depth = 0,
            onUpdate = {},
            onDelete = {},
        )
    }

    @Composable
    private fun GroupEditorCardExample() {
        GroupEditorCard(
            group = conditionalEditorState().rootGroup,
            depth = 0,
            isRoot = true,
            onUpdate = {},
            onDelete = {},
            onAddGroup = {},
            onAddCondition = {},
        )
    }

    private fun conditionalEditorState(): RuleEditorUiState = RuleEditorUiState(
        packageName = "com.example.social",
        componentName = "com.example.social.ShareReceiver",
        componentType = IfwComponentType.BROADCAST,
        blockMode = BlockMode.CONDITIONAL,
        log = false,
        blockEnabled = false,
        rootGroup = IfwEditorNode.Group(
            mode = IfwEditorGroupMode.ALL,
            children = listOf(
                IfwEditorNode.Condition(
                    kind = IfwEditorConditionKind.ACTION,
                    matcherMode = IfwEditorStringMatcherMode.EXACT,
                    value = "android.intent.action.SEND",
                ),
                IfwEditorNode.Group(
                    mode = IfwEditorGroupMode.ANY,
                    children = listOf(
                        IfwEditorNode.Condition(
                            kind = IfwEditorConditionKind.CALLER_TYPE,
                            senderType = SenderType.SYSTEM,
                            excluded = true,
                        ),
                        IfwEditorNode.Condition(
                            kind = IfwEditorConditionKind.HOST,
                            matcherMode = IfwEditorStringMatcherMode.CONTAINS,
                            value = "example.com",
                        ),
                    ),
                ),
            ),
        ),
    )
}
