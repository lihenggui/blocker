/*
 * Copyright 2025 Blocker
 * Copyright 2022 The Android Open Source Project
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.merxury.blocker.core.designsystem.icon.BlockerIcons
import com.merxury.blocker.core.designsystem.theme.BlockerTheme

/**
 * Blocker navigation suite scaffold with item and content slots.
 * Wraps Material 3 [NavigationSuiteScaffold].
 *
 * @param modifier Modifier to be applied to the navigation suite scaffold.
 * @param navigationSuiteItems A slot to display multiple items via [BlockerNavigationSuiteScope].
 * @param windowAdaptiveInfo The window adaptive info.
 * @param content The app content inside the scaffold.
 */
@Composable
fun BlockerNavigationSuiteScaffold(
    navigationSuiteItems: BlockerNavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    content: @Composable () -> Unit,
) {
    val layoutType = NavigationSuiteScaffoldDefaults
        .calculateFromAdaptiveInfo(windowAdaptiveInfo)
    val navigationSuiteItemColors = NavigationSuiteItemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = BlockerNavigationDefaults.navigationSelectedItemColor(),
            unselectedIconColor = BlockerNavigationDefaults.navigationContentColor(),
            selectedTextColor = BlockerNavigationDefaults.navigationSelectedItemColor(),
            unselectedTextColor = BlockerNavigationDefaults.navigationContentColor(),
            indicatorColor = BlockerNavigationDefaults.navigationIndicatorColor(),
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = BlockerNavigationDefaults.navigationSelectedItemColor(),
            unselectedIconColor = BlockerNavigationDefaults.navigationContentColor(),
            selectedTextColor = BlockerNavigationDefaults.navigationSelectedItemColor(),
            unselectedTextColor = BlockerNavigationDefaults.navigationContentColor(),
            indicatorColor = BlockerNavigationDefaults.navigationIndicatorColor(),
        ),
        navigationDrawerItemColors = NavigationDrawerItemDefaults.colors(
            selectedIconColor = BlockerNavigationDefaults.navigationSelectedItemColor(),
            unselectedIconColor = BlockerNavigationDefaults.navigationContentColor(),
            selectedTextColor = BlockerNavigationDefaults.navigationSelectedItemColor(),
            unselectedTextColor = BlockerNavigationDefaults.navigationContentColor(),
        ),
    )

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            BlockerNavigationSuiteScope(
                navigationSuiteScope = this,
                navigationSuiteItemColors = navigationSuiteItemColors,
            ).run(navigationSuiteItems)
        },
        layoutType = layoutType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContentColor = BlockerNavigationDefaults.navigationContentColor(),
            navigationRailContainerColor = Color.Transparent,
        ),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * A wrapper around [NavigationSuiteScope] to declare navigation items.
 */
class BlockerNavigationSuiteScope internal constructor(
    private val navigationSuiteScope: NavigationSuiteScope,
    private val navigationSuiteItemColors: NavigationSuiteItemColors,
) {
    fun item(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: @Composable () -> Unit,
        selectedIcon: @Composable () -> Unit = icon,
        label: @Composable (() -> Unit)? = null,
    ) = navigationSuiteScope.item(
        selected = selected,
        onClick = onClick,
        icon = {
            if (selected) {
                selectedIcon()
            } else {
                icon()
            }
        },
        label = label,
        colors = navigationSuiteItemColors,
        modifier = modifier,
    )
}

@PreviewThemes
@Preview(widthDp = 800, heightDp = 480)
@Composable
private fun BlockerNavigationBarPreview() {
    val navigationItemsMap = mapOf(
        "Apps" to BlockerIcons.Apps,
        "Rules" to BlockerIcons.GeneralRule,
        "Search" to BlockerIcons.Search,
    )
    BlockerTheme {
        Surface {
            BlockerNavigationSuiteScaffold(
                navigationSuiteItems = {
                    navigationItemsMap.forEach { destination ->
                        val selected = destination.key == "Apps"
                        item(
                            selected = selected,
                            onClick = { },
                            icon = {
                                Icon(
                                    imageVector = destination.value,
                                    contentDescription = null,
                                )
                            },
                            selectedIcon = {
                                Icon(
                                    imageVector = destination.value,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(destination.key) },
                        )
                    }
                },
            ) {
                Column {
                    Text("Content")
                }
            }
        }
    }
}

/**
 * Blocker navigation default values.
 */
object BlockerNavigationDefaults {
    @Composable
    fun navigationContentColor() = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun navigationSelectedItemColor() = MaterialTheme.colorScheme.onPrimaryContainer

    @Composable
    fun navigationIndicatorColor() = MaterialTheme.colorScheme.primaryContainer
}
