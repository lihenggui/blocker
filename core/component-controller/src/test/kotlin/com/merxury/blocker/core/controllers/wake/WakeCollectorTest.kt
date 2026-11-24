// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.wake.WakeBatch
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.ScriptedExecutor
import com.merxury.blocker.core.privileged.calls.ReadWakeSource
import com.merxury.blocker.core.wake.proto.WakeStats
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val BOOT = 7

private val PROFILE_SOURCES = listOf(WakeCommand.INPUT, WakeCommand.ACCESSIBILITY, WakeCommand.LISTENERS, WakeCommand.VPN)
private val EVERY_RUN = listOf(
    WakeCommand.INSTALL, WakeCommand.RENEW, WakeCommand.REPORT, WakeCommand.EVENTS, WakeCommand.EVENTS_HEAD,
    WakeCommand.PROCSTATS, WakeCommand.USERS, WakeCommand.SERVICES, WakeCommand.PROVIDERS, WakeCommand.PROCESSES,
    WakeCommand.NOTIFICATIONS,
)
private val DAILY = listOf(
    WakeCommand.INTENTS, WakeCommand.COMPANIONS, WakeCommand.WALLPAPER, WakeCommand.ROLES, WakeCommand.WIDGETS,
    WakeCommand.JOBS, WakeCommand.ALARMS, WakeCommand.SYNCS, WakeCommand.BATTERY, WakeCommand.IDLE_WHITELIST,
    WakeCommand.STANDBY_BUCKETS, WakeCommand.BACKGROUND_RESTRICTED,
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeCollectorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val executor = ScriptedExecutor()
    private val collector = WakeCollector(context, executor)

    /** The statsd config owner the reports name; the privileged process's uid. */
    private var reportOwner = 0

    /** What each command prints; a command not listed prints nothing. */
    private val outputs = mutableMapOf<WakeCommand, () -> ByteArray>(
        WakeCommand.USERS to { "Users:\n\tUserInfo{0:Owner:c13} running\n\tUserInfo{10:Work:1030} running\n".toByteArray() },
        WakeCommand.PACKAGES to { "package:/data/app/a/base.apk=example.app versionCode:12 uid:10123\n".toByteArray() },
        WakeCommand.PROCSTATS to { checkNotNull(javaClass.getResource("/wake/procstats-current.txt")).readBytes() },
        WakeCommand.REPORT to { report(reportOwner) },
    )

    private val reads: List<ReadWakeSource> get() = executor.runs.map { it.first as ReadWakeSource }

    @Before
    fun setUp() {
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, BOOT)
        executor.answer = { call -> answer(call) }
    }

    private fun answer(call: PrivilegedCall<*>): CallResult<ByteArray> {
        val read = call as ReadWakeSource
        return CallResult.Success(outputs[read.command]?.invoke() ?: ByteArray(0))
    }

    private fun report(owner: Int): ByteArray {
        val elapsed = SystemClock.elapsedRealtimeNanos()
        return WakeStats.ReportList.newBuilder()
            .setConfigKey(WakeStats.ConfigKey.newBuilder().setId(WakeStatsCodec.configId(Process.myUid(), BOOT)).setUid(owner))
            .addReports(
                WakeStats.Report.newBuilder()
                    .setCurrentReportElapsedNanos(elapsed)
                    .setCurrentReportWallClockNanos(System.currentTimeMillis() * 1_000_000)
                    .addMetrics(WakeStats.Metric.newBuilder().setMetricId(10476L).setEventMetrics(WakeStats.Events.newBuilder())),
            ).build().toByteArray()
    }

    private fun WakeCapture.batch(source: String): WakeBatch = batches.last { it.source == source }

    private fun WakeCapture.unreadable(): Map<String, List<String>> = batches.filter { it.gaps.isNotEmpty() }.associate { it.source to it.gaps }

    @Test
    fun givenReadyChannel_whenCapturing_thenEachSourceIsReadThroughTheExecutorForItsUser() = runTest {
        val capture = collector.capture(diagnostic = false, PrivilegedIdentity.ROOT)

        val expected = EVERY_RUN.map { it to 0 } + listOf(WakeCommand.PACKAGES to 0, WakeCommand.PACKAGES to 10) +
            PROFILE_SOURCES.flatMap { listOf(it to 0, it to 10) }
        assertThat(reads.map { it.command to it.user.value }).containsExactlyElementsIn(expected)
        assertThat(reads.map { it.appUid }.toSet()).containsExactly(Process.myUid())
        assertThat(reads.map { it.boot }.toSet()).containsExactly(BOOT)
        assertThat(reads.single { it.command == WakeCommand.EVENTS }.argument).isGreaterThan(0L)
        assertThat(capture.profiles).containsExactly(0, 10)
        assertThat(capture.packages.map { it.packageName }).containsExactly("example.app", "example.app")
        assertThat(capture.unreadable()).containsExactly("START_INFO", listOf("requires Android 15"))
        assertThat(capture.channelUnavailable).isFalse()
    }

    @Test
    fun givenDiagnosticRun_whenCapturing_thenTheDailySetIsReadToo() = runTest {
        collector.capture(diagnostic = false, PrivilegedIdentity.ROOT)
        val regular = reads.map { it.command }.toSet()
        executor.runs.clear()

        val capture = collector.capture(diagnostic = true, PrivilegedIdentity.ROOT)

        assertThat(reads.map { it.command }.toSet() - regular).containsExactlyElementsIn(DAILY)
        assertThat(capture.exemptions).isNotNull()
    }

    @Test
    fun givenAnyIdentity_whenCapturing_thenNoReadMayShowAPrompt() = runTest {
        for (identity in PrivilegedIdentity.entries) {
            collector.capture(diagnostic = true, identity)
            // An empty answer is no report; only that nothing prompted matters here.
            runCatching { collector.drain(BOOT.toString(), identity) }
        }

        assertThat(executor.runs).isNotEmpty()
        assertThat(executor.runs.map { (_, connect) -> connect }.toSet()).containsExactly(false)
    }

    @Test
    fun givenShellIdentity_whenCapturing_thenEverySourceItMayReadIsReadIncludingTheRecorder() = runTest {
        reportOwner = 2000

        val capture = collector.capture(diagnostic = true, PrivilegedIdentity.SHELL)

        assertThat(capture.unreadable()).containsExactly("START_INFO", listOf("requires Android 15"))
        assertThat(capture.batch("statsd").subscriptions).isNotEmpty()
        assertThat(reads.map { it.command }).containsAtLeastElementsIn(EVERY_RUN + DAILY)
    }

    @Test
    fun givenEachIdentity_whenCapturing_thenTheRecorderIsOwnedByTheUidItRunsAs() = runTest {
        val owners = mapOf(PrivilegedIdentity.ROOT to 0, PrivilegedIdentity.SYSTEM to 1000, PrivilegedIdentity.SHELL to 2000)
        for ((identity, owner) in owners) {
            reportOwner = owner

            val statsd = collector.capture(diagnostic = false, identity).batch("statsd")

            assertThat(statsd.gaps).isEmpty()
            assertThat(statsd.subscriptions).isNotEmpty()
            val metadata = context.getSharedPreferences("wake_stats_owner", Context.MODE_PRIVATE)
            assertThat(metadata.getInt("generation/$owner", -1)).isEqualTo(BOOT)
        }
    }

    @Test
    fun givenReportOfAnotherOwner_whenCapturing_thenConfigIsInstalledAgainOnce() = runTest {
        reportOwner = 2000

        val statsd = collector.capture(diagnostic = false, PrivilegedIdentity.ROOT).batch("statsd")

        assertThat(statsd.gaps).containsExactly("No report for this configuration owner")
        assertThat(reads.count { it.command == WakeCommand.INSTALL }).isEqualTo(2)
    }

    @Test
    fun givenUnavailableChannel_whenCapturing_thenEverySourceIsMarkedUnreadableAndTheCaptureReturns() = runTest {
        executor.answer = { CallResult.Failure(CallFailure.ChannelUnavailable) }

        val capture = collector.capture(diagnostic = true, PrivilegedIdentity.ROOT)

        assertThat(capture.channelUnavailable).isTrue()
        assertThat(capture.profiles).isEmpty()
        val unreadable = capture.unreadable() - "START_INFO"
        assertThat(unreadable.keys).containsAtLeast("statsd", "EVENTS", "PROCSTATS", "USERS", "SERVICES", "JOBS", "IDLE_WHITELIST")
        assertThat(unreadable.values.toSet()).containsExactly(listOf("privileged channel unavailable"))
        assertThat(capture.batch("authorization").gaps).isEmpty()
    }

    @Test
    fun givenChannelLostDuringCapture_whenCapturing_thenSourcesReadBeforeKeepTheirEvidence() = runTest {
        executor.answer = { call ->
            if ((call as ReadWakeSource).command == WakeCommand.SERVICES) {
                executor.answer = { CallResult.Failure(CallFailure.ChannelUnavailable) }
                CallResult.Failure(CallFailure.ChannelUnavailable)
            } else {
                answer(call)
            }
        }

        val capture = collector.capture(diagnostic = false, PrivilegedIdentity.SHELL)

        assertThat(capture.channelUnavailable).isTrue()
        assertThat(capture.profiles).containsExactly(0, 10)
        assertThat(capture.packages).hasSize(2)
        assertThat(capture.batch("PROCSTATS").gaps).isEmpty()
        assertThat(capture.batch("SERVICES").gaps).containsExactly("privileged channel unavailable")
        assertThat(capture.batch("VPN/10").gaps).containsExactly("privileged channel unavailable")
    }

    @Test
    fun givenServerThatRefusesReads_whenCapturing_thenSourcesSayWhyAndTheChannelStillCounts() = runTest {
        executor.answer = { CallResult.Failure(CallFailure.NotPermitted("Shizuku 12 cannot run it")) }

        val capture = collector.capture(diagnostic = false, PrivilegedIdentity.ROOT)

        assertThat(capture.channelUnavailable).isFalse()
        assertThat(capture.batch("USERS").gaps).containsExactly("not permitted: Shizuku 12 cannot run it")
    }

    @Test
    fun givenServiceRefusal_whenCapturing_thenOnlyThatSourceIsUnavailable() = runTest {
        outputs[WakeCommand.NOTIFICATIONS] = { "Permission Denial: can't dump NotificationManager".toByteArray() }
        outputs[WakeCommand.JOBS] = { "DUMP TIMEOUT".toByteArray() }

        val capture = collector.capture(diagnostic = true, PrivilegedIdentity.SHELL)

        assertThat(capture.batch("NOTIFICATIONS").gaps).containsExactly("Service unavailable")
        assertThat(capture.batch("JOBS").gaps).containsExactly("Service unavailable")
        assertThat(capture.batch("SERVICES").gaps).isEmpty()
    }

    @Test
    fun givenDrainFails_whenDraining_thenItIsSentOnceAndNotRetried() = runTest {
        executor.answer = { CallResult.Failure(CallFailure.ChannelUnavailable) }

        val failure = runCatching { collector.drain(BOOT.toString(), PrivilegedIdentity.ROOT) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(WakeSourceUnavailableException::class.java)
        assertThat(failure).hasMessageThat().isEqualTo("privileged channel unavailable")
        assertThat(reads.map { it.command }).containsExactly(WakeCommand.DRAIN)
        assertThat(reads.single().retryable).isFalse()
        assertThat(reads.single().user).isEqualTo(UserId.SYSTEM)
    }

    @Test
    fun givenDrain_whenDraining_thenTheTailIsDecodedForTheOwner() = runTest {
        reportOwner = 1000
        outputs[WakeCommand.DRAIN] = { report(1000) }

        val tail = collector.drain(BOOT.toString(), PrivilegedIdentity.SYSTEM)

        assertThat(tail.subscriptions).isNotEmpty()
        assertThat(reads.map { it.command }).containsExactly(WakeCommand.DRAIN)
    }

    @Test
    fun givenBootChanged_whenDraining_thenNothingIsRead() = runTest {
        val failure = runCatching { collector.drain("6", PrivilegedIdentity.ROOT) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)

        assertThat(executor.runs).isEmpty()
    }
}
