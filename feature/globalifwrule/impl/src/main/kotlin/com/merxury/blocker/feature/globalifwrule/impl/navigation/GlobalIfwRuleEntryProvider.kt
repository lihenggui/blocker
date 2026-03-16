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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.feature.globalifwrule.api.navigation.GlobalIfwRuleNavKey
import com.merxury.blocker.feature.globalifwrule.impl.AddRuleScreen
import com.merxury.blocker.feature.globalifwrule.impl.GlobalIfwRuleScreen
import com.merxury.blocker.feature.globalifwrule.impl.GlobalIfwRuleScreenState
import com.merxury.blocker.feature.globalifwrule.impl.GlobalIfwRuleViewModel

fun EntryProviderScope<NavKey>.globalIfwRuleEntry() {
    entry<GlobalIfwRuleNavKey> { _ ->
        val viewModel: GlobalIfwRuleViewModel = hiltViewModel()
        val editorState = viewModel.editorState.collectAsStateWithLifecycle()

        AnimatedContent(
            targetState = editorState.value.screen,
            transitionSpec = {
                if (targetState == GlobalIfwRuleScreenState.EDIT) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                } else {
                    slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                }
            },
            label = "globalIfwRuleTransition",
        ) { state ->
            when (state) {
                GlobalIfwRuleScreenState.LIST -> {
                    GlobalIfwRuleScreen(
                        onAddRuleClick = viewModel::startAddingRule,
                        onEditRuleClick = viewModel::startEditingRule,
                        viewModel = viewModel,
                    )
                }

                GlobalIfwRuleScreenState.EDIT -> {
                    AddRuleScreen(
                        initialData = editorState.value.editingData,
                        onSave = viewModel::saveRule,
                        onBack = viewModel::dismissEditor,
                    )
                }
            }
        }
    }
}
