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

package com.merxury.blocker.feature.appdetail.impl.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.dialog
import com.merxury.blocker.feature.appdetail.impl.componentdetail.ComponentDetailDialog
import kotlinx.serialization.Serializable

@Serializable
data class ComponentDetailRoute(
    val componentName: String,
)

fun NavController.navigateToComponentDetail(
    componentName: String,
    navOptions: NavOptionsBuilder.() -> Unit = {},
) {
    navigate(route = ComponentDetailRoute(componentName)) {
        navOptions()
    }
}

fun NavGraphBuilder.componentDetailScreen(
    dismissHandler: () -> Unit,
) {
    dialog<ComponentDetailRoute> {
        ComponentDetailDialog(
            dismissHandler = dismissHandler,
        )
    }
}
