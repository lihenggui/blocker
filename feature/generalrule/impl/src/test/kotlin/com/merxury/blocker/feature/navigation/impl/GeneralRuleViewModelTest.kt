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

package com.merxury.blocker.feature.navigation.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.RefreshProgress
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.feature.generalrule.impl.GeneralRuleUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/** The rules list counts the apps of the selected user each rule matches, from the component cache. */
@RunWith(AndroidJUnit4::class)
class GeneralRuleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private lateinit var fixture: GeneralRuleFixture
    private val viewModel get() = fixture.viewModel

    @Before
    fun setUp() {
        fixture = GeneralRuleFixture(mainDispatcherRule.testDispatcher, tempFolder.newFolder())
        fixture.rules.sendRuleList(listOf(rule(1, "com.tracker."), rule(2, "com.ads."), rule(3, "com.absent.")))
        fixture.installedApps.sendApps(
            listOf(
                installedApp("com.a"),
                installedApp("com.b"),
                installedApp(SELF_PACKAGE),
                installedApp("com.w", WORK),
            ),
        )
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.a", "com.tracker.A"),
                component("com.a", "com.ads.A"),
                component("com.b", "com.tracker.B"),
                component(SELF_PACKAGE, "com.ads.Self"),
            ),
        )
        fixture.components.sendComponents(WORK, listOf(component("com.w", "com.absent.W")))
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
    }

    private fun TestScope.show(keepFresh: Boolean = true) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        if (keepFresh) backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepFresh() }
        runCurrent()
    }

    private fun success() = viewModel.uiState.value.rules as GeneralRuleUiState.Success

    @Test
    fun givenInitialState_whenObserved_thenLoading() = runTest {
        assertThat(viewModel.uiState.value.rules).isEqualTo(GeneralRuleUiState.Loading)
    }

    @Test
    fun givenKeepFreshNotRunning_whenObserved_thenStaysLoading() = runTest {
        show(keepFresh = false)

        assertThat(viewModel.uiState.value.rules).isEqualTo(GeneralRuleUiState.Loading)
    }

    @Test
    fun givenCachedComponents_whenShown_thenCountsTheMatchedAppsOfTheOwnUserMostFirst() = runTest {
        show()

        val state = success()
        assertThat(state.matchedRules.map { it.id to it.matchedAppCount }).containsExactly(1 to 2, 2 to 1).inOrder()
        assertThat(state.unmatchedRules.map { it.id }).containsExactly(3)
    }

    @Test
    fun givenBlockerMatchesARule_whenShown_thenItIsNotCounted() = runTest {
        show()

        // com.ads.Self of Blocker itself matches rule 2 too, but only com.a counts.
        assertThat(success().matchedRules.single { it.id == 2 }.matchedAppCount).isEqualTo(1)
    }

    @Test
    fun givenWorkProfileSelected_whenShown_thenCountsItsAppsAndIndexesIt() = runTest {
        show()

        fixture.selectedUsers.select(WORK)
        runCurrent()

        val state = success()
        assertThat(state.matchedRules.map { it.id to it.matchedAppCount }).containsExactly(3 to 1)
        assertThat(state.unmatchedRules.map { it.id }).containsExactly(1, 2)
        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER, WORK).inOrder()
    }

    @Test
    fun givenIndexingRuns_whenShown_thenTheProgressFollowsItAndTheCountsTheCache() = runTest {
        val progress = MutableSharedFlow<RefreshProgress>()
        fixture.components.refreshAllProgress = { progress }
        show()
        assertThat(success().matchProgress).isEqualTo(0F)

        progress.emit(RefreshProgress.Refreshing(done = 1, total = 4, current = "com.a"))
        assertThat(success().matchProgress).isEqualTo(0.25F)
        fixture.components.sendComponents(OWNER, listOf(component("com.a", "com.absent.A")))
        runCurrent()
        assertThat(success().matchedRules.map { it.id }).containsExactly(3)

        progress.emit(RefreshProgress.Finished(total = 4, failed = 0, appListFailure = null))
        assertThat(success().matchProgress).isEqualTo(1F)
    }

    @Test
    fun givenIndexedOnce_whenShownAgain_thenNotIndexedAgain() = runTest {
        show()
        fixture.selectedUsers.select(WORK)
        runCurrent()
        fixture.selectedUsers.select(OWNER)
        runCurrent()

        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER, WORK).inOrder()
        assertThat(success().matchProgress).isEqualTo(1F)
    }

    @Test
    fun givenARuleClicked_whenShown_thenItIsSelected() = runTest {
        show()

        viewModel.onRuleClick("2")
        runCurrent()

        assertThat(success().selectedRuleId).isEqualTo("2")
    }

    @Test
    fun givenSummaryOffersAccess_whenAccessIsAskedFor_thenTheChannelConnects() = runTest {
        viewModel.grantAccess()
        runCurrent()

        assertThat(fixture.channel.connectCount).isEqualTo(1)
    }
}
