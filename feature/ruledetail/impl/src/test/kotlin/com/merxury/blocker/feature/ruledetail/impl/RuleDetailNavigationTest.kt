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

package com.merxury.blocker.feature.ruledetail.impl

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.navigation.NavigationState
import com.merxury.blocker.core.navigation.Navigator
import com.merxury.blocker.feature.appdetail.api.navigation.AppDetailNavKey
import com.merxury.blocker.feature.ruledetail.api.navigation.RuleDetailNavKey
import com.merxury.blocker.feature.ruledetail.impl.navigation.openAppDetail
import org.junit.Test

/** A matched app opens its detail for its user on the rule's stack, as the entry provider wires it. */
class RuleDetailNavigationTest {
    private val rule = RuleDetailNavKey("7")
    private val state = NavigationState(
        startKey = rule,
        topLevelStack = NavBackStack<NavKey>(rule),
        subStacks = mapOf(rule to NavBackStack<NavKey>(rule)),
    )
    private val navigator = Navigator(state)
    private val openApp = navigator.openAppDetail()

    @Test
    fun givenOwnAndWorkApps_whenOpened_thenEachIsItsOwnDetailForItsUser() {
        openApp("com.example", null)
        openApp("com.example", 10)

        assertThat(state.currentSubStack).containsExactly(
            rule,
            AppDetailNavKey("com.example"),
            AppDetailNavKey("com.example", userId = 10),
        ).inOrder()
    }

    @Test
    fun givenWorkAppOpened_whenGoingBack_thenTheRuleIsShown() {
        openApp("com.example", 10)

        navigator.goBack()

        assertThat(state.currentKey).isEqualTo(rule)
    }
}
