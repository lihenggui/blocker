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

package com.merxury.blocker.core.privileged.calls

import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.privileged.toCallFailure
import com.merxury.blocker.core.privileged.unmarshallCall
import com.merxury.blocker.core.privileged.wake.PROCSTATS_COUNT_PROBE
import com.merxury.blocker.core.privileged.wake.WakeCommand
import com.merxury.core.ifw.WakeRuleFileKind
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

private const val OWNER = "com.merxury.blocker"
private const val APP_UID = 10_111
private const val WORK_APP_UID = 1_010_111
private const val BOOT = 7
private const val SHELL_UID = 2000
private val SHELL_PREFIX = listOf("/system/bin/sh", "-c", """exec "$@" 2>/dev/null""", "blocker")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WakeCallsTest {

    @Test
    fun givenEveryCommand_whenReadInPrivilegedProcess_thenSameArgvWithoutSu() {
        WakeCommand.entries.forEach { command ->
            val argument = if (command == WakeCommand.PROCSTATS_LAST) 4L else 1_795_366_000_045L
            val call = ReadWakeSource(command, UserId(10), WORK_APP_UID, BOOT, argument, owner = SHELL_UID)

            assertThat(call.processCommand()).isEqualTo(SHELL_PREFIX + command.arguments(10, WORK_APP_UID, BOOT, argument))
            assertThat(call.processCommand()).doesNotContain("su")
        }
    }

    @Test
    fun givenCommandsWithArguments_whenRead_thenArgvMatchesTheWakeFeature() {
        fun argv(command: WakeCommand, user: Int = 0, argument: Long = 0) = ReadWakeSource(command, UserId(user), APP_UID, BOOT, argument, owner = SHELL_UID).processCommand().drop(SHELL_PREFIX.size)

        assertThat(argv(WakeCommand.EVENTS, argument = 1_795_366_000_045L)).containsAtLeast("-T", "1795366000.045").inOrder()
        assertThat(argv(WakeCommand.PROCSTATS_LAST, argument = 4)).containsExactly("dumpsys", "-t", "10", "procstats", "-c", "--last", "4").inOrder()
        assertThat(argv(WakeCommand.STANDBY_BUCKETS, user = 10).joinToString(" ")).isEqualTo("am get-standby-bucket --user 10")
        assertThat(argv(WakeCommand.PACKAGES, user = 10).last()).isEqualTo("10")
        assertThat(argv(WakeCommand.INPUT, user = 10)).containsExactly("settings", "--user", "10", "get", "secure", "default_input_method").inOrder()
        val config = (0x424c000000000000L or (APP_UID.toLong() shl 16) or BOOT.toLong()).toString()
        assertThat(argv(WakeCommand.DRAIN)).containsExactly("cmd", "stats", "dump-report", config, "--include_current_bucket", "--proto").inOrder()
    }

    @Test
    fun givenOutOfRangeValues_whenReadCreated_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReadWakeSource(WakeCommand.PROCSTATS, UserId(0), 2000, BOOT, owner = SHELL_UID) }
        assertThrows(IllegalArgumentException::class.java) { ReadWakeSource(WakeCommand.PROCSTATS, UserId(0), 0, BOOT, owner = SHELL_UID) }
        assertThrows(IllegalArgumentException::class.java) { ReadWakeSource(WakeCommand.EVENTS, UserId(0), APP_UID, BOOT, -1, owner = SHELL_UID) }
        assertThrows(IllegalArgumentException::class.java) { ReadWakeSource(WakeCommand.PROCSTATS_LAST, UserId(0), APP_UID, BOOT, 0, owner = SHELL_UID) }
        assertThrows(IllegalArgumentException::class.java) {
            ReadWakeSource(WakeCommand.PROCSTATS_LAST, UserId(0), APP_UID, BOOT, PROCSTATS_COUNT_PROBE.toLong(), owner = SHELL_UID)
        }
    }

    @Test
    fun givenOwnerThatIsNoPrivilegedUid_whenReadCreated_thenRejected() {
        listOf(-1, 1, APP_UID, 1_002_000).forEach { owner ->
            assertThrows("$owner", IllegalArgumentException::class.java) { ReadWakeSource(WakeCommand.DRAIN, UserId(0), APP_UID, BOOT, owner = owner) }
        }
    }

    @Test
    fun givenPrivilegedProcessRunsAsAnotherUid_whenDrainRuns_thenRefusedBeforeTheCommand() {
        // The channel changed from Shizuku as the shell to root while the collection ran.
        ShadowProcess.setUid(0)
        val drain = ReadWakeSource(WakeCommand.DRAIN, UserId(0), APP_UID, BOOT, owner = SHELL_UID)

        val failure = assertThrows(IllegalStateException::class.java) { drain.run(FakeSystemServices()) }

        assertThat(failure).hasMessageThat().isEqualTo("The privileged process runs as uid 0, not 2000")
        assertThat(failure.toCallFailure()).isInstanceOf(CallFailure.SystemError::class.java)
    }

    @Test
    fun givenForgedIndexInParcel_whenUnmarshalledInPrivilegedProcess_thenRejected() {
        val forged = ReadWakeSource(WakeCommand.PROCSTATS_LAST, UserId(0), APP_UID, BOOT, 4, owner = SHELL_UID)
        val bytes = marshallCall(forged)
        val parcel = Parcel.obtain()
        val tampered = try {
            parcel.unmarshall(bytes, 0, bytes.size)
            // The argument is written last but for the owner's uid.
            parcel.setDataPosition(parcel.dataSize() - Long.SIZE_BYTES - Int.SIZE_BYTES)
            parcel.writeLong(-5)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }

        assertThrows(IllegalArgumentException::class.java) { unmarshallCall(tampered) }
    }

    @Test
    fun whenRead_thenRunsInPrivilegedProcessAsShellToo() {
        val call = ReadWakeSource(WakeCommand.SERVICES, UserId(0), APP_UID, BOOT, owner = SHELL_UID)

        assertThat(call.needsPrivilegedProcess).isTrue()
        assertThat(call.needsSystemFiles).isFalse()
    }

    @Test
    fun givenEachCommand_whenRead_thenOnlyDrainIsNotRetryable() {
        val notRetryable = WakeCommand.entries.filterNot { ReadWakeSource(it, UserId(0), APP_UID, BOOT, 1, owner = SHELL_UID).retryable }

        assertThat(notRetryable).containsExactly(WakeCommand.DRAIN)
    }

    @Test
    fun givenLargeOutput_whenResultParceled_thenSameBytesArrive() {
        val call = ReadWakeSource(WakeCommand.PROCSTATS, UserId(0), APP_UID, BOOT, owner = SHELL_UID)
        // Robolectric keeps a parcel byte by byte; the device test sends megabytes.
        val output = ByteArray(256 * 1024) { it.toByte() }
        val parcel = Parcel.obtain()
        try {
            call.writeResult(output, parcel)
            parcel.setDataPosition(0)

            assertThat(call.readResult(parcel)).isEqualTo(output)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun givenOwnerOrTargetNotAPackageName_whenRuleFileCallCreated_thenRejected() {
        listOf("", "blocker", "../com.evil", "com.evil/x", "com..evil", "com.evil\u0000").forEach { name ->
            WakeRuleFileKind.entries.forEach { kind ->
                assertThrows(name, IllegalArgumentException::class.java) { ReplaceWakeRuleFile(name, kind, "<rules />") }
                assertThrows(name, IllegalArgumentException::class.java) { DeleteWakeRuleFile(name, kind) }
                assertThrows(name, IllegalArgumentException::class.java) { RemoveWakeRuleTarget(name, kind, "com.example") }
                assertThrows(name, IllegalArgumentException::class.java) { RemoveWakeRuleTarget(OWNER, kind, name) }
            }
        }
    }

    @Test
    fun givenFileCalls_whenMarshalled_thenSameCallsArriveAndNeedSystemFiles() {
        val calls = listOf(
            ReplaceWakeRuleFile(OWNER, WakeRuleFileKind.MANAGED, "<rules />"),
            DeleteWakeRuleFile(OWNER, WakeRuleFileKind.PROBE),
            RemoveWakeRuleTarget(OWNER, WakeRuleFileKind.MANAGED, "com.example"),
            ReadWakeJournal(OWNER),
            ReplaceWakeJournal(OWNER, "{}"),
            ReadWakeSource(WakeCommand.EVENTS, UserId(10), WORK_APP_UID, BOOT, 5, owner = SHELL_UID),
        )

        calls.forEach { assertThat(unmarshallCall(marshallCall(it))).isEqualTo(it) }
        assertThat(calls.dropLast(1).all { it.needsPrivilegedProcess && it.needsSystemFiles && it.retryable }).isTrue()
    }

    @Test
    fun givenJournalAtTheCap_whenReplaceCreated_thenAccepted() {
        ReplaceWakeJournal(OWNER, "x".repeat(256 * 1024))
    }

    @Test
    fun givenJournalEmptyOrOverTheCap_whenReplaceCreated_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReplaceWakeJournal(OWNER, "") }
        assertThrows(IllegalArgumentException::class.java) { ReplaceWakeJournal(OWNER, "x".repeat(256 * 1024 + 1)) }
        // The cap counts bytes, not characters: each of these is three bytes in UTF-8.
        assertThrows(IllegalArgumentException::class.java) { ReplaceWakeJournal(OWNER, "中".repeat(90_000)) }
    }

    @Test
    fun givenOwnerNotAPackageName_whenJournalCallCreated_thenRejected() {
        listOf("", "../x", "a/b", "single").forEach { owner ->
            assertThrows(owner, IllegalArgumentException::class.java) { ReadWakeJournal(owner) }
            assertThrows(owner, IllegalArgumentException::class.java) { ReplaceWakeJournal(owner, "{}") }
        }
    }
}
