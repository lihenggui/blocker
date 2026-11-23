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

package com.merxury.blocker.core.privileged.root

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.MeasureInPrivilegedProcess
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.decodeOutcome
import com.merxury.blocker.core.privileged.marshallCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.parcelize.Parcelize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Released by the test once the fast call has answered.
private val release = CountDownLatch(1)

/** A call that blocks, as one waiting for a system callback does, until the test releases it. */
@Parcelize
internal data object WaitForRelease : PrivilegedCall<Boolean> {
    override fun run(system: SystemServices): Boolean = release.await(10, TimeUnit.SECONDS)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RootCallCommandTest {

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
    @Test
    fun givenSlowCallRunning_whenFastCallSent_thenFastAnswersFirst() = runBlocking {
        // librootkotlinx starts every command on the root process's single main thread.
        newSingleThreadContext("root main").use { main ->
            val slow = async(main) { RootCallCommand(CallBytes.request(marshallCall(WaitForRelease))).execute() }

            val fastCall = MeasureInPrivilegedProcess("fast")
            val fast = withTimeout(5_000) {
                async(main) { RootCallCommand(CallBytes.request(marshallCall(fastCall))).execute() }.await()
            }
            assertThat(slow.isCompleted).isFalse()
            release.countDown()

            assertThat(decodeOutcome(fastCall, fast.read())).isEqualTo(CallResult.Success(4))
            assertThat(decodeOutcome(WaitForRelease, slow.await().read())).isEqualTo(CallResult.Success(true))
        }
    }
}
