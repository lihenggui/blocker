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

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.RefreshProgress
import com.merxury.blocker.core.domain.search.IndexState
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.util.MainDispatcherRule
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The components of every app are read the first time the components tab needs them, once per user. */
class SearchIndexingTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fixture: SearchFixture
    private val viewModel get() = fixture.viewModel

    // The progress the repository reports, sent by each test while the indexing runs.
    private val progress = mutableMapOf<UserId, Channel<RefreshProgress>>()

    @Before
    fun setUp() {
        fixture = SearchFixture(mainDispatcherRule.testDispatcher)
        fixture.components.refreshAllProgress = { user -> Channel<RefreshProgress>(Channel.UNLIMITED).also { progress[user] = it }.consumeAsFlow() }
        fixture.installedApps.sendApps(listOf(installedApp("com.a", label = "A"), installedApp("com.b", label = "B"), installedApp("com.work", WORK)))
    }

    private fun TestScope.show() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keepFresh() }
    }

    private fun TestScope.searchNow(keyword: String) {
        viewModel.search(keyword)
        advanceTimeBy(301)
        runCurrent()
    }

    private fun TestScope.report(event: RefreshProgress, user: UserId = OWNER) {
        progress.getValue(user).trySend(event)
        runCurrent()
    }

    private fun TestScope.finish(user: UserId = OWNER, total: Int = 2, failed: Int = 0, appListFailure: ReadFailure? = null) {
        report(RefreshProgress.Finished(total, failed, appListFailure), user)
        progress.getValue(user).close()
        runCurrent()
    }

    @Test
    fun givenKeyword_whenTheAppsTabIsShown_thenNothingIsIndexed() = runTest {
        show()

        searchNow("push")

        assertThat(fixture.components.refreshedUsers).isEmpty()
    }

    @Test
    fun givenKeyword_whenTheComponentsTabIsShown_thenTheUserIsIndexedWithProgress() = runTest {
        show()
        searchNow("push")

        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()
        assertThat(viewModel.uiState.value.indexing).isEqualTo(IndexingProgress(done = 0, total = 0))
        report(RefreshProgress.Refreshing(done = 1, total = 2, current = "com.a"))

        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER)
        assertThat(viewModel.uiState.value.indexing).isEqualTo(IndexingProgress(done = 1, total = 2))
        finish()
        assertThat(viewModel.uiState.value.indexing).isNull()
    }

    @Test
    fun givenIndexingRuns_whenAnAppIsRead_thenItsComponentsShowAtOnce() = runTest {
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()
        assertThat(fixture.loaded().components).isEmpty()

        fixture.components.sendComponents(OWNER, listOf(component("com.a", "PushReceiver")))
        report(RefreshProgress.Refreshing(done = 1, total = 2, current = "com.a"))

        assertThat(fixture.loaded().components.map { it.label }).containsExactly("A")
        assertThat(viewModel.uiState.value.indexing).isNotNull()
    }

    @Test
    fun givenIndexingRuns_whenTypingGoesOn_thenItIsNeverWaitedFor() = runTest {
        fixture.components.sendComponents(OWNER, listOf(component("com.b", "Sync")))
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()

        searchNow("sync")

        assertThat(fixture.loaded().components.single().label).isEqualTo("B")
        assertThat(viewModel.uiState.value.indexing).isNotNull()
    }

    @Test
    fun givenUserIndexed_whenSearchedAgain_thenNotIndexedAgain() = runTest {
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()
        finish()

        viewModel.selectTab(SearchTab.APPS)
        searchNow("sync")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()

        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER)
        assertThat(viewModel.uiState.value.indexing).isNull()
    }

    @Test
    fun givenOwnerIndexed_whenWorkSelected_thenWorkIsIndexedOnceToo() = runTest {
        fixture.selectedUsers.sendUsers(listOf(ownerUser, workUser))
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()
        finish()

        viewModel.selectUser(WORK)
        runCurrent()
        finish(WORK)
        viewModel.selectUser(OWNER)
        runCurrent()

        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER, WORK).inOrder()
    }

    @Test
    fun givenUserIndexed_whenRefreshed_thenIndexedAgain() = runTest {
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()
        finish()

        viewModel.refreshComponents()
        runCurrent()

        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER, OWNER)
        assertThat(viewModel.uiState.value.indexing).isNotNull()
    }

    @Test
    fun givenNoAccess_whenIndexed_thenWhatCanBeReadIsIndexedWithoutAMessage() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()

        finish()

        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER)
        assertThat(viewModel.uiState.value.messages).isEmpty()
        assertThat(viewModel.uiState.value.indexing).isNull()
    }

    @Test
    fun givenIndexedWithoutAccess_whenAccessIsGranted_thenIndexedAgain() = runTest {
        fixture.channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()
        finish()

        fixture.channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()

        assertThat(fixture.components.refreshedUsers).containsExactly(OWNER, OWNER)
    }

    @Test
    fun givenSomeAppsFail_whenIndexed_thenTheBarEndsWithoutAMessage() = runTest {
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()

        finish(failed = 1)

        assertThat(viewModel.uiState.value.messages).isEmpty()
        assertThat(viewModel.uiState.value.indexing).isNull()
    }

    @Test
    fun givenAppsCannotBeListed_whenIndexed_thenTheBarEndsWithoutAMessage() = runTest {
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()

        finish(total = 0, appListFailure = ReadFailure.UserNotReachable(OWNER))

        assertThat(viewModel.uiState.value.messages).isEmpty()
        assertThat(viewModel.uiState.value.indexing).isNull()
    }

    @Test
    fun givenIndexingRuns_whenTheScreenGoesAway_thenItRunsToTheEnd() = runTest {
        show()
        searchNow("push")
        viewModel.selectTab(SearchTab.COMPONENTS)
        runCurrent()

        // As when the view model is cleared.
        viewModel.viewModelScope.cancel()
        report(RefreshProgress.Refreshing(done = 2, total = 2, current = "com.b"))
        finish()

        assertThat(fixture.index.state(OWNER).first()).isInstanceOf(IndexState.Indexed::class.java)
    }
}
