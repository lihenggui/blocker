/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.applist.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailNavKey
import com.merxury.blocker.feature.appdetail.api.navigation.navigateToAppDetail
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import org.junit.Test

/** The app list opens the detail screen of an app for its user, as the entry provider wires it. */
class AppDetailNavigationTest {
    private val state = NavigationState(
        startKey = AppListNavKey,
        topLevelStack = NavBackStack<NavKey>(AppListNavKey),
        subStacks = mapOf(AppListNavKey to NavBackStack<NavKey>(AppListNavKey)),
    )
    private val navigator = Navigator(state)

    // What AppListEntryProvider passes on for a tapped row.
    private val open: (String, Int?) -> Unit = { packageName, userId -> navigator.navigateToAppDetail(packageName, userId = userId) }

    @Test
    fun givenOwnAndWorkApps_whenOpened_thenEachIsItsOwnDetailOnTheStack() {
        open("com.example", null)
        open("com.example", 10)

        assertThat(state.currentSubStack).containsExactly(
            AppListNavKey,
            AppDetailNavKey("com.example"),
            AppDetailNavKey("com.example", userId = 10),
        ).inOrder()
    }

    @Test
    fun givenWorkAppOpened_whenGoingBack_thenTheOwnAppAndThenTheListAreShown() {
        open("com.example", null)
        open("com.example", 10)

        navigator.goBack()
        assertThat(state.currentKey).isEqualTo(AppDetailNavKey("com.example"))

        navigator.goBack()
        assertThat(state.currentKey).isEqualTo(AppListNavKey)
    }

    @Test
    fun givenWorkAppOpenedTwice_whenOpenedAgain_thenItMovesToTheTopOnce() {
        open("com.example", 10)
        open("com.other", null)
        open("com.example", 10)

        assertThat(state.currentSubStack).containsExactly(
            AppListNavKey,
            AppDetailNavKey("com.other"),
            AppDetailNavKey("com.example", userId = 10),
        ).inOrder()
    }
}
