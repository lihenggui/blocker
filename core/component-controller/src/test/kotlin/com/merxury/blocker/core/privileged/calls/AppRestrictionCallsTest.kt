// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.privileged.calls

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_DEFAULT
import android.app.AppOpsManager.MODE_ERRORED
import android.app.AppOpsManager.MODE_IGNORED
import android.content.pm.ApplicationInfo
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.DeepBackgroundRestriction
import com.merxury.blocker.core.model.system.DeepRestrictionSnapshot
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.decodeOutcome
import com.merxury.blocker.core.privileged.encodeOutcome
import com.merxury.blocker.core.privileged.marshallCall
import com.merxury.blocker.core.privileged.unmarshallCall
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class AppRestrictionCallsTest {
    private val work = UserId(10)
    private val system = FakeSystemServices()
    private val standby = FakeStandbyCommand()
    private val read = ReadDeepBackgroundRestriction(PACKAGE, work)
    private val set = SetDeepBackgroundRestricted(PACKAGE, work, restricted = true)

    @Before
    fun installInBothUsers() {
        for ((user, uid) in listOf(UserId.SYSTEM to 10_123, work to WORK_UID)) {
            system.packageManager.install(user.value, PACKAGE)
            checkNotNull(packageInfo(system, PACKAGE, 0, user)).applicationInfo = ApplicationInfo().apply { this.uid = uid }
        }
    }

    @Test fun givenDeepRestrictionMutation_whenCheckingRetryPolicy_thenItCannotBeReplayed() {
        assertThat(set.retryable).isFalse()
        assertThat(set.copy(restricted = false).retryable).isFalse()
        assertThat(read.retryable).isTrue()
    }

    @Test fun givenInstalledApp_whenRestrictingAndRestoring_thenUseTheRequestedUserAndRestoreEachOp() {
        system.appOps.modes[key(EXACT_ALARM)] = MODE_DEFAULT
        assertThat(read.run(system, standby::run).toReading().setting.anyActive).isFalse()
        assertThat(set.run(system, standby::run).toReading().setting.active).isTrue()
        assertThat(read.run(system, standby::run).toReading().setting).isEqualTo(DeepBackgroundRestriction(true, true, true, changeable = true))
        assertThat(system.appOps.modes).containsExactly(key(RUN_IN_BACKGROUND), MODE_IGNORED, key(EXACT_ALARM), MODE_IGNORED)
        assertThat(read.copy(user = UserId.SYSTEM).run(system, standby::run).toReading().setting.anyActive).isFalse()

        assertThat(set.copy(restricted = false).run(system, standby::run).toReading().setting.anyActive).isFalse()
        assertThat(read.run(system, standby::run).toReading().setting.anyActive).isFalse()
        assertThat(system.appOps.modes).containsExactly(key(RUN_IN_BACKGROUND), MODE_ALLOWED, key(EXACT_ALARM), MODE_DEFAULT)
        assertThat(standby.writes).containsExactly(
            listOf("/system/bin/am", "set-standby-bucket", "--user", "10", PACKAGE, "45"),
            listOf("/system/bin/am", "set-standby-bucket", "--user", "10", PACKAGE, "10"),
        ).inOrder()
    }

    @Test fun givenSystemPowerAllowlist_whenReadingAndWriting_thenReportsProtectedAndKeepsAllSettings() {
        system.deviceIdle.systemAllowlist += PACKAGE
        assertThat(read.run(system, standby::run).toReading().setting.changeable).isFalse()
        standby.calls.clear()
        assertThat(unmarshallCall(marshallCall(set))).isEqualTo(set)
        assertThat(set.copy(restricted = false).run(system, standby::run).changeable).isFalse()
        assertThat(system.backgroundUsageChanges).isEmpty()
        assertThat(standby.writes).isEmpty()
    }

    @Test
    @Config(sdk = [28])
    fun givenAndroidBeforeS_whenRestricting_thenUseRareBucketAndSkipExactAlarm() {
        assertThat(set.run(system, standby::run).toReading().setting.active).isTrue()
        val state = read.run(system, standby::run).toReading().setting

        assertThat(standby.writes.single().last()).isEqualTo("40")
        assertThat(state.active).isTrue()
        assertThat(state.exactAlarmSupported).isFalse()
        assertThat(system.appOps.modes.keys).containsExactly(key(RUN_IN_BACKGROUND))
    }

    @Test
    @Config(sdk = [24, 27])
    fun givenAndroidBeforeP_whenRestricting_thenOnlyAppOpsAreUsedAndReadResultParcels() {
        assertThat(set.needsPrivilegedProcess).isFalse()
        assertThat(set.run(system, standby::run).toReading().setting.active).isTrue()
        val result = decodeOutcome(read, encodeOutcome(unmarshallCall(marshallCall(read)), system))

        val expected = AppRestrictionState(null, MODE_IGNORED, null, changeable = true, bucketSupported = false, exactAlarmSupported = false, firstInstallTime = 0)
        assertThat(result).isEqualTo(CallResult.Success(expected))
        assertThat(expected.toReading().setting.bucketSupported).isFalse()
        assertThat(expected.toReading().setting.anyActive).isTrue()
        assertThat(expected.toReading().setting.active).isTrue()
        val clear = set.copy(restricted = false, expected = expected.snapshot(), firstInstallTime = 0)
        val cleared = decodeOutcome(clear, encodeOutcome(unmarshallCall(marshallCall(clear)), system))
        assertThat(cleared).isEqualTo(CallResult.Success(expected.copy(runInBackgroundMode = MODE_ALLOWED)))
        assertThat(standby.calls).isEmpty()
        assertThat(system.appOps.modes.keys).containsExactly(key(RUN_IN_BACKGROUND))
    }

    @Test fun givenSupportedButUnreadableBucket_whenReadingOrWriting_thenFailBeforeChangingAnything() {
        val failedCommands = listOf<(List<String>) -> String>(
            { throw IOException("am timed out") },
            { "Error: unknown command get-standby-bucket\n10" },
        )
        for (command in failedCommands) {
            val expected = "Cannot read the standby bucket for $PACKAGE in user 10"
            assertThat(assertThrows(IOException::class.java) { read.run(system, command) }.message).isEqualTo(expected)
            assertThat(assertThrows(IOException::class.java) { set.run(system, command) }.message).isEqualTo(expected)
            assertThat(system.backgroundUsageChanges).isEmpty()
        }
    }

    @Test fun givenBucketChangesButReadBackFails_whenRestricting_thenBothBucketAndAppOpsRollBack() {
        system.appOps.modes[key(EXACT_ALARM)] = MODE_DEFAULT
        var refuseNextRead = false
        val command: (List<String>) -> String = { args ->
            when {
                args[1] == "get-standby-bucket" && refuseNextRead -> {
                    refuseNextRead = false
                    "unknown"
                }

                else -> standby.run(args).also { if (args[1] == "set-standby-bucket" && args.last() == "45") refuseNextRead = true }
            }
        }

        assertThrows(IllegalStateException::class.java) { set.run(system, command) }

        assertThat(standby.writes.map { it.last() }).containsExactly("45", "10").inOrder()
        assertThat(read.run(system, standby::run).snapshot()).isEqualTo(DeepRestrictionSnapshot(10, MODE_ALLOWED, MODE_DEFAULT))
    }

    @Test fun givenAppOpWriteHasNoEffect_whenRestricting_thenEarlierWritesRollBackWithoutChangingBucket() {
        system.appOps.modes[key(EXACT_ALARM)] = MODE_DEFAULT
        system.appOps.ignoredWriteCode = EXACT_ALARM

        val failure = assertThrows(IllegalStateException::class.java) { set.run(system, standby::run) }

        assertThat(failure.message).contains("EXACT_ALARM")
        assertThat(standby.writes).isEmpty()
        assertThat(read.run(system, standby::run).snapshot()).isEqualTo(DeepRestrictionSnapshot(10, MODE_ALLOWED, MODE_DEFAULT))
    }

    @Test fun givenExactTarget_whenRestoring_thenTheNonDefaultModesAndBucketArePreserved() {
        val baseline = read.run(system, standby::run)
        val snapshot = DeepRestrictionSnapshot(30, MODE_ERRORED, MODE_DEFAULT)
        val call = set.copy(restricted = false, target = snapshot, expected = baseline.snapshot(), firstInstallTime = baseline.firstInstallTime)

        assertThat(unmarshallCall(marshallCall(call))).isEqualTo(call)
        assertThat(call.run(system, standby::run).snapshot()).isEqualTo(snapshot)
        assertThat(read.run(system, standby::run).snapshot()).isEqualTo(snapshot)
    }

    @Test fun givenInvalidPackage_whenReadingOrWriting_thenRejectBeforeAnySystemAccess() {
        val invalid = "--user 0; echo package"
        system.packageManager.flagsSeen.clear()
        assertThrows(IllegalArgumentException::class.java) { read.copy(packageName = invalid).run(system, standby::run) }
        assertThrows(IllegalArgumentException::class.java) { set.copy(packageName = invalid).run(system, standby::run) }
        assertThat(system.packageManager.flagsSeen).isEmpty()
        assertThat(system.backgroundUsageChanges).isEmpty()
        assertThat(standby.calls).isEmpty()
    }

    @Test fun givenReadableStandbyBucket_whenItsWriteFails_thenRestorePreviousAppOps() {
        system.appOps.modes[key(EXACT_ALARM)] = MODE_DEFAULT
        val refusingCommand: (List<String>) -> String = { args ->
            if (args[1] == "set-standby-bucket") throw IOException("am refused write")
            standby.run(args)
        }

        val error = assertThrows(IllegalStateException::class.java) { set.run(system, refusingCommand) }

        assertThat(error.message).isEqualTo("Cannot change the standby bucket for $PACKAGE in user 10")
        assertThat(read.run(system, standby::run).toReading().setting.anyActive).isFalse()
        assertThat(system.appOps.modes).containsExactly(key(RUN_IN_BACKGROUND), MODE_ALLOWED, key(EXACT_ALARM), MODE_DEFAULT)
    }

    private fun key(op: Int) = Triple(op, WORK_UID, PACKAGE)

    private class FakeStandbyCommand {
        val calls = mutableListOf<List<String>>()
        val writes get() = calls.filter { it[1] == "set-standby-bucket" }
        private val buckets = mutableMapOf<String, String>()

        fun run(args: List<String>): String {
            calls += args
            val user = args[3]
            return when (args[1]) {
                "get-standby-bucket" -> "\n${buckets[user] ?: "10"}\n"

                "set-standby-bucket" -> {
                    buckets[user] = args.last()
                    ""
                }

                else -> error("Unexpected command: $args")
            }
        }
    }

    private companion object {
        const val PACKAGE = "com.example.restricted"
        const val WORK_UID = 1_010_123
        const val RUN_IN_BACKGROUND = 63
        const val EXACT_ALARM = 107
    }
}
