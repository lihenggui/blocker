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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.PM
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.preference.DarkThemeConfig.FOLLOW_SYSTEM
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITHUB
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.core.model.preference.ThemeBrand.ANDROID
import com.merxury.blocker.feature.settings.R.string
import com.merxury.blocker.feature.settings.UserEditableSettings

@Composable
fun BlockerSettings(
    modifier: Modifier = Modifier,
    settings: UserEditableSettings,
    onChangeControllerType: (ControllerType) -> Unit,
    onChangeRuleServerProvider: (RuleServerProvider) -> Unit,
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
    ) {
        DialogSettingsItems(
            icon = ImageVectorIcon(BlockerIcons.AutoFix),
            titleRes = string.controller_type,
            selectedItem = settings.controllerType,
            itemList = listOf(
                IFW to string.intent_firewall,
                PM to string.package_manager,
                SHIZUKU to string.shizuku,
            ),
            onValueChange = onChangeControllerType,
            paddingValues = PaddingValues(16.dp),
        )
        DialogSettingsItems(
            icon = ImageVectorIcon(BlockerIcons.Block),
            titleRes = string.online_rule_source,
            selectedItem = settings.ruleServerProvider,
            itemList = listOf(
                GITHUB to string.options_github,
                GITLAB to string.options_gitlab,
            ),
            onValueChange = onChangeRuleServerProvider,
            paddingValues = PaddingValues(16.dp),
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
                    themeBrand = ANDROID,
                    darkThemeConfig = FOLLOW_SYSTEM,
                    useDynamicColor = false,
                ),
                onChangeControllerType = {},
                onChangeRuleServerProvider = {},
            )
        }
    }
}
