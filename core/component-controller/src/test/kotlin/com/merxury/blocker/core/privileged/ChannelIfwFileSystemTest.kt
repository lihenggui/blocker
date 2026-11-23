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

package com.merxury.blocker.core.privileged

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.calls.DeleteIfwRuleFile
import com.merxury.blocker.core.privileged.calls.ListIfwRuleFiles
import com.merxury.blocker.core.privileged.calls.ReadIfwRuleFile
import com.merxury.blocker.core.privileged.calls.ReplaceIfwRuleFile
import com.merxury.core.ifw.IfwAccessException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

private const val PKG = "com.example"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelIfwFileSystemTest {

    private val executor = ScriptedExecutor()
    private val fileSystem = ChannelIfwFileSystem(executor)

    @Test
    fun whenReadAndWrite_thenEachConnects() = runTest {
        executor.answer = { CallResult.Success(if (it is ReadIfwRuleFile) "<rules />" else Unit) }

        assertThat(fileSystem.readRules(PKG)).isEqualTo("<rules />")
        fileSystem.writeRules(PKG, "<rules />")

        assertThat(executor.runs).containsExactly(
            ReadIfwRuleFile(PKG) to true,
            ReplaceIfwRuleFile(PKG, "<rules />") to true,
        ).inOrder()
    }

    @Test
    fun whenReadIfConnected_thenNeverConnects() = runTest {
        executor.answer = { CallResult.Success(null) }

        assertThat(fileSystem.readRulesIfConnected(PKG)).isNull()

        assertThat(executor.runs).containsExactly(ReadIfwRuleFile(PKG) to false)
    }

    @Test
    fun givenNoChannelOrShell_whenRead_thenAccessException() = runTest {
        listOf(CallFailure.ChannelUnavailable, CallFailure.NotPermitted("shell")).forEach { failure ->
            executor.answer = { CallResult.Failure(failure) }

            val thrown = runCatching { fileSystem.readRules(PKG) }.exceptionOrNull()

            assertThat(thrown).isInstanceOf(IfwAccessException::class.java)
        }
    }

    @Test
    fun givenSystemError_whenWrite_thenIoExceptionThatIsNoAccessException() = runTest {
        executor.answer = { CallResult.Failure(CallFailure.SystemError("EIO")) }

        val thrown = runCatching { fileSystem.writeRules(PKG, "<rules />") }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(thrown).isNotInstanceOf(IfwAccessException::class.java)
        assertThat(thrown).hasMessageThat().contains("EIO")
    }

    @Test
    fun givenWakeFiles_whenList_thenOnlyPackages() = runTest {
        executor.answer = { CallResult.Success(listOf("blocker-managed-wake", "blocker-wake-capability-probe", "com.a")) }

        assertThat(fileSystem.listRuleFiles()).containsExactly("com.a")
        assertThat(executor.runs.single().first).isEqualTo(ListIfwRuleFiles)
    }

    @Test
    fun whenDelete_thenAnswersWhetherThereWasAFile() = runTest {
        executor.answer = { CallResult.Success(false) }

        assertThat(fileSystem.deleteRules(PKG)).isFalse()
        assertThat(executor.runs.single()).isEqualTo(DeleteIfwRuleFile(PKG) to true)
    }
}

/** Answers every call with [answer] and records each with whether it could connect. */
private class ScriptedExecutor : PrivilegedExecutor {
    var answer: (PrivilegedCall<*>) -> CallResult<*> = { CallResult.Failure(CallFailure.ChannelUnavailable) }
    val runs = mutableListOf<Pair<PrivilegedCall<*>, Boolean>>()

    override suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R> = run(call, connect = true)

    override suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R> = run(call, connect = false)

    @Suppress("UNCHECKED_CAST")
    private fun <R> run(call: PrivilegedCall<R>, connect: Boolean): CallResult<R> {
        runs += call to connect
        return answer(call) as CallResult<R>
    }
}
