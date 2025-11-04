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

package com.merxury.blocker.feature.debloater

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.component.NoComponentScreen
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.screen.ErrorScreen
import com.merxury.blocker.core.ui.screen.LoadingScreen

@Composable
fun DebloaterContent(
    modifier: Modifier = Modifier,
    data: Result<List<MatchedTarget>> = Result.Loading,
    onBlockAllInItemClick: (List<DebloatableComponentUiItem>) -> Unit = { _ -> },
    onEnableAllInItemClick: (List<DebloatableComponentUiItem>) -> Unit = { _ -> },
    onSwitch: (DebloatableComponentUiItem, Boolean) -> Unit = { _, _ -> },
) {
    when (data) {
        is Result.Success -> {
            val debloatableApps = data.data
            if (debloatableApps.isEmpty()) {
                NoComponentScreen()
                return
            }
            DebloatableAppList(
                modifier = modifier.testTag("debloater:targetList"),
                list = debloatableApps,
                onBlockAllInItemClick = onBlockAllInItemClick,
                onEnableAllInItemClick = onEnableAllInItemClick,
                onSwitch = onSwitch,
            )
        }

        is Result.Error -> ErrorScreen(error = UiMessage(title = data.exception.message.orEmpty()))

        is Result.Loading -> LoadingScreen()
    }
}
