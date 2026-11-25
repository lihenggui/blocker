// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.wake

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.ScriptedExecutor
import com.merxury.blocker.core.privileged.calls.DeleteWakeRuleFile
import com.merxury.blocker.core.privileged.calls.RemoveWakeRuleTarget
import com.merxury.blocker.core.privileged.calls.ReplaceWakeRuleFile
import com.merxury.core.ifw.WakeRuleFileKind
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthorizedWakeRuleWriterTest {
    private val context = RuntimeEnvironment.getApplication()
    private val owner = context.packageName
    private val executor = ScriptedExecutor().apply { answer = { CallResult.Success(Unit) } }
    private val writer = AuthorizedWakeRuleWriter(executor, context)

    @Test fun whenWritingEachKind_thenFileReplacedOrDeletedWithoutConnecting() = runTest {
        writer.write(WakeRuleFileKind.MANAGED, "<rules />")
        writer.write(WakeRuleFileKind.PROBE, "<rules />")
        writer.write(WakeRuleFileKind.PROBE, null)

        assertThat(executor.runs).containsExactly(
            ReplaceWakeRuleFile(owner, WakeRuleFileKind.MANAGED, "<rules />") to false,
            ReplaceWakeRuleFile(owner, WakeRuleFileKind.PROBE, "<rules />") to false,
            DeleteWakeRuleFile(owner, WakeRuleFileKind.PROBE) to false,
        ).inOrder()
    }

    @Test fun whenResettingEverything_thenBothFilesDeleted() = runTest {
        writer.reset(null)

        assertThat(executor.runs).containsExactly(
            DeleteWakeRuleFile(owner, WakeRuleFileKind.MANAGED) to false,
            DeleteWakeRuleFile(owner, WakeRuleFileKind.PROBE) to false,
        ).inOrder()
    }

    @Test fun whenResettingOnePackage_thenOnlyItsManagedRulesRemoved() = runTest {
        writer.reset("target.pkg")

        assertThat(executor.runs).containsExactly(RemoveWakeRuleTarget(owner, WakeRuleFileKind.MANAGED, "target.pkg") to false)
    }

    @Test fun givenNameNoWakeRuleHolds_whenResetting_thenNothingSent() = runTest {
        writer.reset("android")

        assertThat(executor.runs).isEmpty()
    }

    @Test fun givenChannelNotReady_whenWriting_thenThrowsWithoutConnecting() = runTest {
        executor.answer = { CallResult.Failure(CallFailure.ChannelUnavailable) }

        val thrown = runCatching { writer.write(WakeRuleFileKind.MANAGED, "<rules />") }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(thrown).hasMessageThat().contains("ChannelUnavailable")
        assertThat(executor.runs.map { it.second }).containsExactly(false)
    }
}
