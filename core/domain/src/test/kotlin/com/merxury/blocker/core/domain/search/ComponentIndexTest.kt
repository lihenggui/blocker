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

package com.merxury.blocker.core.domain.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.RefreshProgress
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComponentIndexTest {
    private val owner = UserId(0)
    private val work = UserId(10)
    private val components = FakeComponentStatusRepository()
    private val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.ROOT))
    private val progress = mutableMapOf<UserId, Channel<RefreshProgress>>()

    init {
        components.refreshAllProgress = { user -> Channel<RefreshProgress>(Channel.UNLIMITED).also { progress[user] = it }.consumeAsFlow() }
    }

    private fun TestScope.index() = ComponentIndex(components, channel, backgroundScope)

    private fun TestScope.finish(user: UserId, failed: Int = 0) {
        progress.getValue(user).apply {
            trySend(RefreshProgress.Finished(total = 2, failed = failed, appListFailure = null))
            close()
        }
        runCurrent()
    }

    @Test
    fun givenNotIndexed_whenEnsured_thenProgressIsFollowedToTheEnd() = runTest {
        val index = index()

        index.state(owner).test {
            assertThat(awaitItem()).isEqualTo(IndexState.NotIndexed)
            index.ensureIndexed(owner)
            assertThat(awaitItem()).isEqualTo(IndexState.Indexing(run = 0, done = 0, total = 0))
            runCurrent()
            progress.getValue(owner).trySend(RefreshProgress.Refreshing(done = 1, total = 2, current = "com.a"))
            assertThat(awaitItem()).isEqualTo(IndexState.Indexing(run = 0, done = 1, total = 2))
            finish(owner)
            assertThat(awaitItem()).isEqualTo(IndexState.Indexed(run = 0, total = 2, failed = 0, appListFailure = null, identity = PrivilegedIdentity.ROOT))
        }
    }

    @Test
    fun givenIndexingOrIndexed_whenEnsuredAgain_thenNotStartedAgain() = runTest {
        val index = index()

        index.ensureIndexed(owner)
        runCurrent()
        index.ensureIndexed(owner)
        finish(owner)
        index.ensureIndexed(owner)
        runCurrent()

        assertThat(components.refreshedUsers).containsExactly(owner)
    }

    @Test
    fun givenOneUserIndexed_whenAnotherIsEnsured_thenItIsIndexedApart() = runTest {
        val index = index()
        index.ensureIndexed(owner)
        runCurrent()
        finish(owner)

        index.ensureIndexed(work)
        runCurrent()

        assertThat(components.refreshedUsers).containsExactly(owner, work).inOrder()
    }

    @Test
    fun givenIndexing_whenReindexed_thenStartedOver() = runTest {
        val index = index()
        index.ensureIndexed(owner)
        runCurrent()

        index.reindex(owner)
        runCurrent()

        assertThat(components.refreshedUsers).containsExactly(owner, owner)
        index.state(owner).test {
            assertThat(awaitItem()).isEqualTo(IndexState.Indexing(run = 1, done = 0, total = 0))
        }
    }

    @Test
    fun givenIndexedWithoutAccess_whenAccessIsReady_thenEnsuringIndexesAgain() = runTest {
        channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))
        val index = index()
        index.ensureIndexed(owner)
        runCurrent()
        finish(owner)
        index.ensureIndexed(owner)
        runCurrent()
        assertThat(components.refreshedUsers).containsExactly(owner)

        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        index.ensureIndexed(owner)
        runCurrent()

        assertThat(components.refreshedUsers).containsExactly(owner, owner)
    }

    @Test
    fun givenAccessNotChecked_whenEnsured_thenItWaitsForTheCheck() = runTest {
        channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        val index = index()

        index.ensureIndexed(owner)
        runCurrent()
        assertThat(components.refreshedUsers).isEmpty()
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        runCurrent()

        assertThat(components.refreshedUsers).containsExactly(owner)
    }

    @Test
    fun givenAccessCheckNeverEnds_whenEnsured_thenWhatCanBeReadIsIndexedInTime() = runTest {
        channel.setState(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        val index = index()

        index.ensureIndexed(owner)
        advanceTimeBy(10_001)
        runCurrent()

        assertThat(components.refreshedUsers).containsExactly(owner)
    }

    @Test
    fun givenAccessLostDuringTheRun_whenIndexed_thenIncompleteAndIndexedAgainOnceAccessReturns() = runTest {
        val index = index()
        index.ensureIndexed(owner)
        runCurrent()

        channel.setState(ChannelState.Unavailable(UnavailableReason.FAILED))
        finish(owner)
        val state = index.state(owner).first() as IndexState.Indexed
        index.ensureIndexed(owner)
        runCurrent()
        assertThat(components.refreshedUsers).containsExactly(owner)
        channel.setState(ChannelState.Ready(PrivilegedIdentity.ROOT))
        index.ensureIndexed(owner)
        runCurrent()

        assertThat(state.identity).isNull()
        assertThat(state.incomplete).isTrue()
        assertThat(components.refreshedUsers).containsExactly(owner, owner)
    }

    @Test
    fun givenIdentityChangedDuringTheRun_whenIndexed_thenIncomplete() = runTest {
        val index = index()
        index.ensureIndexed(owner)
        runCurrent()

        channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))
        finish(owner)

        assertThat((index.state(owner).first() as IndexState.Indexed).incomplete).isTrue()
    }

    @Test
    fun givenAppsFailOrNoAccess_whenIndexed_thenIncomplete() {
        val complete = IndexState.Indexed(run = 0, total = 2, failed = 0, appListFailure = null, identity = PrivilegedIdentity.ROOT)

        assertThat(complete.incomplete).isFalse()
        assertThat(complete.copy(failed = 1).incomplete).isTrue()
        assertThat(complete.copy(identity = null).incomplete).isTrue()
    }

    @Test
    fun givenPartialProgress_whenTheRefreshThrows_thenPublishFailureAndAllowRetry() = runTest {
        val failure = IllegalStateException("Cannot read the remaining apps")
        components.refreshAllProgress = {
            flow {
                emit(RefreshProgress.Refreshing(1, 2, "com.a"))
                throw failure
            }
        }
        val index = index()

        index.ensureIndexed(owner)
        runCurrent()

        assertThat(index.state(owner).first()).isEqualTo(IndexState.Failed(run = 0, exception = failure))

        components.refreshAllProgress = { flowOf(RefreshProgress.Finished(total = 2, failed = 0, appListFailure = null)) }
        index.ensureIndexed(owner)
        runCurrent()

        val completed = index.state(owner).first() as IndexState.Indexed
        assertThat(completed.run).isEqualTo(1)
        assertThat(completed.hasAllApps).isTrue()
    }

    @Test
    fun givenSupersededRun_whenItFailsDuringCancellation_thenKeepTheNewRunState() = runTest {
        val finishOld = CompletableDeferred<Unit>()
        val finishNew = CompletableDeferred<Unit>()
        components.refreshAllProgress = {
            flow {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { finishOld.await() }
                    error("Old refresh failed in cleanup")
                }
            }
        }
        val index = index()
        index.ensureIndexed(owner)
        runCurrent()

        components.refreshAllProgress = {
            flow {
                emit(RefreshProgress.Refreshing(1, 2, "com.a"))
                finishNew.await()
                emit(RefreshProgress.Finished(total = 2, failed = 0, appListFailure = null))
            }
        }
        index.reindex(owner)
        runCurrent()
        finishOld.complete(Unit)
        runCurrent()

        assertThat(index.state(owner).first()).isEqualTo(IndexState.Indexing(run = 1, done = 1, total = 2))
        finishNew.complete(Unit)
        runCurrent()
        assertThat((index.state(owner).first() as IndexState.Indexed).run).isEqualTo(1)
    }
}
