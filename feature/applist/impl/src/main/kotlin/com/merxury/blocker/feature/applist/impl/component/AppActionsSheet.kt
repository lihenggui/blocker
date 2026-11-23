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

package com.merxury.blocker.feature.applist.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.component.BlockerBodyLargeText
import com.merxury.blocker.core.designsystem.component.BlockerBodyMediumText
import com.merxury.blocker.core.designsystem.component.PreviewThemes
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.core.ui.applist.AppIcon
import com.merxury.blocker.feature.applist.api.R.string
import com.merxury.blocker.feature.applist.impl.AppAction
import com.merxury.blocker.feature.applist.impl.AppRow
import com.merxury.blocker.core.ui.R.string as uiString

/**
 * The actions on an app of another user, shown when the app is tapped, since the app detail
 * screen does not show other users' apps yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppActionsSheet(
    app: AppRow,
    onAction: (AppAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        AppActionsContent(
            app = app,
            onAction = { action ->
                onDismissRequest()
                onAction(action)
            },
        )
    }
}

@Composable
internal fun AppActionsContent(
    app: AppRow,
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().testTag("appList:actionsSheet")) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            AppIcon(app.icon, Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            BlockerBodyLargeText(text = app.label)
        }
        BlockerBodyMediumText(
            text = stringResource(string.feature_applist_api_other_profile_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        actionsFor(app).forEach { (action, label) ->
            BlockerBodyLargeText(
                text = stringResource(label),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAction(action) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** The same actions, in the same order, as the menu of a row. */
private fun actionsFor(app: AppRow): List<Pair<AppAction, Int>> = buildList {
    if (app.isRunning != false) add(AppAction.FORCE_STOP to uiString.core_ui_force_stop)
    if (app.isDisabled) {
        add(AppAction.ENABLE to uiString.core_ui_enable)
    } else {
        add(AppAction.DISABLE to uiString.core_ui_disable)
    }
    add(AppAction.CLEAR_CACHE to uiString.core_ui_clear_cache)
    add(AppAction.CLEAR_DATA to uiString.core_ui_clear_data)
    add(AppAction.UNINSTALL to uiString.core_ui_uninstall)
}

@PreviewThemes
@Composable
private fun AppActionsContentPreview() {
    BlockerTheme {
        Surface {
            AppActionsContent(app = previewAppRows().first(), onAction = {})
        }
    }
}
