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

package com.merxury.blocker.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

private object TestFirstTopLevelKey : NavKey
private object TestSecondTopLevelKey : NavKey
private object TestThirdTopLevelKey : NavKey
private object TestKeyFirst : NavKey
private object TestKeySecond : NavKey

class NavigatorTest {

    private lateinit var navigationState: NavigationState
    private lateinit var navigator: Navigator

    @Before
    fun setup() {
        val startKey = TestFirstTopLevelKey
        val topLevelStack = NavBackStack<NavKey>(startKey)
        val topLevelKeys = listOf(
            startKey,
            TestSecondTopLevelKey,
            TestThirdTopLevelKey,
        )
        val subStacks = topLevelKeys.associateWith { key -> NavBackStack(key) }

        navigationState = NavigationState(
            startKey = startKey,
            topLevelStack = topLevelStack,
            subStacks = subStacks,
        )
        navigator = Navigator(navigationState)
    }

    @Test
    fun whenNavigatorIsCreated_thenStartKeyIsCurrent() {
        assertThat(navigationState.startKey).isEqualTo(TestFirstTopLevelKey)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
    }

    @Test
    fun whenNavigatingToDetail_thenCurrentSubStackReceivesKey() {
        navigator.navigate(TestKeyFirst)

        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
        assertThat(navigationState.subStacks[TestFirstTopLevelKey]?.last()).isEqualTo(TestKeyFirst)
    }

    @Test
    fun whenNavigatingToTopLevel_thenCurrentTopLevelChanges() {
        navigator.navigate(TestSecondTopLevelKey)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestSecondTopLevelKey)
    }

    @Test
    fun givenCurrentDetail_whenNavigatingToSameDetail_thenStackHasOneEntryForIt() {
        navigator.navigate(TestKeyFirst)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
            TestKeyFirst,
        ).inOrder()

        navigator.navigate(TestKeyFirst)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
            TestKeyFirst,
        ).inOrder()
    }

    @Test
    fun givenOpenDetail_whenReselectingCurrentTopLevel_thenSubStackResets() {
        navigator.navigate(TestSecondTopLevelKey)
        navigator.navigate(TestKeyFirst)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestSecondTopLevelKey,
            TestKeyFirst,
        ).inOrder()

        navigator.navigate(TestSecondTopLevelKey)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestSecondTopLevelKey,
        ).inOrder()
    }

    @Test
    fun whenNavigatingBetweenDetails_thenTopLevelStaysCurrent() {
        navigator.navigate(TestKeyFirst)

        assertThat(navigationState.currentKey).isEqualTo(TestKeyFirst)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)

        navigator.navigate(TestKeySecond)

        assertThat(navigationState.currentKey).isEqualTo(TestKeySecond)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
    }

    @Test
    fun whenReturningToStartStack_thenPreviousDetailIsRestored() {
        // add to start stack
        navigator.navigate(TestKeyFirst)

        assertThat(navigationState.currentKey).isEqualTo(TestKeyFirst)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)

        // navigate to new top level
        navigator.navigate(TestSecondTopLevelKey)

        assertThat(navigationState.currentKey).isEqualTo(TestSecondTopLevelKey)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestSecondTopLevelKey)

        // add to new stack
        navigator.navigate(TestKeySecond)

        assertThat(navigationState.currentKey).isEqualTo(TestKeySecond)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestSecondTopLevelKey)

        // go back to start stack
        navigator.navigate(TestFirstTopLevelKey)

        assertThat(navigationState.currentKey).isEqualTo(TestKeyFirst)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
    }

    @Test
    fun givenTwoDetails_whenGoingBack_thenPreviousDetailIsCurrent() {
        navigator.navigate(TestKeyFirst)
        navigator.navigate(TestKeySecond)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
            TestKeyFirst,
            TestKeySecond,
        ).inOrder()

        navigator.goBack()

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
            TestKeyFirst,
        ).inOrder()

        assertThat(navigationState.currentKey).isEqualTo(TestKeyFirst)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
    }

    @Test
    fun givenTwoTopLevels_whenGoingBack_thenPreviousTopLevelAndDetailAreRestored() {
        navigator.navigate(TestKeyFirst)
        navigator.navigate(TestSecondTopLevelKey)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestSecondTopLevelKey,
        ).inOrder()

        assertThat(navigationState.currentKey).isEqualTo(TestSecondTopLevelKey)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestSecondTopLevelKey)

        // remove TopLevel
        navigator.goBack()

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
            TestKeyFirst,
        ).inOrder()

        assertThat(navigationState.currentKey).isEqualTo(TestKeyFirst)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
    }

    @Test
    fun givenTwoDetails_whenGoingBackTwice_thenStartKeyIsCurrent() {
        navigator.navigate(TestKeyFirst)
        navigator.navigate(TestKeySecond)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
            TestKeyFirst,
            TestKeySecond,
        ).inOrder()

        navigator.goBack()
        navigator.goBack()

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
        ).inOrder()

        assertThat(navigationState.currentKey).isEqualTo(TestFirstTopLevelKey)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
    }

    @Test
    fun givenMultipleTopLevelsWithDetails_whenGoingBackFourTimes_thenStartKeyIsCurrent() {
        // second sub-stack
        navigator.navigate(TestSecondTopLevelKey)
        navigator.navigate(TestKeyFirst)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestSecondTopLevelKey,
            TestKeyFirst,
        ).inOrder()

        // third sub-stack
        navigator.navigate(TestThirdTopLevelKey)
        navigator.navigate(TestKeySecond)

        assertThat(navigationState.currentSubStack).containsExactly(
            TestThirdTopLevelKey,
            TestKeySecond,
        ).inOrder()

        repeat(4) {
            navigator.goBack()
        }

        assertThat(navigationState.currentSubStack).containsExactly(
            TestFirstTopLevelKey,
        ).inOrder()

        assertThat(navigationState.currentKey).isEqualTo(TestFirstTopLevelKey)
        assertThat(navigationState.currentTopLevelKey).isEqualTo(TestFirstTopLevelKey)
    }

    @Test
    fun givenOnlyStartKey_whenGoingBack_thenThrowsIllegalStateException() {
        assertFailsWith<IllegalStateException> {
            navigator.goBack()
        }
    }
}
