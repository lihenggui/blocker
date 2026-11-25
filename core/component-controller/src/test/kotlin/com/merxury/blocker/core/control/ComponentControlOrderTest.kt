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

package com.merxury.blocker.core.control

import android.content.pm.PackageManager
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.control.ComponentWriteObserver
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val MAIN = declaration("Main")
private val SYNC = declaration("Sync", ComponentType.SERVICE)
private val OTHER_MAIN = declaration("Main", packageName = OTHER_PKG)
private val THIRD_MAIN = declaration("Main", packageName = "com.example.third")

/** Packages in order, one lock per package, duplicates and cancellation. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentControlOrderTest {

    private val fixture = ControlFixture()

    @Test
    fun givenSeveralPackages_whenApply_thenOneEventPerPackageInRequestOrderThenFinished() = runTest {
        fixture.control.apply(block(PM_ONLY, OTHER_MAIN, MAIN, THIRD_MAIN, SYNC)).test {
            val first = awaitItem() as ControlEvent.PackageDone
            assertThat(first.packageName).isEqualTo(OTHER_PKG)
            assertThat(first.outcomes.map { it.key.id }).containsExactly(OTHER_MAIN.id)
            val second = awaitItem() as ControlEvent.PackageDone
            assertThat(second.packageName).isEqualTo(PKG)
            assertThat(second.outcomes.map { it.key.id }).containsExactly(MAIN.id, SYNC.id).inOrder()
            assertThat((awaitItem() as ControlEvent.PackageDone).packageName).isEqualTo(THIRD_MAIN.id.packageName)
            assertThat(awaitItem()).isEqualTo(ControlEvent.Finished(achieved = 4, partial = 0, failed = 0))
            awaitComplete()
        }
    }

    @Test
    fun givenDuplicates_whenApply_thenEachComponentInOneOutcome() = runTest {
        val events = fixture.run(block(PM_ONLY, MAIN, SYNC, MAIN, OTHER_MAIN, MAIN))

        assertThat(events.outcomes().map { it.key.id }).containsExactly(MAIN.id, SYNC.id, OTHER_MAIN.id).inOrder()
        assertThat(fixture.executor.calls.map { it.components.size }).containsExactly(2, 1)
        assertThat(events.last()).isEqualTo(ControlEvent.Finished(achieved = 3, partial = 0, failed = 0))
    }

    @Test
    fun givenNoComponents_whenApply_thenOnlyFinishedAndNoConnect() = runTest {
        fixture.channel.mutableState.value = ChannelState.Unavailable(UnavailableReason.NOT_CHECKED)

        fixture.control.apply(block(PM_ONLY)).test {
            assertThat(awaitItem()).isEqualTo(ControlEvent.Finished(0, 0, 0))
            awaitComplete()
        }
        assertThat(fixture.channel.connectCount).isEqualTo(0)
    }

    @Test
    fun givenBatchRefused_whenApplyingPmOneAtATime_thenReportsEachWriteAsItCompletes() = runTest {
        val held = CompletableDeferred<Unit>()
        val protected = declaration("Protected")
        fixture.system.protected += protected.id
        fixture.system.beforeSet = { call ->
            if (call.components.singleOrNull()?.className == SYNC.id.className) held.await()
        }
        val observer = WriteObserver()

        val applying = launch { fixture.control.apply(block(PM_ONLY, MAIN, SYNC, protected), observer).toList() }
        runCurrent()

        try {
            assertThat(applying.isActive).isTrue()
            assertThat(observer.writes).containsExactly(listOf(MAIN.id))
        } finally {
            held.complete(Unit)
        }
        applying.join()
        assertThat(observer.writes).containsExactly(listOf(MAIN.id), listOf(SYNC.id)).inOrder()
    }

    @Test
    fun givenAQueuedControlCannotStartWriting_whenItsReadFinishes_thenNothingIsWritten() = runTest {
        val held = CompletableDeferred<Unit>()
        fixture.system.beforeRead = { held.await() }
        val observer = WriteObserver()
        val applying = launch { fixture.control.apply(block(BOTH, MAIN), observer).toList() }
        runCurrent()

        observer.allowed = false
        held.complete(Unit)
        applying.join()

        assertThat(observer.writes).isEmpty()
        assertThat(fixture.executor.calls).isEmpty()
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenOneExistingIfwBlock_whenApplyingAnother_thenReportsOnlyTheRuleThatChanged() = runTest {
        fixture.blockInIfw(MAIN)
        val observer = WriteObserver()

        fixture.control.apply(block(IFW_ONLY, MAIN, SYNC), observer).toList()

        assertThat(observer.writes).containsExactly(listOf(SYNC.id))
    }

    @Test
    fun givenTwoAppliesOnSamePackage_whenConcurrent_thenSecondStartsAfterFirstReadBack() = runTest {
        val held = CompletableDeferred<Unit>()
        fixture.system.beforeSet = { if (it.newState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) held.await() }

        val blocking = launch { fixture.run(block(PM_ONLY, MAIN)) }
        runCurrent()
        val unblocking = launch { fixture.run(unblock(MAIN)) }
        runCurrent()
        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 1 DISABLED")
        held.complete(Unit)
        blocking.join()
        unblocking.join()

        assertThat(fixture.system.log).containsExactly(
            "read $PKG",
            "set $PKG 1 DISABLED",
            "read $PKG",
            "read $PKG",
            "set $PKG 1 DEFAULT",
            "read $PKG",
        ).inOrder()
        assertThat(fixture.pm(MAIN)).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenPackageLockHeldByAnotherFeature_whenApply_thenWaitsForItBeforeReading() = runTest {
        val held = CompletableDeferred<Unit>()
        val other = launch { fixture.locks.withLock(PKG) { held.await() } }
        runCurrent()

        val applying = launch { fixture.run(block(PM_ONLY, MAIN)) }
        runCurrent()
        assertThat(fixture.system.log).isEmpty()
        held.complete(Unit)
        other.join()
        applying.join()

        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 1 DISABLED", "read $PKG").inOrder()
    }

    @Test
    fun givenApplyHeldOnOnePackage_whenAnotherPackageApplied_thenItProceeds() = runTest {
        val held = CompletableDeferred<Unit>()
        fixture.system.beforeSet = { if (it.components.single().packageName == PKG) held.await() }

        val blocking = launch { fixture.run(block(PM_ONLY, MAIN)) }
        runCurrent()
        val other = fixture.run(block(PM_ONLY, OTHER_MAIN))

        assertThat(other.outcomeOf(OTHER_MAIN).result).isEqualTo(ControlResult.ACHIEVED)
        assertThat(blocking.isActive).isTrue()
        held.complete(Unit)
        blocking.join()
    }

    @Test
    fun givenRequestOverManyPackages_whenTwoRunConcurrently_thenBothFinish() = runTest {
        val first = launch { fixture.run(block(PM_ONLY, MAIN, OTHER_MAIN, THIRD_MAIN)) }
        val second = launch { fixture.run(block(PM_ONLY, THIRD_MAIN, OTHER_MAIN, MAIN)) }
        first.join()
        second.join()

        assertThat(listOf(MAIN, OTHER_MAIN, THIRD_MAIN).map { fixture.pm(it) }).containsExactly(PmSetting.DISABLED, PmSetting.DISABLED, PmSetting.DISABLED)
    }

    @Test
    fun givenCollectorStopsAfterFirstPackage_whenApply_thenNextPackageNotStarted() = runTest {
        val first = fixture.control.apply(block(PM_ONLY, MAIN, OTHER_MAIN)).first()

        assertThat((first as ControlEvent.PackageDone).packageName).isEqualTo(PKG)
        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 1 DISABLED", "read $PKG").inOrder()
        assertThat(fixture.pm(OTHER_MAIN)).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenCancelledWhileChanging_whenCollecting_thenPackageReadBackAndNoFurtherPackage() = runTest {
        val held = CompletableDeferred<Unit>()
        fixture.system.beforeSet = { held.await() }
        val events = mutableListOf<ControlEvent>()

        val job = launch { fixture.control.apply(block(PM_ONLY, MAIN, OTHER_MAIN)).toList(events) }
        runCurrent()
        job.cancel()
        held.complete(Unit)
        job.join()

        assertThat(fixture.system.log).containsExactly("read $PKG", "set $PKG 1 DISABLED", "read $PKG").inOrder()
        assertThat(fixture.pm(MAIN)).isEqualTo(PmSetting.DISABLED)
        assertThat(events).isEmpty()
    }

    @Test
    fun givenPmWriteCancelledAfterItStarts_whenWakeTakesTheLock_thenJournalAndReadBackAreAlreadyStored() = runTest {
        val readingBack = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val persisted = mutableListOf<ControlEvent.PackageDone>()
        val journal = mutableSetOf<ComponentId>()
        val persistence = object : ComponentControlPersistence {
            override suspend fun beforePmBlock(app: AppKey, components: Collection<ComponentId>) {
                journal += components
            }

            override suspend fun afterPackage(event: ControlEvent.PackageDone, alreadyDisabled: Set<ComponentId>) {
                persisted += event
            }
        }
        fixture.system.beforeSet = {
            assertThat(journal).containsExactly(MAIN.id)
            assertThat(persisted).isEmpty()
        }
        fixture.system.beforeRead = {
            if (fixture.pm(MAIN, WORK) == PmSetting.DISABLED) {
                readingBack.complete(Unit)
                reply.await()
            }
        }

        val control = launch { fixture.control.apply(block(PM_ONLY, MAIN, OTHER_MAIN, user = WORK), persistence = persistence).collect() }
        readingBack.await()
        control.cancel()
        val wake = async {
            fixture.locks.withLock(PKG) {
                assertThat(journal).containsExactly(MAIN.id)
                assertThat(persisted.single().pmWrites).containsExactly(MAIN.id)
                assertThat(persisted.single().outcomes.single().state.pm).isEqualTo(PmSetting.DISABLED)
            }
        }
        runCurrent()
        assertThat(wake.isCompleted).isFalse()
        reply.complete(Unit)
        wake.await()
        control.join()
        assertThat(fixture.pm(OTHER_MAIN, WORK)).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenOwnPackageInAnotherUser_whenBlockingWithFirewallOrBoth_thenOnlyThePmRequestTakesEffect() = runTest {
        val own = declaration("Main", packageName = "com.merxury.blocker.test")
        fixture.control.apply(block(IFW_ONLY, own, user = WORK)).test {
            val event = awaitItem() as ControlEvent.PackageDone
            assertThat(event.outcomes.single().result).isEqualTo(ControlResult.SKIPPED)
            assertThat(event.outcomes.single().failures.values).containsExactly(ControlFailure.FirewallProtectedComponent)
            assertThat((awaitItem() as ControlEvent.Finished).skipped).isEqualTo(1)
            awaitComplete()
        }

        val outcome = fixture.run(block(BOTH, own, user = WORK)).outcomeOf(own, WORK)

        assertThat(outcome.result).isEqualTo(ControlResult.ACHIEVED)
        assertThat(fixture.pm(own, WORK)).isEqualTo(PmSetting.DISABLED)
        assertThat(fixture.pm(own, OWNER)).isEqualTo(PmSetting.DEFAULT)
        assertThat(fixture.ifwWrites).isEmpty()
    }

    @Test
    fun givenCancelledBeforeChanging_whenWaitingForLock_thenNothingSent() = runTest {
        val held = CompletableDeferred<Unit>()
        fixture.system.beforeSet = { held.await() }

        val holder = launch { fixture.run(block(PM_ONLY, MAIN)) }
        runCurrent()
        val waiter = launch { fixture.run(block(PM_ONLY, SYNC)) }
        runCurrent()
        waiter.cancel()
        held.complete(Unit)
        holder.join()
        waiter.join()

        assertThat(fixture.executor.calls.flatMap { call -> call.components.map { it.className } }).containsExactly(MAIN.id.className)
        assertThat(fixture.pm(SYNC)).isEqualTo(PmSetting.DEFAULT)
    }
}

private class WriteObserver : ComponentWriteObserver {
    var allowed = true
    val writes = mutableListOf<List<ComponentId>>()

    override fun canStart(): Boolean = allowed

    override fun onWrite(components: Collection<ComponentId>) {
        writes += components.toList()
    }
}
