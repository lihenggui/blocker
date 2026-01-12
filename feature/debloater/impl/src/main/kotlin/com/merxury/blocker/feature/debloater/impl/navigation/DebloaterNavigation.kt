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

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.merxury.blocker.core.designsystem.component.SnackbarHostState
import com.merxury.blocker.feature.debloater.impl.DebloaterScreen
import kotlinx.serialization.Serializable

@Serializable
data object DebloaterRoute

fun NavController.navigateToDebloater(navOptions: NavOptions? = null) {
    navigate(route = DebloaterRoute, navOptions)
}

fun NavGraphBuilder.debloaterScreen(
    snackbarHostState: SnackbarHostState,
) {
    composable<DebloaterRoute> {
        DebloaterScreen(
            snackbarHostState = snackbarHostState,
        )
    }
}
