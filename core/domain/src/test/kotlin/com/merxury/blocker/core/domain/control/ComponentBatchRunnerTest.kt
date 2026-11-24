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
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Which components the batches of every screen change, as each screen shows them in flight. */
class ComponentBatchRunnerTest {
    private val owner = UserId(0)
    private val work = UserId(10)
    private val receiver = ComponentId("com.a", "com.a.Receiver")
    private val service = ComponentId("com.b", "com.b.Service")

    @Test
    fun givenBatchesOfTwoScreens_whenRunning_thenEveryComponentOfBothIsInFlightForItsUser() = runTest {
        val runner = ComponentBatchRunner(FakeComponentStatusRepository(), backgroundScope)

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
}
