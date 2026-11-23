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

package com.merxury.blocker.core.controllers.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.ScriptedExecutor
import com.merxury.blocker.core.privileged.calls.ReadWakeJournal
import com.merxury.blocker.core.privileged.calls.ReplaceWakeJournal
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WakeComponentJournalTest {
    private val context = RuntimeEnvironment.getApplication()
    private val owner = context.packageName
    private val executor = ScriptedExecutor()
    private val journal = WakeComponentJournal(executor, context)

    @Test fun whenWritingThenReading_thenTheContentRoundTripsWithoutConnecting() = runTest {
        var stored: String? = null
        executor.answer = { call ->
            when (call) {
                is ReplaceWakeJournal -> CallResult.Success(Unit).also { stored = call.contents }
                is ReadWakeJournal -> CallResult.Success(stored)
                else -> error("Unexpected $call")
            }
        }

        assertThat(journal.read()).isNull()
        journal.write("[]")
        assertThat(journal.read()).isEqualTo("[]")

        assertThat(executor.runs).containsExactly(
            ReadWakeJournal(owner) to false,
            ReplaceWakeJournal(owner, "[]") to false,
            ReadWakeJournal(owner) to false,
        ).inOrder()
    }

    @Test fun givenRefusedOrUnavailable_whenReading_thenOnlyTheRefusalIsARefusal() = runTest {
        executor.answer = { CallResult.Failure(CallFailure.NotPermitted("Shizuku 12 cannot run it")) }
        assertThat(runCatching { journal.read() }.exceptionOrNull()).isInstanceOf(WakeCallRefusedException::class.java)

        executor.answer = { CallResult.Failure(CallFailure.ChannelUnavailable) }
        assertThat(runCatching { journal.read() }.exceptionOrNull()).isNotInstanceOf(WakeCallRefusedException::class.java)
    }

    @Test fun givenChannelNotReadyOrShell_whenReadingOrWriting_thenThrows() = runTest {
        for (failure in listOf(CallFailure.ChannelUnavailable, CallFailure.NotPermitted("shell"))) {
            executor.answer = { CallResult.Failure(failure) }

            assertThat(runCatching { journal.read() }.exceptionOrNull()).isInstanceOf(IOException::class.java)
            assertThat(runCatching { journal.write("[]") }.exceptionOrNull()).isInstanceOf(IOException::class.java)
        }
        assertThat(executor.runs.map { it.second }.distinct()).containsExactly(false)
    }
}
