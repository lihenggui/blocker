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

package com.merxury.blocker.core.domain.control

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.data.device.PackageControlActivity
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Which components the batches of every screen change, as each screen shows them in flight. */
@OptIn(ExperimentalCoroutinesApi::class)
class ComponentBatchRunnerTest {
    private val owner = UserId(0)
    private val work = UserId(10)
    private val receiver = ComponentId("com.a", "com.a.Receiver")
    private val service = ComponentId("com.b", "com.b.Service")

    @Test
    fun givenBatchesOfTwoScreens_whenRunning_thenEveryComponentOfBothIsInFlightForItsUser() = runTest {
        val runner = ComponentBatchRunner(FakeComponentStatusRepository(), backgroundScope, PackageControlActivity())

        runner.inFlight.test {
            assertThat(awaitItem()).isEmpty()
            val app = runner.reserve(BatchKey.App(AppKey("com.a", owner)), ControlGoal.BLOCK, listOf(receiver))!!
            assertThat(awaitItem()).containsExactly(ComponentKey(receiver, owner))
            val search = runner.reserve(BatchKey.Search(work), ControlGoal.UNBLOCK, listOf(receiver, service))!!
            assertThat(awaitItem()).containsExactly(ComponentKey(receiver, owner), ComponentKey(receiver, work), ComponentKey(service, work))

            assertThat(runner.isInFlight(ComponentKey(service, work))).isTrue()
            assertThat(runner.isInFlight(ComponentKey(service, owner))).isFalse()
            runner.release(app)
            assertThat(awaitItem()).containsExactly(ComponentKey(receiver, work), ComponentKey(service, work))
            runner.release(search)
            assertThat(awaitItem()).isEmpty()
        }
    }

    @Test
    fun givenReservedBatch_whenPreparationCompletes_thenSendPreparedComponentsAndReturnTheirOutcomes() = runTest {
        val repository = FakeComponentStatusRepository()
        val runner = ComponentBatchRunner(repository, backgroundScope, PackageControlActivity())
        val key = BatchKey.Search(owner)
        val run = runner.reserve(key, ControlGoal.BLOCK, listOf(receiver, service))!!
        val prepared = CompletableDeferred<ControlRequest>()
        runner.start(run) { prepared.await() }
        runCurrent()

        assertThat(repository.requests).isEmpty()
        assertThat(run.step.value.pending).containsExactly(receiver, service)

        val request = request()
        prepared.complete(request)
        runCurrent()

        assertThat(repository.requests).containsExactly(request)
        assertThat(run.outcomes.await().map { it.key }).containsExactly(ComponentKey(receiver, owner))
        assertThat(runner.current(key)).isNull()
    }

    @Test
    fun givenReservedBatch_whenPreparationThrows_thenOutcomesFailAndReservationIsReleased() = runTest {
        val repository = FakeComponentStatusRepository()
        val runner = ComponentBatchRunner(repository, backgroundScope, PackageControlActivity())
        val key = BatchKey.Search(owner)
        val run = runner.reserve(key, ControlGoal.BLOCK, listOf(receiver))!!
        val failure = IllegalStateException("Cannot prepare batch")

        runner.start(run) { throw failure }
        runCurrent()

        assertThat(runCatching { run.outcomes.await() }.exceptionOrNull()).hasMessageThat().isEqualTo(failure.message)
        assertThat(repository.requests).isEmpty()
        assertThat(runner.current(key)).isNull()
    }

    @Test
    fun givenBatchHasNotStarted_whenItsScopeIsCancelled_thenOutcomesAreCancelledWithoutAnEmptyList() = runTest {
        val job = Job()
        val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
        val runner = ComponentBatchRunner(FakeComponentStatusRepository(), scope, PackageControlActivity())
        val key = BatchKey.Search(owner)
        val run = runner.reserve(key, ControlGoal.BLOCK, listOf(receiver))!!
        val preparing = CompletableDeferred<Unit>()
        runner.start(run) {
            preparing.complete(Unit)
            request()
        }

        job.cancel()
        runCurrent()

        assertThat(preparing.isCompleted).isFalse()
        assertThat(runCatching { run.outcomes.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(runner.current(key)).isNull()
    }

    @Test
    fun givenBatchIsPreparing_whenItsScopeIsCancelled_thenOutcomesAreCancelledWithoutSendingARequest() = runTest {
        val job = Job()
        val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
        val repository = FakeComponentStatusRepository()
        val runner = ComponentBatchRunner(repository, scope, PackageControlActivity())
        val key = BatchKey.Search(owner)
        val run = runner.reserve(key, ControlGoal.BLOCK, listOf(receiver))!!
        val preparing = CompletableDeferred<Unit>()
        runner.start(run) {
            preparing.complete(Unit)
            awaitCancellation()
        }
        runCurrent()
        assertThat(preparing.isCompleted).isTrue()

        job.cancel()
        runCurrent()

        assertThat(runCatching { run.outcomes.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(repository.requests).isEmpty()
        assertThat(runner.current(key)).isNull()
    }

    @Test
    fun givenBatchHasCollectedAnOutcome_whenItsScopeIsCancelled_thenOutcomesAreCancelledWithoutPartialResults() = runTest {
        val job = Job()
        val scope = CoroutineScope(job + StandardTestDispatcher(testScheduler))
        val reported = CompletableDeferred<Unit>()
        val repository = object : ComponentStatusRepository by FakeComponentStatusRepository() {
            override fun control(request: ControlRequest) = flow {
                val outcome = ComponentOutcome(
                    ComponentKey(receiver, owner),
                    ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED),
                    ControlResult.ACHIEVED,
                    emptyMap(),
                )
                emit(ControlEvent.PackageDone(receiver.packageName, listOf(outcome)))
                reported.complete(Unit)
                awaitCancellation()
            }
        }
        val runner = ComponentBatchRunner(repository, scope, PackageControlActivity())
        val key = BatchKey.Search(owner)
        val run = runner.reserve(key, ControlGoal.BLOCK, listOf(receiver))!!
        runner.start(run) { request() }
        runCurrent()
        assertThat(reported.isCompleted).isTrue()

        job.cancel()
        runCurrent()

        assertThat(runCatching { run.outcomes.await() }.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(runner.current(key)).isNull()
    }

    private fun request() = ControlRequest(
        owner,
        listOf(ComponentDeclaration(receiver, ComponentType.RECEIVER, exported = false, enabledInManifest = true)),
        ControlGoal.BLOCK,
        setOf(ControlLayer.PACKAGE_MANAGER),
    )
}
