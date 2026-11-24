// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.controllers.wake

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.InMemoryRootGrantMemory
import com.merxury.blocker.core.privileged.LargeReply
import com.merxury.blocker.core.privileged.PrivilegedTransport
import com.merxury.blocker.core.privileged.calls.ReadWakeSource
import com.merxury.blocker.core.privileged.connectedShizukuTransport
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.privileged.uid
import com.merxury.blocker.core.privileged.root.BlockerRootSession
import com.merxury.blocker.core.wake.proto.WakeStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "WakeDeviceTest"

class WakeFixtureService : Service() {
    override fun onBind(intent: Intent): IBinder = Binder()
}

/**
 * Reads the wake feature's evidence through the privileged channel, as background collection does.
 * Its writes are in [WakeWriteDeviceTest].
 *
 * Arguments: `wakeDeviceTest=root` reads through a root process started with su, or, with
 * `rootVia=shizuku`, through Shizuku started as root; `wakeDeviceTest=shell` reads through
 * Shizuku started through adb. Without `wakeDeviceTest` every test is skipped. Shizuku reads an
 * app's grant only when its server starts: install this APK,
 * `pm grant <package> moe.shizuku.manager.permission.API_V23`, then start the server.
 *
 * Each test removes the statsd config it installed.
 */
class WakeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private lateinit var transport: PrivilegedTransport
    private lateinit var identity: PrivilegedIdentity

    @Before
    fun allowHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    @After
    fun removeConfig() = runBlocking<Unit> {
        if (!::transport.isInitialized) return@runBlocking
        val removed = transport.execute(read(WakeCommand.REMOVE))
        if (removed is CallResult.Failure) Log.w(TAG, "Cannot remove the statsd config: ${removed.reason}")
    }

    private fun connect() {
        val mode = arguments.getString("wakeDeviceTest")
        assumeTrue("Pass -e wakeDeviceTest root|shell", mode == "root" || mode == "shell")
        identity = if (mode == "root") PrivilegedIdentity.ROOT else PrivilegedIdentity.SHELL
        transport = if (identity == PrivilegedIdentity.ROOT && arguments.getString("rootVia") != "shizuku") {
            RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO).also {
                assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), runBlocking { it.connect() })
            }
        } else {
            connectedShizukuTransport(context, identity)
        }
    }

    private fun read(command: WakeCommand, argument: Long = 0) = ReadWakeSource(command, UserId.SYSTEM, Process.myUid(), boot, argument, identity.uid)

    private suspend fun bytes(command: WakeCommand, argument: Long = 0): ByteArray = when (val result = transport.execute(read(command, argument))) {
        is CallResult.Success -> result.value
        is CallResult.Failure -> throw AssertionError("$command failed: ${result.reason}")
    }

    @Test
    fun givenChannel_whenCapturingWithTheDailySet_thenEverySourceIsReadThroughIt() = runBlocking<Unit> {
        connect()
        val collector = WakeCollector(context, transport)

        val started = SystemClock.elapsedRealtime()
        val capture = collector.capture(diagnostic = true, identity)
        val took = SystemClock.elapsedRealtime() - started

        capture.batches.forEach { Log.i(TAG, "${it.source}: ${it.observations.size} observations, gaps ${it.gaps}") }
        Log.i(TAG, "Capture as $identity on API ${Build.VERSION.SDK_INT}: ${capture.batches.size} batches in $took ms")
        assertFalse(capture.channelUnavailable)
        assertTrue(0 in capture.profiles)
        assertTrue(capture.packages.any { it.packageName == context.packageName })
        // Not read failures: what this release lacks, and gaps in the event buffer itself.
        val expectedGaps = buildSet {
            add("EVENTS/system/epoch")
            if (Build.VERSION.SDK_INT < 35) add("START_INFO")
            if (Build.VERSION.SDK_INT < 29) add("statsd")
        }
        val unread = capture.batches.filter { it.gaps.isNotEmpty() && it.source !in expectedGaps }
        assertTrue("Sources not read: ${unread.map { it.source to it.gaps }}", unread.isEmpty())
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(capture.batches.single { it.source == "statsd" }.subscriptions.isNotEmpty())
            val tail = collector.drain(capture.boot, identity)
            Log.i(TAG, "Drained ${tail.observations.size} observations, gaps ${tail.gaps}")
        }
    }

    @Test
    fun givenLargeOutputs_whenReadThroughTheChannel_thenTheyArriveWhole() = runBlocking<Unit> {
        connect()
        val large = when (val result = transport.execute(LargeReply(6 * 1024 * 1024))) {
            is CallResult.Success -> result.value
            is CallResult.Failure -> throw AssertionError("LargeReply failed: ${result.reason}")
        }
        assertEquals(6 * 1024 * 1024, large.size)
        assertTrue(large.withIndex().all { (index, byte) -> byte == (index % 251).toByte() })

        // A committed procstats period is a file that no longer changes, so both reads must match.
        val count = WakeProcStats.committedCount(String(bytes(WakeCommand.PROCSTATS_COUNT)))
        assumeTrue("Needs a committed procstats period", count >= 2)
        val periods = (1 until count).associateWith { bytes(WakeCommand.PROCSTATS_LAST, it.toLong()) }
        val (index, channel) = periods.maxBy { it.value.size }
        val shell = shell(WakeCommand.PROCSTATS_LAST.arguments(0, Process.myUid(), boot, index.toLong()).joinToString(" "))
        Log.i(TAG, "procstats --last $index: ${channel.size} bytes through the channel, ${shell.size} through the shell")
        assertTrue(channel.size > 64 * 1024)
        assertArrayEquals(shell, channel)
    }

    @Test
    fun givenRecorder_whenBindingAService_thenReportContainsItsRequest() = runBlocking<Unit> {
        connect()
        assumeTrue("Bounded statsd recording requires Android 10", Build.VERSION.SDK_INT >= 29)
        val id = WakeStatsCodec.configId(Process.myUid(), boot)
        bytes(WakeCommand.INSTALL)
        bytes(WakeCommand.RENEW)
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        var bound = false
        try {
            val action = "${context.packageName}.WAKE_FIXTURE"
            bound = context.bindService(Intent(context, WakeFixtureService::class.java).setAction(action), connection, Context.BIND_AUTO_CREATE)
            assertTrue(bound)
            assertTrue(latch.await(30, TimeUnit.SECONDS))
            val connections = WakeSystemEvidence.snapshot(WakeCommand.SERVICES, String(bytes(WakeCommand.SERVICES)), SystemClock.elapsedRealtimeNanos())
            assertTrue(
                "Missing full action from the live service binding",
                connections.any {
                    it.kind == "HeldConnection" && it.action == action && it.callerUid == Process.myUid() &&
                        it.targetUid == Process.myUid() && it.component?.endsWith("WakeFixtureService") == true
                },
            )
            var found = false
            for (attempt in 1..12) {
                delay(750)
                val data = bytes(WakeCommand.REPORT)
                val elapsed = SystemClock.elapsedRealtimeNanos()
                val batch = WakeStatsCodec.decode(data, System.currentTimeMillis() * 1_000_000 - elapsed, elapsed, identity.uid, id)
                found = batch.observations.any {
                    it.kind == "ServiceRequestEventReported" && it.targetUid == Process.myUid() &&
                        it.callerUid == Process.myUid() && it.component?.endsWith("WakeFixtureService") == true
                }
                if (found) break
            }
            assertTrue("Framework omitted the test service request", found)
        } finally {
            if (bound) context.unbindService(connection)
        }
    }

    /**
     * Installs the config through the instrumentation's shell, whose uid owns it and logs the
     * breadcrumbs: the shell, or root where adbd runs as root. The lease matches breadcrumbs of
     * the owner only, so the config must name that uid.
     */
    @Test
    fun givenActiveMetrics_whenLeaseExpires_thenRecordingStopsUntilRenewal() = runBlocking<Unit> {
        assumeTrue(arguments.getString("wakeDeviceTest") != null)
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val ui = instrumentation.uiAutomation
        val id = WakeStatsCodec.configId(Process.myUid(), 0) xor 0x200000000000L
        val label = WakeStatsCodec.leaseLabel(id)
        val owner = shellText("id -u").toInt()
        val config = WakeStats.Config.parseFrom(WakeStatsCodec.config(id, owner)).toBuilder()
        for ((index, metric) in config.metricActivationList.withIndex()) {
            config.setMetricActivation(index, metric.toBuilder().setEventActivation(0, metric.getEventActivation(0).toBuilder().setTtlSeconds(8)))
        }
        fun active(expected: Boolean): Boolean {
            val fd = ui.executeShellCommand("cmd stats dump-report $id --keep_data --include_current_bucket --proto")
            val report = ParcelFileDescriptor.AutoCloseInputStream(fd).use { WakeStats.ReportList.parseFrom(it) }
            check(report.configKey.uid == owner) { "The config is owned by ${report.configKey.uid}, not $owner" }
            val metrics = report.reportsList.lastOrNull()?.metricsList.orEmpty()
            return metrics.size == config.eventMetricCount && metrics.all { it.hasIsActive() && it.isActive == expected }
        }
        suspend fun awaitActive(expected: Boolean) {
            withContext(Dispatchers.IO) {
                withTimeout(30_000) {
                    while (!active(expected)) {
                        delay(350)
                        // statsd evaluates expired activations as events arrive.
                        shellText("cmd stats log-app-breadcrumb ${label xor 1} 1")
                    }
                }
            }
        }
        try {
            val descriptors = ui.executeShellCommandRw("cmd stats config update $id")
            ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { output ->
                ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]).use { it.write(config.build().toByteArray()) }
                output.readBytes()
            }
            awaitActive(false)
            shellText("cmd stats log-app-breadcrumb $label 1")
            awaitActive(true)
            awaitActive(false)
            shellText("cmd stats log-app-breadcrumb $label 1")
            awaitActive(true)
        } finally {
            shellText("cmd stats config remove $id")
        }
    }

    private fun shell(command: String): ByteArray = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }

    private fun shellText(command: String): String = String(shell(command)).trim()
}
