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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

@Composable
fun BlockerBodySmallText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        maxLines = maxLines,
        style = style,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        color = color,
    )
}

@Composable
fun BlockerBodyMediumText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight? = FontWeight(400),
) {
    Text(
        text = text,
        maxLines = maxLines,
        style = style,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        fontSize = fontSize,
        color = color,
        fontWeight = fontWeight,
    )
}

@Composable
fun BlockerBodyLargeText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        maxLines = maxLines,
        style = style,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        fontSize = fontSize,
        color = color,
    )
}

@Composable
fun BlockerLabelSmallText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        maxLines = maxLines,
        style = style,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
    )
}

@PreviewThemes
@Composable
private fun BlockerBodySmallTextPreview() {
    BlockerTheme {
        Surface {
            Column {
                BlockerBodySmallText(text = "Body small")
                BlockerBodyMediumText(text = "Body medium")
                BlockerBodyLargeText(text = "Body large")
            }
        }
    }
}
