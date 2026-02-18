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

package com.merxury.blocker.feature.debloater.impl.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.merxury.blocker.core.ui.LocalSnackbarHostState
import com.merxury.blocker.feature.debloater.impl.DebloaterScreen
import com.merxury.blocker.feature.debloater.impl.DebloaterViewModel
import com.merxury.blocker.feature.debloator.api.navigation.DebloaterNavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.debloaterEntry() {
    entry<DebloaterNavKey> {
        val snackbarHostState = LocalSnackbarHostState.current
        DebloaterScreen(
            snackbarHostState = snackbarHostState,
            viewModel = hiltViewModel<DebloaterViewModel, DebloaterViewModel.Factory>
            { factory ->
                factory.create()
            },
        )
    }
}
