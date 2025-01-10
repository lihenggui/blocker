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

package com.merxury.blocker.feature.appdetail.navigation

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailDialogRoute
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.text.Charsets.UTF_8

private val URL_CHARACTER_ENCODING = UTF_8.name()

@VisibleForTesting
internal const val COMPONENT_ARG_NAME = "componentName"

internal class ComponentDetailArgs(
    val name: String,
) {
    constructor(savedStateHandle: SavedStateHandle) :
        this(
            URLDecoder.decode(
                checkNotNull(savedStateHandle[COMPONENT_ARG_NAME]),
                URL_CHARACTER_ENCODING,
            ),
        )
}

fun NavController.navigateToComponentDetail(
    name: String,
) {
    val encodedId = URLEncoder.encode(name, URL_CHARACTER_ENCODING)
    navigate("app_component_detail_route/$encodedId")
}

fun NavGraphBuilder.componentDetailScreen(
    dismissHandler: () -> Unit,
) {
    dialog(
        route = "app_component_detail_route/{$COMPONENT_ARG_NAME}",
        arguments = listOf(
            navArgument(COMPONENT_ARG_NAME) { type = NavType.StringType },
        ),
    ) {
        ComponentDetailDialogRoute(
            dismissHandler = dismissHandler,
        )
    }
}
