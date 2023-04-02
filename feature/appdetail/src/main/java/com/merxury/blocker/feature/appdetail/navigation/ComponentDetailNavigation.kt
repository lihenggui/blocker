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

package com.merxury.blocker.feature.appdetail.navigation

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailRoute

@VisibleForTesting
internal const val componentNameArg = "componentName"

internal class ComponentDetailArgs(
    val name: String,
) {
    constructor(savedStateHandle: SavedStateHandle, stringDecoder: StringDecoder) :
        this(
            stringDecoder.decodeString(checkNotNull(savedStateHandle[componentNameArg])),
        )
}

fun NavController.navigateToComponentDetail(
    name: String,
) {
    val encodedId = Uri.encode(name)
    navigate("app_component_detail_route/$encodedId")
}

fun NavGraphBuilder.componentDetailScreen(
    dismissHandler: () -> Unit,
) {
    dialog(
        route = "app_component_detail_route/{$componentNameArg}",
        arguments = listOf(
            navArgument(componentNameArg) { type = NavType.StringType },
        ),
    ) {
        ComponentDetailRoute(
            dismissHandler = dismissHandler,
        )
    }
}
