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

package com.merxury.blocker.feature.search.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailNavKey
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailTabs
import com.merxury.blocker.feature.applist.api.navigation.AppListNavKey
import com.merxury.blocker.feature.ruledetail.api.navigation.RuleDetailNavKey
import com.merxury.blocker.feature.ruledetail.api.navigation.navigateToRuleDetail
import com.merxury.blocker.feature.search.api.navigation.SearchNavKey
import com.merxury.blocker.feature.search.impl.navigation.openAppDetail
import org.junit.Before
import org.junit.Test

/** The search opens app and rule details on its own stack, as the entry provider wires it. */
class SearchNavigationTest {
    private val state = NavigationState(
        startKey = AppListNavKey,
        topLevelStack = NavBackStack<NavKey>(AppListNavKey),
        subStacks = mapOf(
            AppListNavKey to NavBackStack<NavKey>(AppListNavKey),
            SearchNavKey to NavBackStack<NavKey>(SearchNavKey),
        ),
    )
    private val navigator = Navigator(state)
    private val openApp = navigator.openAppDetail()

    @Before
    fun showSearch() {
        navigator.navigate(SearchNavKey)
    }

    @Test
    fun givenOwnAndWorkApps_whenOpenedFromTheSearch_thenEachIsItsOwnDetailOnTheSearchStack() {
        openApp("com.example", null, AppDetailTabs.Info, emptyList())
        openApp("com.example", 10, AppDetailTabs.Receiver, listOf("push"))

        assertThat(state.currentSubStack).containsExactly(
            SearchNavKey,
            AppDetailNavKey("com.example"),
            AppDetailNavKey("com.example", tab = "receiver", searchKeyword = listOf("push"), userId = 10),
        ).inOrder()
    }

    @Test
    fun givenRuleOpenedFromTheSearch_whenGoingBack_thenTheSearchIsShown() {
        navigator.navigateToRuleDetail("7")
        assertThat(state.currentKey).isEqualTo(RuleDetailNavKey("7"))

        navigator.goBack()

        assertThat(state.currentKey).isEqualTo(SearchNavKey)
    }

    @Test
    fun givenWorkAppOpened_whenGoingBackTwice_thenTheOwnAppAndThenTheSearchAreShown() {
        openApp("com.example", null, AppDetailTabs.Info, emptyList())
        openApp("com.example", 10, AppDetailTabs.Service, listOf("push"))

        navigator.goBack()
        assertThat(state.currentKey).isEqualTo(AppDetailNavKey("com.example"))
        navigator.goBack()

        assertThat(state.currentKey).isEqualTo(SearchNavKey)
    }
}
