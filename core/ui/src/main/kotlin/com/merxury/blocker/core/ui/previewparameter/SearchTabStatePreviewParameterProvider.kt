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

package com.merxury.blocker.core.ui.previewparameter

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.merxury.blocker.core.ui.SearchScreenTabs
import com.merxury.blocker.core.ui.TabState

class SearchTabStatePreviewParameterProvider :
    PreviewParameterProvider<List<TabState<SearchScreenTabs>>> {
    override val values: Sequence<List<TabState<SearchScreenTabs>>> = sequenceOf(
        listOf(
            TabState(
                items = listOf(
                    SearchScreenTabs.App(3),
                    SearchScreenTabs.Component(1),
                    SearchScreenTabs.Rule(2),
                ),
                selectedItem = SearchScreenTabs.App(),
            ),
            TabState(
                items = listOf(
                    SearchScreenTabs.App(3),
                    SearchScreenTabs.Component(1),
                    SearchScreenTabs.Rule(2),
                ),
                selectedItem = SearchScreenTabs.Component(),
            ),
            TabState(
                items = listOf(
                    SearchScreenTabs.App(3),
                    SearchScreenTabs.Component(1),
                    SearchScreenTabs.Rule(2),
                ),
                selectedItem = SearchScreenTabs.Rule(),
            ),
        ),
    )
}
