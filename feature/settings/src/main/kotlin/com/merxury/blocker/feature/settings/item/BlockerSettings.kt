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

package com.merxury.blocker.feature.settings.item

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.data.UserEditableSettings
import com.merxury.blocker.core.model.preference.DarkThemeConfig.FOLLOW_SYSTEM
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.feature.settings.R.string
import java.util.Locale

@Composable
fun BlockerSettings(
    settings: UserEditableSettings,
    onChangeControllerType: (ControllerType) -> Unit = {},
    onChangeRuleServerProvider: (RuleServerProvider) -> Unit = {},
    onChangeAppDisplayLanguage: (String) -> Unit = {},
    onChangeRuleDisplayLanguage: (String) -> Unit = {},
) {
    Column {
        DialogSettingsItems(
            icon = ImageVectorIcon(BlockerIcons.AutoFix),
            titleRes = string.feature_settings_controller_type,
            selectedItem = settings.controllerType,
            itemList = listOf(
                IFW to string.feature_settings_intent_firewall,
                PM to string.feature_settings_package_manager,
                SHIZUKU to string.feature_settings_shizuku,
            ),
            onValueChange = onChangeControllerType,
        )
        DialogSettingsItems(
            icon = ImageVectorIcon(BlockerIcons.Block),
            titleRes = string.feature_settings_online_rule_source,
            selectedItem = settings.ruleServerProvider,
            itemList = listOf(
                GITHUB to string.feature_settings_options_github,
                GITLAB to string.feature_settings_options_gitlab,
            ),
            onValueChange = onChangeRuleServerProvider,
        )
        DialogSettingsItems(
            icon = ImageVectorIcon(BlockerIcons.Language),
            titleRes = string.feature_settings_display_language,
            selectedItem = settings.appDisplayLanguage,
            itemList = listOf(
                "" to string.feature_settings_follow_system,
                Locale.ENGLISH.toLanguageTag() to string.feature_settings_english,
                Locale.SIMPLIFIED_CHINESE.toLanguageTag() to string.feature_settings_simplifed_chinese,
                Locale.TRADITIONAL_CHINESE.toLanguageTag() to string.feature_settings_traditional_chinese,
                Locale("ru").toLanguageTag() to string.feature_settings_russian,
            ),
            onValueChange = onChangeAppDisplayLanguage,
        )
        DialogSettingsItems(
            icon = ImageVectorIcon(BlockerIcons.Translate),
            titleRes = string.feature_settings_library_language,
            selectedItem = settings.appDisplayLanguage,
            itemList = listOf(
                "" to string.feature_settings_follow_system,
                Locale.ENGLISH.toLanguageTag() to string.feature_settings_english,
                Locale.SIMPLIFIED_CHINESE.toLanguageTag() to string.feature_settings_simplifed_chinese,
            ),
            onValueChange = onChangeRuleDisplayLanguage,
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BlockerSettingsPreview() {
    BlockerTheme {
        Surface {
            BlockerSettings(
                settings = UserEditableSettings(
                    darkThemeConfig = FOLLOW_SYSTEM,
                    useDynamicColor = false,
                ),
            )
        }
    }
}
