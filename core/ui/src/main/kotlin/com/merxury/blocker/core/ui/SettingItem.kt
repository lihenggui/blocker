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

package com.merxury.blocker.core.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.icon.Icon
import com.merxury.blocker.core.designsystem.icon.Icon.DrawableResourceIcon
import com.merxury.blocker.core.designsystem.icon.Icon.ImageVectorIcon
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

@Composable
fun ItemHeader(
    title: String,
    modifier: Modifier = Modifier,
    extraIconPadding: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                if (extraIconPadding) {
                    PaddingValues(vertical = 16.dp, horizontal = 56.dp)
                } else {
                    PaddingValues(16.dp)
                },
            ),
        )
    }
}

@Composable
fun BlockerSettingItem(
    title: String,
    modifier: Modifier = Modifier,
    extraIconPadding: Boolean = false,
    summary: String? = null,
    icon: Icon? = null,
    onItemClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(
                if (extraIconPadding && icon == null) {
                    PaddingValues(vertical = 16.dp, horizontal = 56.dp)
                } else {
                    PaddingValues(16.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            when (icon) {
                is ImageVectorIcon -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                )

                is DrawableResourceIcon -> Icon(
                    painter = painterResource(id = icon.id),
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun BlockerSettingItem(
    title: String,
    modifier: Modifier = Modifier,
    extraIconPadding: Boolean = false,
    itemDesc: String? = null,
    itemDesc1: String? = null,
    icon: Icon? = null,
    onItemClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(
                if (extraIconPadding && icon == null) {
                    PaddingValues(vertical = 8.dp, horizontal = 56.dp)
                } else {
                    PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            when (icon) {
                is ImageVectorIcon -> Icon(
                    imageVector = icon.imageVector,
                    contentDescription = null,
                )

                is DrawableResourceIcon -> Icon(
                    painter = painterResource(id = icon.id),
                    contentDescription = null,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (itemDesc != null) {
                Text(
                    text = itemDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (itemDesc1 != null) {
                Text(
                    text = itemDesc1,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun BlockerItem(
    title: String,
    icon: Icon,
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit = {},
    iconColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(
                PaddingValues(vertical = 8.dp),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (icon) {
            is ImageVectorIcon -> Icon(
                imageVector = icon.imageVector,
                contentDescription = title,
                tint = iconColor,
            )

            is DrawableResourceIcon -> Icon(
                painter = painterResource(id = icon.id),
                contentDescription = title,
                tint = iconColor,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun HeaderPreview() {
    BlockerTheme {
        Surface {
            ItemHeader(title = "Normal Header")
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun HeaderWithPaddingPreview() {
    BlockerTheme {
        Surface {
            ItemHeader(
                title = "Header with Extra padding",
                extraIconPadding = true,
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun SingleLineSettingItemPreview() {
    BlockerTheme {
        Surface {
            BlockerSettingItem(title = "Single line item")
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun SingleLineSettingItemWithPaddingPreview() {
    BlockerTheme {
        Surface {
            BlockerSettingItem(
                title = "Single line item with padding",
                extraIconPadding = true,
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun SettingsItemWithoutIconPreview() {
    BlockerTheme {
        Surface {
            BlockerSettingItem(
                title = "Normal setting item",
                summary = "Summary of the item",
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun SettingsItemWithIconPreview() {
    BlockerTheme {
        Surface {
            BlockerSettingItem(
                title = "Setting item with icon",
                summary = "Summary of the item",
                icon = ImageVectorIcon(BlockerIcons.Apps),
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun SettingsItemWithPaddingPreview() {
    BlockerTheme {
        Surface {
            BlockerSettingItem(
                title = "Item with extra space",
                summary = "Summary of the item",
                extraIconPadding = true,
            )
        }
    }
}

@Composable
@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
private fun SettingsItemPreview() {
    BlockerTheme {
        Surface {
            BlockerItem(
                title = "Item with icon and extra space",
                icon = ImageVectorIcon(BlockerIcons.CheckList),
            )
        }
    }
}

@Composable
@Preview
private fun SettingsItemWithDescPreview() {
    BlockerTheme {
        Surface {
            BlockerSettingItem(
                title = "Setting item title",
                itemDesc = "description",
                itemDesc1 = "description1",
            )
        }
    }
}
