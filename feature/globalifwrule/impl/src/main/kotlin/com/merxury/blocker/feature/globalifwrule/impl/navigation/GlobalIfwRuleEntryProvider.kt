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

package com.merxury.blocker.feature.globalifwrule.impl.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.globalifwrule.api.navigation.GlobalIfwRuleNavKey
import com.merxury.blocker.feature.globalifwrule.impl.AddRuleData
import com.merxury.blocker.feature.globalifwrule.impl.AddRuleScreen
import com.merxury.blocker.feature.globalifwrule.impl.GlobalIfwRuleScreen
import com.merxury.blocker.feature.globalifwrule.impl.GlobalIfwRuleViewModel

private enum class ScreenState { LIST, EDIT }

fun EntryProviderScope<NavKey>.globalIfwRuleEntry(navigator: Navigator) {
    entry<GlobalIfwRuleNavKey> { _ ->
        val viewModel: GlobalIfwRuleViewModel = hiltViewModel()
        var editingData: AddRuleData? by remember { mutableStateOf(null) }
        var screenState by remember { mutableStateOf(ScreenState.LIST) }

        AnimatedContent(
            targetState = screenState,
            transitionSpec = {
                if (targetState == ScreenState.EDIT) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "globalIfwRuleTransition",
        ) { state ->
            when (state) {
                ScreenState.LIST -> {
                    GlobalIfwRuleScreen(
                        onAddRuleClick = {
                            editingData = null
                            screenState = ScreenState.EDIT
                        },
                        onEditRuleClick = { packageName, ruleIndex ->
                            editingData = viewModel.getRuleForEdit(packageName, ruleIndex)
                            screenState = ScreenState.EDIT
                        },
                        viewModel = viewModel,
                    )
                }

                ScreenState.EDIT -> {
                    AddRuleScreen(
                        initialData = editingData,
                        onSave = { data ->
                            if (data.editingRuleIndex != null) {
                                viewModel.updateRule(data)
                            } else {
                                viewModel.saveNewRule(data)
                            }
                            screenState = ScreenState.LIST
                        },
                        onBack = {
                            screenState = ScreenState.LIST
                        },
                    )
                }
            }
        }
    }
}
