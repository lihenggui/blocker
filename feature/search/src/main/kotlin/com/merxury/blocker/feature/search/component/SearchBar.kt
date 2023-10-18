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

package com.merxury.blocker.feature.search.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.merxury.blocker.core.designsystem.component.BlockerSearchTextField
import com.merxury.blocker.core.designsystem.component.BlockerTopAppBar
import com.merxury.blocker.feature.search.R

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    keyword: String,
    onSearchTextChanged: (String) -> Unit,
    onClearClick: () -> Unit,
) {
    BlockerTopAppBar(
        title = stringResource(id = R.string.feature_search_searching),
        actions = {
            BlockerSearchTextField(
                keyword = keyword,
                hintTextRes = R.string.feature_search_search_hint,
                onValueChange = onSearchTextChanged,
                modifier = modifier.fillMaxWidth(),
                onClearClick = onClearClick,
            )
        },
    )
}
