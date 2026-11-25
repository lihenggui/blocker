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

package com.merxury.blocker.feature.search.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.testing.repository.defaultUserData
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import com.merxury.blocker.core.ui.control.ControlMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** What the search finds for the selected user, as the repositories hold it. */
class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Created after the rule sets the main dispatcher, which the view model's scope keeps.
    private lateinit var fixture: SearchFixture
    private val viewModel get() = fixture.viewModel

    @Before
    fun setUp() {
        fixture = SearchFixture(mainDispatcherRule.testDispatcher)
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
    }

    private fun TestScope.searchNow(keyword: String) {
        viewModel.search(keyword)
        advanceTimeBy(TYPING_PAUSE + 1)
        runCurrent()
    }

    @Test
    fun givenNewViewModel_whenNothingObserves_thenNothingIsRead() = runTest {
        advanceUntilIdle()

        assertThat(fixture.installedApps.calls).isEmpty()
        assertThat(fixture.appControl.calls).isEmpty()
        assertThat(fixture.components.refreshedUsers).isEmpty()
    }

    @Test
    fun givenNoKeyword_whenShown_thenIdle() = runTest {
        collectState()

        assertThat(viewModel.uiState.value.results).isEqualTo(SearchResults.Idle)
        assertThat(viewModel.uiState.value.keyword).isEmpty()
    }

    @Test
    fun givenApps_whenTypingPauses_thenAppsOfTheSelectedUserFound() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.example.chat", label = "Chat"), installedApp("com.example.mail", label = "Mail"), installedApp("com.example.chat", WORK)))
        collectState()

        viewModel.search("chat")
        advanceTimeBy(TYPING_PAUSE - 50)
        runCurrent()
        assertThat(viewModel.uiState.value.results).isEqualTo(SearchResults.Idle)
        advanceTimeBy(100)
        runCurrent()

        assertThat(fixture.loaded().apps.map { it.key }).containsExactly(AppKey("com.example.chat", OWNER))
        assertThat(viewModel.uiState.value.keyword).isEqualTo("chat")
    }

    @Test
    fun givenTypingGoesOn_whenItPauses_thenOnlyTheLastKeywordIsSearched() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "Alpha"), installedApp("com.b", label = "Beta")))
        val states = mutableListOf<SearchUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { states += it } }

        viewModel.search("al")
        advanceTimeBy(TYPING_PAUSE - 100)
        viewModel.search("be")
        advanceTimeBy(TYPING_PAUSE + 1)
        runCurrent()

        assertThat(states.map { it.keyword }).doesNotContain("al")
        assertThat(states.last().keyword).isEqualTo("be")
        assertThat(fixture.loaded().apps.map { it.label }).containsExactly("Beta")
    }

    @Test
    fun givenResults_whenKeywordCleared_thenIdleAtOnce() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        collectState()
        searchNow("com")

        viewModel.search("")
        runCurrent()

        assertThat(viewModel.uiState.value.results).isEqualTo(SearchResults.Idle)
    }

    @Test
    fun givenKeywordWithSpaces_whenSearched_thenTrimmed() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "Alpha")))
        collectState()

        searchNow("  alp ")

        assertThat(fixture.loaded().apps.map { it.label }).containsExactly("Alpha")
    }

    @Test
    fun givenSystemAppsHidden_whenSearched_thenSystemAppsAndBlockerLeftOutOfAppsAndComponents() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.app"), installedApp("com.system", isSystem = true), installedApp(SELF_PACKAGE)))
        fixture.components.sendComponents(OWNER, listOf(component("com.app", "Push"), component("com.system", "Push"), component(SELF_PACKAGE, "Push")))
        collectState()

        searchNow("com")

        assertThat(fixture.loaded().apps.map { it.key.packageName }).containsExactly("com.app")
        searchNow("push")
        assertThat(fixture.loaded().components.map { it.app.packageName }).containsExactly("com.app")
    }

    @Test
    fun givenSystemAppsShown_whenSearched_thenSystemAppsFound() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(showSystemApps = true))
        fixture.installedApps.sendApps(listOf(installedApp("com.system", isSystem = true)))
        fixture.components.sendComponents(OWNER, listOf(component("com.system", "Push")))
        collectState()

        searchNow("push")

        assertThat(fixture.loaded().components.map { it.app.packageName }).containsExactly("com.system")
    }

    @Test
    fun givenComponentsOfTwoApps_whenSearched_thenGroupedByApp() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A"), installedApp("com.b", label = "B")))
        fixture.components.sendComponents(
            OWNER,
            listOf(
                component("com.b", "PushService", type = ComponentType.SERVICE, ifw = IfwBlock.BLOCKED),
                component("com.a", "PushReceiver", pm = PmSetting.DISABLED),
                component("com.a", "PushActivity", type = ComponentType.ACTIVITY),
                component("com.a", "PushProvider", type = ComponentType.PROVIDER, pm = PmSetting.UNKNOWN),
                component("com.a", "Other"),
            ),
        )
        collectState()

        searchNow("push")

        val groups = fixture.loaded().components
        assertThat(groups.map { it.label }).containsExactly("A", "B").inOrder()
        val a = groups.first().components.associateBy { it.id.simpleName }
        assertThat(a.keys).containsExactly("PushActivity", "PushProvider", "PushReceiver")
        assertThat(groups.first().firstType).isEqualTo(ComponentType.RECEIVER)
        assertThat(groups.last().components.single().type).isEqualTo(ComponentType.SERVICE)
    }

    @Test
    fun givenRulesAndComponents_whenSearched_thenRulesSplitByMatchedAppsAndTheTabsCounted() = runTest {
        fixture.rules.sendRuleList(listOf(rule(1, "Push SDK", keywords = listOf("com.push.Push")), rule(2, "Push kit", keywords = listOf("com.kit.")), rule(3, "Other", keywords = listOf("com.push."))))
        fixture.installedApps.sendApps(listOf(installedApp("com.push")))
        fixture.components.sendComponents(OWNER, listOf(component("com.push", "PushA"), component("com.push", "PushB")))
        collectState()

        searchNow("push")

        val loaded = fixture.loaded()
        assertThat(loaded.matchedRules.map { it.id }).containsExactly(1)
        assertThat(loaded.unmatchedRules.map { it.id }).containsExactly(2)
        // The components tab counts the apps they were found in, as the old search did.
        assertThat(viewModel.uiState.value.tabs.itemCount).containsExactly(SearchTab.APPS, 1, SearchTab.COMPONENTS, 1, SearchTab.RULES, 2)
    }

    @Test
    fun givenWorkProfileSelected_whenRulesSearched_thenTheyAreSplitByItsApps() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.rules.sendRuleList(listOf(rule(1, "Push SDK", keywords = listOf("com.push.")), rule(2, "Push kit", keywords = listOf("com.kit."))))
        fixture.installedApps.sendApps(listOf(installedApp("com.push"), installedApp("com.kit", WORK)))
        fixture.components.sendComponents(OWNER, listOf(component("com.push", "PushA")))
        fixture.components.sendComponents(WORK, listOf(component("com.kit", "KitA")))
        collectState()
        searchNow("push")
        assertThat(fixture.loaded().matchedRules.map { it.id }).containsExactly(1)

        viewModel.selectUser(WORK)
        runCurrent()

        assertThat(fixture.loaded().matchedRules.map { it.id to it.matchedAppCount }).containsExactly(2 to 1)
        assertThat(fixture.loaded().unmatchedRules.map { it.id }).containsExactly(1)
    }

    @Test
    fun givenRulesNotMatchedYet_whenSearched_thenAppsAndComponentsShowAndTheRulesFollow() = runTest {
        fixture.rules.catalogGate = CompletableDeferred()
        fixture.rules.sendRuleList(listOf(rule(1, "Push SDK", keywords = listOf("com.push."))))
        fixture.installedApps.sendApps(listOf(installedApp("com.push")))
        fixture.components.sendComponents(OWNER, listOf(component("com.push", "PushA")))
        collectState()

        searchNow("push")
        val waiting = fixture.loaded()
        assertThat(waiting.apps.map { it.key.packageName }).containsExactly("com.push")
        assertThat(waiting.components.map { it.app.packageName }).containsExactly("com.push")
        assertThat(waiting.rulesLoading).isTrue()

        fixture.rules.catalogGate!!.complete(Unit)
        runCurrent()

        assertThat(fixture.loaded().rulesLoading).isFalse()
        assertThat(fixture.loaded().matchedRules.map { it.id }).containsExactly(1)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun givenRuleMatcherReadFails_whenQueryChanges_thenSearchContinuesAndRulesRecover() = runTest {
        fixture.rules.readFailure = IllegalStateException("Rule catalog unavailable")
        fixture.rules.sendRuleList(listOf(rule(1, "Push SDK", keywords = listOf("com.push."))))
        fixture.installedApps.sendApps(listOf(installedApp("com.push"), installedApp("com.other")))
        fixture.components.sendComponents(OWNER, listOf(component("com.push", "PushA")))

        viewModel.uiState.test {
            searchNow("push")
            val partial = expectMostRecentItem().results as SearchResults.Loaded
            assertThat(partial.apps.map { it.key.packageName }).containsExactly("com.push")
            assertThat(partial.matchedRules).isEmpty()
            assertThat(partial.rulesLoading).isFalse()

            searchNow("other")
            val next = expectMostRecentItem().results as SearchResults.Loaded
            assertThat(next.apps.map { it.key.packageName }).containsExactly("com.other")

            fixture.rules.readFailure = null
            searchNow("push")
            advanceTimeBy(10_001)
            runCurrent()

            val recovered = expectMostRecentItem().results as SearchResults.Loaded
            assertThat(recovered.matchedRules.map { it.id }).containsExactly(1)
        }
    }

    @Test
    fun givenAppRemovedFromTheCache_whenShown_thenItLeavesTheResults() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a"), installedApp("com.b")))
        collectState()
        searchNow("com")

        fixture.installedApps.sendApps(listOf(installedApp("com.b")))
        runCurrent()

        assertThat(fixture.loaded().apps.map { it.key.packageName }).containsExactly("com.b")
    }

    @Test
    fun givenActiveSearch_whenACachedPackageAddsAMatchingComponent_thenTheResultAppears() = runTest {
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        collectState()
        searchNow("push")
        assertThat(fixture.loaded().components).isEmpty()

        fixture.components.sendComponents(OWNER, listOf(component("com.a", "Push")))
        runCurrent()

        assertThat(fixture.loaded().components.single().app).isEqualTo(AppKey("com.a", OWNER))
    }

    @Test
    fun givenTwoUsers_whenWorkSelected_thenWorkResultsShownAndTheSwitcherOffered() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.installedApps.sendApps(listOf(installedApp("com.a", OWNER), installedApp("com.work", WORK)))
        fixture.components.sendComponents(WORK, listOf(component("com.work", "Push")))
        collectState()
        searchNow("com")
        assertThat(viewModel.uiState.value.showProfileSwitcher).isTrue()

        viewModel.selectUser(WORK)
        runCurrent()

        assertThat(viewModel.uiState.value.selectedUser).isEqualTo(WORK)
        assertThat(fixture.loaded().apps.map { it.key }).containsExactly(AppKey("com.work", WORK))
        searchNow("push")
        assertThat(fixture.loaded().components.single().app).isEqualTo(AppKey("com.work", WORK))
    }

    @Test
    fun givenOneUser_whenShown_thenNoSwitcher() = runTest {
        collectState()

        assertThat(viewModel.uiState.value.showProfileSwitcher).isFalse()
    }

    @Test
    fun givenShownForAUser_whenKeptFresh_thenItsAppsAreReadOnceAndWhatRunsEachTime() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        fixture.appControl.running[OWNER] = mutableSetOf("com.a")
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        collectState()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepFresh() }
        searchNow("com")
        assertThat(fixture.loaded().apps.single().isRunning).isTrue()

        viewModel.selectUser(WORK)
        runCurrent()
        viewModel.selectUser(OWNER)
        runCurrent()

        assertThat(fixture.installedApps.calls.filter { it.startsWith("refresh ") }).containsExactly("refresh 0", "refresh 10").inOrder()
        assertThat(fixture.appControl.calls.count { it == "runningPackages 0" }).isEqualTo(2)
    }

    @Test
    fun givenAccessNotChecked_whenKeptFresh_thenNothingIsReadUntilTheCheckEnds() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        collectState()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepFresh() }
        runCurrent()
        assertThat(fixture.installedApps.calls).isEmpty()

        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        runCurrent()

        assertThat(fixture.installedApps.calls).containsExactly("refresh 0")
        assertThat(fixture.appControl.calls).isEmpty()
    }

    @Test
    fun givenNoAccess_whenGranted_thenConnects() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        collectState()

        viewModel.grantAccess()
        runCurrent()

        assertThat(fixture.channel.connectCount).isEqualTo(1)
    }

    @Test
    fun givenServiceInfoShown_whenAppCounted_thenItsServicesShown() = runTest {
        fixture.userData.sendUserData(defaultUserData.copy(showServiceInfo = true))
        fixture.installedApps.sendApps(listOf(installedApp("com.a")))
        fixture.components.sendComponents(OWNER, listOf(component("com.a", "Sync", type = ComponentType.SERVICE)))
        collectState()
        searchNow("com")
        assertThat(fixture.loaded().showServiceInfo).isTrue()

        viewModel.countServices(AppKey("com.a", OWNER))

        assertThat(fixture.loaded().apps.single().services?.total).isEqualTo(1)
    }

    private companion object {
        const val TYPING_PAUSE = 300L
    }
}
