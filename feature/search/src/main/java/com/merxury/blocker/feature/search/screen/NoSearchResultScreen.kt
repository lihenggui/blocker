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

package com.merxury.blocker.feature.search.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme
import com.merxury.blocker.feature.search.R.string

@Composable
fun NoSearchResultScreen() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = BlockerIcons.Inbox,
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .padding(8.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(id = string.no_search_result),
            color = MaterialTheme.colorScheme.outline,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
@Preview
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
private fun NoSearchResultScreenPreview() {
    BlockerTheme {
        Surface {
            NoSearchResultScreen()
        }
    }
}
