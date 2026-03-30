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

package com.merxury.blocker.feature.globalifwrule.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.AdvancedGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.GlobalIfwRuleUiState
import com.merxury.blocker.core.model.data.SimpleGlobalIfwRuleDraft
import com.merxury.blocker.core.model.data.SimpleTargetMode
import com.merxury.blocker.core.testing.util.DefaultTestDevices
import com.merxury.blocker.core.testing.util.captureForDevice
import com.merxury.blocker.core.testing.util.captureMultiDevice
import com.merxury.blocker.core.ui.previewparameter.GlobalIfwRulePreviewParameterData
import com.merxury.core.ifw.model.IfwIntentFilter
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = HiltTestApplication::class)
@LooperMode(LooperMode.Mode.PAUSED)
class GlobalIfwRuleScreenScreenshotTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @Test
    fun globalIfwRuleScreen() {
        composeTestRule.captureMultiDevice("GlobalIfwRuleScreen") {
            GlobalIfwRuleListScreen(
                uiState = GlobalIfwRuleUiState.Success(
                    groups = GlobalIfwRulePreviewParameterData.packageRuleGroups,
                ),
            )
        }
    }

    @Test
    fun globalIfwRuleScreen_dark() {
        capturePhoneDark("GlobalIfwRuleScreen") {
            GlobalIfwRuleListScreen(
                uiState = GlobalIfwRuleUiState.Success(
                    groups = GlobalIfwRulePreviewParameterData.packageRuleGroups,
                ),
            )
        }
    }

    @Test
    fun globalIfwRuleScreenEmpty() {
        composeTestRule.captureMultiDevice("GlobalIfwRuleScreenEmpty") {
            GlobalIfwRuleListScreen(
                uiState = GlobalIfwRuleUiState.Success(groups = emptyList()),
            )
        }
    }

    @Test
    fun globalIfwRuleScreenEmpty_dark() {
        capturePhoneDark("GlobalIfwRuleScreenEmpty") {
            GlobalIfwRuleListScreen(
                uiState = GlobalIfwRuleUiState.Success(groups = emptyList()),
            )
        }
    }

    @Test
    fun globalIfwRuleScreenLoading() {
        composeTestRule.captureMultiDevice("GlobalIfwRuleScreenLoading") {
            GlobalIfwRuleListScreen(uiState = GlobalIfwRuleUiState.Loading)
        }
    }

    @Test
    fun globalIfwRuleScreenLoading_dark() {
        capturePhoneDark("GlobalIfwRuleScreenLoading") {
            GlobalIfwRuleListScreen(uiState = GlobalIfwRuleUiState.Loading)
        }
    }

    @Test
    fun globalIfwRuleScreenError() {
        composeTestRule.captureMultiDevice("GlobalIfwRuleScreenError") {
            GlobalIfwRuleListScreen(
                uiState = GlobalIfwRuleUiState.Error(
                    message = "Failed to load IFW rules from local storage.",
                ),
            )
        }
    }

    @Test
    fun globalIfwRuleScreenError_dark() {
        capturePhoneDark("GlobalIfwRuleScreenError") {
            GlobalIfwRuleListScreen(
                uiState = GlobalIfwRuleUiState.Error(
                    message = "Failed to load IFW rules from local storage.",
                ),
            )
        }
    }

    @Test
    fun simpleGlobalIfwRuleScreenAdd() {
        val draft = simpleAddDraft()
        composeTestRule.captureMultiDevice("SimpleGlobalIfwRuleScreenAdd") {
            SimpleRuleEditorScreen(
                draft = draft,
                visibleComponents = GlobalIfwRulePreviewParameterData.simpleRuleComponents(
                    selectedTargets = draft.targets.toSet(),
                ),
            )
        }
    }

    @Test
    fun simpleGlobalIfwRuleScreenAdd_dark() {
        val draft = simpleAddDraft()
        capturePhoneDark("SimpleGlobalIfwRuleScreenAdd") {
            SimpleRuleEditorScreen(
                draft = draft,
                visibleComponents = GlobalIfwRulePreviewParameterData.simpleRuleComponents(
                    selectedTargets = draft.targets.toSet(),
                ),
            )
        }
    }

    @Test
    fun simpleGlobalIfwRuleScreenLoadError() {
        composeTestRule.captureMultiDevice("SimpleGlobalIfwRuleScreenLoadError") {
            SimpleRuleEditorScreen(
                draft = simpleLoadErrorDraft(),
                visibleComponents = emptyList(),
                componentLoadError = "Failed to load broadcast receivers.",
            )
        }
    }

    @Test
    fun simpleGlobalIfwRuleScreenLoadError_dark() {
        capturePhoneDark("SimpleGlobalIfwRuleScreenLoadError") {
            SimpleRuleEditorScreen(
                draft = simpleLoadErrorDraft(),
                visibleComponents = emptyList(),
                componentLoadError = "Failed to load broadcast receivers.",
            )
        }
    }

    @Test
    fun advancedGlobalIfwRuleScreenAdd() {
        composeTestRule.captureMultiDevice("AdvancedGlobalIfwRuleScreenAdd") {
            AdvancedRuleEditorScreen(
                draft = GlobalIfwRulePreviewParameterData.advancedRuleDraft,
            )
        }
    }

    @Test
    fun advancedGlobalIfwRuleScreenAdd_dark() {
        capturePhoneDark("AdvancedGlobalIfwRuleScreenAdd") {
            AdvancedRuleEditorScreen(
                draft = GlobalIfwRulePreviewParameterData.advancedRuleDraft,
            )
        }
    }

    @Test
    fun advancedGlobalIfwRuleScreenEdit() {
        composeTestRule.captureMultiDevice("AdvancedGlobalIfwRuleScreenEdit") {
            AdvancedRuleEditorScreen(
                draft = advancedEditDraft(),
                isDirty = true,
            )
        }
    }

    @Test
    fun advancedGlobalIfwRuleScreenEdit_dark() {
        capturePhoneDark("AdvancedGlobalIfwRuleScreenEdit") {
            AdvancedRuleEditorScreen(
                draft = advancedEditDraft(),
                isDirty = true,
            )
        }
    }

    @Test
    fun advancedGlobalIfwRuleDetailScreen() {
        composeTestRule.captureMultiDevice("AdvancedGlobalIfwRuleDetailScreen") {
            AdvancedRuleDetailScreen()
        }
    }

    @Test
    fun advancedGlobalIfwRuleDetailScreen_dark() {
        capturePhoneDark("AdvancedGlobalIfwRuleDetailScreen") {
            AdvancedRuleDetailScreen()
        }
    }

    private fun capturePhoneDark(
        screenshotName: String,
        body: @Composable () -> Unit,
    ) {
        composeTestRule.captureForDevice(
            deviceName = "phone_dark",
            deviceSpec = DefaultTestDevices.PHONE.spec,
            screenshotName = screenshotName,
            darkMode = true,
            body = body,
        )
    }

    private fun simpleAddDraft(): SimpleGlobalIfwRuleDraft = SimpleGlobalIfwRuleDraft(
        selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
        targets = listOf(GlobalIfwRulePreviewParameterData.PREVIEW_BOOT_RECEIVER_NAME),
        block = true,
        log = false,
    )

    private fun simpleLoadErrorDraft(): SimpleGlobalIfwRuleDraft = SimpleGlobalIfwRuleDraft(
        selectedPackageName = GlobalIfwRulePreviewParameterData.PREVIEW_SIMPLE_RULE_PACKAGE_NAME,
        targetMode = SimpleTargetMode.MULTIPLE,
        targets = listOf(
            GlobalIfwRulePreviewParameterData.PREVIEW_BOOT_RECEIVER_NAME,
            GlobalIfwRulePreviewParameterData.PREVIEW_ALARM_RECEIVER_NAME,
        ),
        block = true,
        log = true,
        action = "android.intent.action.BOOT_COMPLETED",
        category = "android.intent.category.DEFAULT",
        callerPackage = "android",
        editingRuleIndex = 1,
    )

    private fun advancedEditDraft(): AdvancedGlobalIfwRuleDraft = GlobalIfwRulePreviewParameterData.advancedRuleDraft.copy(
        intentFilters = listOf(
            IfwIntentFilter(
                actions = listOf("android.intent.action.SEND"),
                categories = listOf("android.intent.category.DEFAULT"),
            ),
        ),
        editingRuleIndex = 2,
    )

    @Composable
    private fun GlobalIfwRuleListScreen(
        uiState: GlobalIfwRuleUiState,
    ) {
        BlockerTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                GlobalIfwRuleScreen(
                    uiState = uiState,
                    onAddSimpleRuleClick = {},
                    onAddAdvancedRuleClick = {},
                    onOpenRuleClick = { _, _ -> },
                    onDeleteRule = { _, _ -> },
                )
            }
        }
    }

    @Composable
    private fun SimpleRuleEditorScreen(
        draft: SimpleGlobalIfwRuleDraft,
        visibleComponents: List<com.merxury.blocker.core.model.data.SimpleRuleComponentUiState>,
        componentLoadError: String? = null,
    ) {
        BlockerTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                SimpleGlobalIfwRuleScreen(
                    draft = draft,
                    isDirty = false,
                    selectedPackageLabel = "Spotify",
                    componentQuery = "",
                    visibleComponents = visibleComponents,
                    isComponentLoading = false,
                    componentLoadError = componentLoadError,
                    onSave = {},
                    onBack = {},
                    onPackageNameChange = {},
                    onComponentTypeChange = {},
                    onTargetModeChange = {},
                    onBlockChange = {},
                    onLogChange = {},
                    onActionChange = {},
                    onCategoryChange = {},
                    onCallerPackageChange = {},
                    onComponentQueryChange = {},
                    onSelectSingleTarget = {},
                    onToggleMultiTarget = {},
                )
            }
        }
    }

    @Composable
    private fun AdvancedRuleEditorScreen(
        draft: AdvancedGlobalIfwRuleDraft,
        isDirty: Boolean = false,
    ) {
        BlockerTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                AdvancedGlobalIfwRuleScreen(
                    draft = draft,
                    isDirty = isDirty,
                    onSave = {},
                    onBack = {},
                    onPackageNameChange = {},
                    onComponentTypeChange = {},
                    onBlockChange = {},
                    onLogChange = {},
                    onRootGroupChange = {},
                )
            }
        }
    }

    @Composable
    private fun AdvancedRuleDetailScreen() {
        BlockerTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                AdvancedGlobalIfwRuleDetailScreen(
                    detail = GlobalIfwRulePreviewParameterData.advancedRuleDetail,
                    onBack = {},
                    onCopyAsNew = {},
                    onDelete = {},
                )
            }
        }
    }
}
