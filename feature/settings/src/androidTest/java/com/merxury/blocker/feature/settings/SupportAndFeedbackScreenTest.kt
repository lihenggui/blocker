/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.merxury.blocker.feature.helpandfeedback.SupportAndFeedbackScreen
import com.merxury.blocker.feature.settings.R.string
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SupportAndFeedbackScreenTest {
    @get:Rule(order = 0)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var projectHomepage: String
    private lateinit var ruleRepository: String
    private lateinit var reportBugsOrSubmitIdeas: String
    private lateinit var telegramGroup: String
    private lateinit var designersHomepage: String
    private lateinit var openSourceLicenses: String

    @Before
    fun setup() {
        composeTestRule.activity.apply {
            projectHomepage = getString(string.feature_settings_project_homepage)
            ruleRepository = getString(string.feature_settings_rule_repository)
            reportBugsOrSubmitIdeas = getString(string.feature_settings_report_bugs_or_submit_ideas)
            telegramGroup = getString(string.feature_settings_telegram_group)
            designersHomepage = getString(string.feature_settings_designers_homepage)
            openSourceLicenses = getString(string.feature_settings_open_source_licenses)
        }
    }

    @Test
    fun showScreen() {
        composeTestRule.setContent {
            SupportAndFeedbackScreen()
        }

        composeTestRule.onNodeWithText(projectHomepage).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(ruleRepository).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(reportBugsOrSubmitIdeas).assertExists()
            .assertHasClickAction()
        composeTestRule.onNodeWithText(telegramGroup).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(designersHomepage).assertExists().assertHasClickAction()
        composeTestRule.onNodeWithText(openSourceLicenses).assertExists().assertHasClickAction()
    }
}
