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

package com.merxury.blocker.core.privileged.wake

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.control.PackageLocks
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.InMemoryRootGrantMemory
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.PrivilegedTransport
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledIfCurrent
import com.merxury.blocker.core.privileged.connectedShizukuTransport
import com.merxury.blocker.core.privileged.root.BlockerRootSession
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.core.ifw.ManagedWakeRules
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.WakeServiceFilter
import com.merxury.core.ifw.renderWakeRules
import com.merxury.core.ifw.wakeRuleFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TAG = "WakeWriteDeviceTest"
private const val IFW = "/data/system/ifw"

/**
 * Runs the wake feature's writes through the privileged channel against the real system.
 *
 * Argument `wakeWrites=<case>`: `root` (su allows this package, as with the PackageManager
 * controller type), `shizuku-root` (Shizuku runs as root and allows this package) or
 * `shizuku-shell` (Shizuku started through adb). As the shell the test checks that nothing was
 * written through su when su allows this package; on a device without su, check
 * `/data/system` from outside after the run. Without it every test is skipped. Shizuku reads an
 * app's grant only when its server starts: install this APK, `pm grant <package>
 * moe.shizuku.manager.permission.API_V23`, then start the server.
 */
class WakeWriteDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val self = context.packageName
    private val case: String? = InstrumentationRegistry.getArguments().getString("wakeWrites")
    private val fixture = ComponentName(context, WakeFixtureService::class.java)
    private val managedFile = "$IFW/${wakeRuleFileName(self, WakeRuleFileKind.MANAGED)}.xml"
    private val probeFile = "$IFW/${wakeRuleFileName(self, WakeRuleFileKind.PROBE)}.xml"
    private val journalFile = "/data/system/blocker-wake-$self.json"
    private val manualTarget = "blocker.test.first"
    private val manualFile = "$IFW/$manualTarget.xml"

    /** A channel that may read and delete the system files, set once a test runs. */
    private var verifier: PrivilegedTransport? = null

    @Before
    fun allowHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    /** Skips unless the case is one of [cases]; then connects its channel. */
    private fun connect(vararg cases: String): PrivilegedTransport {
        assumeTrue(case in cases)
        val connected = when (case) {
            "root" -> rootTransport()
            "shizuku-root" -> connectedShizukuTransport(context, PrivilegedIdentity.ROOT)
            else -> connectedShizukuTransport(context, PrivilegedIdentity.SHELL)
        }
        verifier = if (case == "shizuku-shell") rootTransportIfAllowed() else connected
        Log.i(TAG, "$case: channel ${connected.state.value}")
        return connected
    }

    /** A root channel when su allows this package, for checking the files as the shell; else null. */
    private fun rootTransportIfAllowed(): PrivilegedTransport? {
        val root = RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO)
        val state = runBlocking { root.connect() }
        Log.i(TAG, "$case: su for checking the files: $state")
        return root.takeIf { state == ChannelState.Ready(PrivilegedIdentity.ROOT) }
    }

    private fun rootTransport() = RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO).also {
        assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), runBlocking { it.connect() })
    }

    @After
    fun cleanUp() = runBlocking<Unit> {
        val channel = verifier ?: return@runBlocking
        // Each step on its own: one that fails must not keep the others from running.
        quietly { channel.execute(DeleteFiles(listOf(managedFile, probeFile, manualFile, journalFile))) }
        for (expected in listOf(COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_ENABLED)) {
            quietly { channel.execute(SetComponentEnabledIfCurrent(fixture, UserId.SYSTEM, expected, COMPONENT_ENABLED_STATE_DEFAULT)) }
        }
    }

    private suspend fun quietly(block: suspend () -> Any?) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Clean-up step failed", e)
        }
    }

    @Test
    fun givenWritableChannel_whenReplacingAndResettingManagedRules_thenFileIsExactAndOnlyTheTargetGoes() = runBlocking<Unit> {
        val writer = AuthorizedWakeRuleWriter(connect("root", "shizuku-root"), context)
        val other = "blocker.test.second"
        val filters = listOf(manualTarget, other).map { WakeServiceFilter(it, it, "$it.Service", "test.WAKE") }
        val xml = requireNotNull(renderWakeRules(filters))
        assertNull(inSystem(StatFile(managedFile)))
        assertNull(inSystem(StatFile(manualFile)))
        inSystem(WriteFile(manualFile, "<rules />"))

        writer.write(WakeRuleFileKind.MANAGED, xml)
        assertEquals(xml, inSystem(ReadFile(managedFile)))
        val stat = inSystem(StatFile(managedFile))
        Log.i(TAG, "$case: managed file $stat, directory ${inSystem(StatFile(IFW))}")
        // Written by uid 0, readable by system_server, which reads rule files as the system user.
        assertEquals("644 0:0", stat)

        writer.reset(manualTarget)
        assertEquals(renderWakeRules(filters.drop(1)), inSystem(ReadFile(managedFile)))
        assertEquals("<rules />", inSystem(ReadFile(manualFile)))

        writer.reset(other)
        assertNull(inSystem(StatFile(managedFile)))
        writer.write(WakeRuleFileKind.MANAGED, xml)
        writer.write(WakeRuleFileKind.PROBE, xml)
        writer.reset(null)
        assertNull(inSystem(StatFile(managedFile)))
        assertNull(inSystem(StatFile(probeFile)))
        assertEquals("<rules />", inSystem(ReadFile(manualFile)))
    }

    @Test
    fun givenWritableChannel_whenTestingPeerFilter_thenProbeWrittenAndDeletedAfter() = runBlocking<Unit> {
        val rules = ManagedWakeRules(AuthorizedWakeRuleWriter(connect("root", "shizuku-root"), context), context)
        context.getSharedPreferences("wake_protection", Context.MODE_PRIVATE).edit().clear().commit()
        val seen = async(Dispatchers.IO) {
            var present = false
            while (isActive && !present) {
                present = inSystem(StatFile(probeFile)) != null
                if (!present) delay(50)
            }
            present
        }

        val supported = withContext(Dispatchers.IO) { rules.supportsPeerFilter() }

        val probeSeen = seen.isCompleted && seen.await()
        seen.cancel()
        Log.i(TAG, "$case: peer filter supported $supported, probe seen while testing $probeSeen")
        assertTrue(probeSeen, "The probe file never appeared")
        assertNull(inSystem(StatFile(probeFile)))
    }

    @Test
    fun givenWritableChannel_whenJournalWrittenAndRead_thenRoundTripsInAPrivateFile() = runBlocking<Unit> {
        val journal = WakeComponentJournal(connect("root", "shizuku-root"), context)
        assertNull(inSystem(StatFile(journalFile)))
        assertNull(journal.read())

        journal.write("""[{"pathId":"first"}]""")
        journal.write("""[{"pathId":"second"}]""")

        assertEquals("""[{"pathId":"second"}]""", journal.read())
        val stat = inSystem(StatFile(journalFile))
        Log.i(TAG, "$case: journal $stat")
        // Root and the system uid can both read it, so the user can switch between them.
        assertEquals("640 0:1000", stat)
    }

    @Test
    fun givenJournalAnEarlierVersionWroteForRootOnly_whenReadAsRoot_thenItIsSharedWithTheSystemUid() = runBlocking<Unit> {
        val journal = WakeComponentJournal(connect("root", "shizuku-root"), context)
        inSystem(WriteFile(journalFile, """[{"pathId":"old"}]"""))
        inSystem(SetFileMode(journalFile, 0x180))
        assertEquals("600 0:0", inSystem(StatFile(journalFile)))

        assertEquals("""[{"pathId":"old"}]""", journal.read())

        assertEquals("640 0:1000", inSystem(StatFile(journalFile)))
    }

    @Test
    fun givenWritableChannel_whenComparingAndSetting_thenComponentChangesWithoutKillingItsProcess() = runBlocking<Unit> {
        val components = ChannelManagedComponentState(connect("root", "shizuku-root"), PackageLocks())
        val pm = context.packageManager
        assertEquals(COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(fixture))
        val connection = bindFixture()
        try {
            val pid = fixturePid()
            assertTrue(pid.isNotEmpty(), "The fixture process did not start")

            assertTrue(components.compareAndSet(self, fixture.className, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED))
            assertEquals(COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(fixture))
            // The setting is not what the caller expected: nothing changes.
            assertFalse(components.compareAndSet(self, fixture.className, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_ENABLED))
            assertEquals(COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(fixture))
            assertTrue(components.compareAndSet(self, fixture.className, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT))
            assertEquals(COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(fixture))

            delay(2_000)
            Log.i(TAG, "$case: fixture pid before $pid, after ${fixturePid()}")
            assertEquals(pid, fixturePid())
        } finally {
            context.unbindService(connection)
        }
    }

    @Test
    fun givenShellChannel_whenWriting_thenRefusedAndNothingWritten() = runBlocking<Unit> {
        val shell = connect("shizuku-shell")
        val writer = AuthorizedWakeRuleWriter(shell, context)
        val journal = WakeComponentJournal(shell, context)
        val xml = requireNotNull(renderWakeRules(listOf(WakeServiceFilter(self, self, fixture.className, "test.WAKE"))))

        val refused = listOf(
            runCatching { writer.write(WakeRuleFileKind.MANAGED, xml) }.exceptionOrNull(),
            runCatching { writer.write(WakeRuleFileKind.PROBE, xml) }.exceptionOrNull(),
            runCatching { journal.write("[]") }.exceptionOrNull(),
        )

        Log.i(TAG, "$case: refused ${refused.map { it?.message }}")
        refused.forEach {
            assertIs<IOException>(it)
            assertTrue(it.message.orEmpty().contains("NotPermitted"), "Not refused as the shell: ${it.message}")
        }
        if (verifier != null) listOf(managedFile, probeFile, journalFile).forEach { assertNull(inSystem(StatFile(it)), it) }
    }

    /** Runs [call] through [verifier], failing the test when it cannot. */
    private suspend fun <R> inSystem(call: PrivilegedCall<R>): R = when (val result = checkNotNull(verifier).execute(call)) {
        is CallResult.Success -> result.value
        is CallResult.Failure -> throw AssertionError("$call failed: ${result.reason}")
    }

    private fun bindFixture(): ServiceConnection {
        val connected = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = connected.countDown()
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent(context, WakeFixtureService::class.java), connection, Context.BIND_AUTO_CREATE))
        assertTrue(connected.await(30, TimeUnit.SECONDS), "The fixture service did not connect")
        return connection
    }

    private fun fixturePid(): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pidof $self:wake_fixture"))
        .bufferedReader().use { it.readText().trim() }
}
