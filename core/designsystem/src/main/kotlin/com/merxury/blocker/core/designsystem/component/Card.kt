/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

@Composable
fun BlockerOutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        onClick = onClick,
    ) {
        content()
    }
}

@PreviewThemes
@Composable
private fun BlockerOutlinedCardPreview() {
    BlockerTheme {
        Surface {
            BlockerOutlinedCard(
                onClick = {},
            ) {
                Column(Modifier.padding(16.dp)) {
                    BlockerBodyLargeText(
                        text = "title",
                    )
                    BlockerBodyMediumText(
                        text = "description",
                    )
                }
            }
        }
    }
}
