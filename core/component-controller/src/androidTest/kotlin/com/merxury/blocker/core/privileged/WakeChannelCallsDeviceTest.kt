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

import android.content.ComponentName
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.appcontrol.AppControlFixtureService
import com.merxury.blocker.core.appcontrol.PrivilegedAppControl
import com.merxury.blocker.core.control.ControlFixtureActivity
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.privileged.calls.ComponentEnabledSettings
import com.merxury.blocker.core.privileged.calls.DeleteIfwRuleFile
import com.merxury.blocker.core.privileged.calls.DeleteWakeRuleFile
import com.merxury.blocker.core.privileged.calls.ForceStop
import com.merxury.blocker.core.privileged.calls.ListIfwRuleFiles
import com.merxury.blocker.core.privileged.calls.ReadWakeJournal
import com.merxury.blocker.core.privileged.calls.ReadWakeSource
import com.merxury.blocker.core.privileged.calls.RemoveWakeRuleTarget
import com.merxury.blocker.core.privileged.calls.ReplaceIfwRuleFile
import com.merxury.blocker.core.privileged.calls.ReplaceWakeJournal
import com.merxury.blocker.core.privileged.calls.ReplaceWakeRuleFile
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledIfCurrent
import com.merxury.blocker.core.privileged.calls.StartService
import com.merxury.blocker.core.privileged.root.BlockerRootSession
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.privileged.wake.WakeCommand
import com.merxury.blocker.core.privileged.wake.WakeJournalRead
import com.merxury.core.ifw.WakeRuleFileKind
import com.merxury.core.ifw.WakeServiceFilter
import com.merxury.core.ifw.renderWakeRules
import com.merxury.core.ifw.wakeRuleFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.parcelize.Parcelize
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val TAG = "WakeChannelCallsDeviceTest"

/** A reply of [size] bytes made in the privileged process, so it has to come back through the pipe. */
@Parcelize
internal data class LargeReply(val size: Int) : PrivilegedCall<ByteArray> {
    override val needsPrivilegedProcess: Boolean get() = true

    override val needsSystemFiles: Boolean get() = false

    override fun run(system: SystemServices): ByteArray = ByteArray(size) { (it % 251).toByte() }

    override fun writeResult(result: ByteArray, parcel: android.os.Parcel) {
        parcel.writeByteArray(result)
    }

    override fun readResult(parcel: android.os.Parcel): ByteArray = checkNotNull(parcel.createByteArray())
}

/**
 * Runs the calls the wake feature and app control use through the Shizuku user service and the
 * binder, or, for the root cases with `rootVia=su`, through a root process started with su.
 *
 * Arguments: `wakeChannelTest=root` needs Shizuku started as root, `wakeChannelTest=shell`
 * Shizuku started through adb. `otherUser=<id>` names a started work profile that has this
 * package, for example after `pm install-existing --user <id> <package>`. Without
 * `wakeChannelTest` every test is skipped. Shizuku reads an app's grant only when its server
 * starts: install this APK, `pm grant <package> moe.shizuku.manager.permission.API_V23`, then
 * start the server.
 *
 * The journal test leaves `/data/system/blocker-wake-<package>.json` behind, since no call
 * deletes a journal; remove it with root afterwards.
 */
class WakeChannelCallsDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val self = context.packageName
    private val appUid = Process.myUid()
    private val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private val otherUser: UserId
        get() = UserId(requireNotNull(arguments.getString("otherUser")) { "Pass -e otherUser <id>" }.toInt())

    private lateinit var transport: PrivilegedTransport

    // The uid the privileged process runs as, which each read names.
    private var owner = 0

    @Before
    fun allowHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    @After
    fun cleanUp() = runBlocking<Unit> {
        if (!::transport.isInitialized || arguments.getString("wakeChannelTest") != "root") return@runBlocking
        // Each step on its own: one that fails must not keep the others from running.
        WakeRuleFileKind.entries.forEach { quietly { transport.execute(DeleteWakeRuleFile(self, it)) } }
        quietly { transport.execute(DeleteIfwRuleFile(self)) }
        quietly { transport.execute(read(WakeCommand.REMOVE)) }
        val activity = ComponentName(self, ControlFixtureActivity::class.java.name)
        listOf(UserId.SYSTEM, otherUser).forEach { user ->
            quietly { transport.execute(SetComponentEnabledIfCurrent(activity, user, COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT)) }
        }
        quietly { transport.execute(ForceStop(self, otherUser)) }
        quietly { shell("dumpsys deviceidle whitelist -$self") }
    }

    private suspend fun quietly(block: suspend () -> Any?) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Clean-up step failed", e)
        }
    }

    private fun connect(identity: PrivilegedIdentity) {
        assumeTrue(arguments.getString("wakeChannelTest") == identity.name.lowercase())
        owner = identity.uid
        transport = if (identity == PrivilegedIdentity.ROOT && arguments.getString("rootVia") == "su") {
            RootTransport(BlockerRootSession(context), InMemoryRootGrantMemory(remembered = false), Dispatchers.IO).also {
                assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), runBlocking { it.connect() })
            }
        } else {
            connectedShizukuTransport(context, identity)
        }
    }

    private fun read(command: WakeCommand, user: UserId = UserId.SYSTEM, argument: Long = 0) = ReadWakeSource(command, user, appUid, boot, argument, owner)

    @Test
    fun givenShizukuRoot_whenReadWakeSources_thenEachAnswersFromThePrivilegedProcess() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        assertReadsWork()
        assertTrue(self in String(transport.execute(read(WakeCommand.PACKAGES, otherUser)).value()), "$self in user ${otherUser.value}")
    }

    @Test
    fun givenShizukuShell_whenReadWakeSources_thenEachAnswers() = runBlocking<Unit> {
        connect(PrivilegedIdentity.SHELL)
        assertReadsWork()
    }

    private suspend fun assertReadsWork() {
        val since = System.currentTimeMillis() - 10 * 60 * 1000
        val required = listOf(
            read(WakeCommand.USERS),
            read(WakeCommand.PACKAGES),
            read(WakeCommand.PROCSTATS),
            read(WakeCommand.SERVICES),
            read(WakeCommand.PROCESSES),
            read(WakeCommand.ALARMS),
            read(WakeCommand.JOBS),
            read(WakeCommand.BATTERY),
            read(WakeCommand.EVENTS, argument = since),
            read(WakeCommand.EVENTS_HEAD),
            read(WakeCommand.IDLE_WHITELIST),
            read(WakeCommand.INPUT),
        )
        required.forEach { call ->
            val bytes = transport.execute(call).value()
            Log.i(TAG, "${call.command}: ${bytes.size} bytes")
        }
        assertTrue("Owner" in String(transport.execute(read(WakeCommand.USERS)).value()))
        // Logged only, to record what this release offers.
        listOf(WakeCommand.STANDBY_BUCKETS, WakeCommand.BACKGROUND_RESTRICTED, WakeCommand.START_INFO, WakeCommand.ROLES, WakeCommand.PROCSTATS_COUNT).forEach { command ->
            Log.i(TAG, "$command: ${transport.execute(read(command)).describe()}")
        }
        val large = transport.execute(LargeReply(6 * 1024 * 1024)).value()
        assertEquals(6 * 1024 * 1024, large.size)
        assertTrue(large.withIndex().all { (index, byte) -> byte == (index % 251).toByte() })
    }

    @Test
    fun givenShizukuRoot_whenStatsCycle_thenInstallRenewAndRemoveWorkAndReportsAsTheReleaseAllows() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        assertTrue(read(WakeCommand.DRAIN).retryable.not())

        transport.execute(read(WakeCommand.INSTALL)).value()
        transport.execute(read(WakeCommand.RENEW)).value()
        val report = transport.execute(read(WakeCommand.REPORT))
        val drain = transport.execute(read(WakeCommand.DRAIN))
        Log.i(TAG, "API ${Build.VERSION.SDK_INT}: report ${report.describe()}, drain ${drain.describe()}")
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
            // Android 9's dump-report takes only [UID] NAME [--proto]: with --keep_data it prints
            // its usage text and exits with 0, so the call succeeds with text that is no report.
            assertTrue(String(report.value()).startsWith("usage:"))
            drain.value()
        } else {
            report.value()
            drain.value()
        }
        transport.execute(read(WakeCommand.REMOVE)).value()
    }

    @Test
    fun givenShizukuRoot_whenWakeRuleFilesChanged_thenFileInFirewallDirectoryFollows() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        val name = wakeRuleFileName(self, WakeRuleFileKind.MANAGED)
        val xml = checkNotNull(
            renderWakeRules(
                listOf(
                    WakeServiceFilter("com.example.caller", "com.example.first", "com.example.first.Wake", ""),
                    WakeServiceFilter("com.example.caller", "com.example.second", "com.example.second.Wake", ""),
                ),
            ),
        )

        transport.execute(ReplaceWakeRuleFile(self, WakeRuleFileKind.MANAGED, xml)).value()
        transport.execute(ReplaceWakeRuleFile(self, WakeRuleFileKind.MANAGED, xml)).value()
        transport.execute(ReplaceWakeRuleFile(self, WakeRuleFileKind.PROBE, xml)).value()
        assertTrue(name in ruleFiles())
        assertTrue(wakeRuleFileName(self, WakeRuleFileKind.PROBE) in ruleFiles())

        transport.execute(RemoveWakeRuleTarget(self, WakeRuleFileKind.MANAGED, "com.example.first")).value()
        transport.execute(RemoveWakeRuleTarget(self, WakeRuleFileKind.MANAGED, "com.example.first")).value()
        assertTrue(name in ruleFiles())
        transport.execute(RemoveWakeRuleTarget(self, WakeRuleFileKind.MANAGED, "com.example.second")).value()
        assertTrue(name !in ruleFiles(), "The file goes once no rule is left")

        transport.execute(DeleteWakeRuleFile(self, WakeRuleFileKind.PROBE)).value()
        transport.execute(DeleteWakeRuleFile(self, WakeRuleFileKind.PROBE)).value()
        transport.execute(RemoveWakeRuleTarget(self, WakeRuleFileKind.PROBE, "com.example.first")).value()
        assertTrue(ruleFiles().none { it.startsWith("blocker-") && it.endsWith(self) })
    }

    private suspend fun ruleFiles(): List<String> = transport.execute(ListIfwRuleFiles).value()

    @Test
    fun givenShizukuRoot_whenJournalReplaced_thenReadBackWhole() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        val first = """{"components":[]}"""
        val second = """{"components":["${"x".repeat(200 * 1024)}"]}"""

        transport.execute(ReplaceWakeJournal(self, first)).value()
        assertEquals(WakeJournalRead.Found(first), transport.execute(ReadWakeJournal(self)).value())
        transport.execute(ReplaceWakeJournal(self, second)).value()
        assertEquals(WakeJournalRead.Found(second), transport.execute(ReadWakeJournal(self)).value())
        assertEquals(WakeJournalRead.Missing, transport.execute(ReadWakeJournal("$self.absent")).value())
    }

    @Test
    fun givenShizukuRoot_whenCompareAndSetInOtherUser_thenTargetProcessKeepsRunning() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        val user = otherUser
        val activity = ComponentName(self, ControlFixtureActivity::class.java.name)
        // Lets the fixture start while its app is in the background, as an app the user just used.
        shell("dumpsys deviceidle whitelist +$self")
        transport.execute(StartService(ComponentName(self, AppControlFixtureService::class.java.name), user)).value()
        val pid = awaitFixturePid(user)

        val changed = SetComponentEnabledIfCurrent(activity, user, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED)
        assertTrue(transport.execute(changed).value())
        assertTrue(transport.execute(changed).value(), "Already in the target state")
        assertEquals(false, transport.execute(SetComponentEnabledIfCurrent(activity, user, COMPONENT_ENABLED_STATE_ENABLED, COMPONENT_ENABLED_STATE_DEFAULT)).value())
        assertEquals(listOf(COMPONENT_ENABLED_STATE_DISABLED), transport.execute(ComponentEnabledSettings(self, listOf(activity.className), user)).value())
        assertEquals(listOf(COMPONENT_ENABLED_STATE_DEFAULT), transport.execute(ComponentEnabledSettings(self, listOf(activity.className), UserId.SYSTEM)).value())
        assertTrue(transport.execute(SetComponentEnabledIfCurrent(activity, user, COMPONENT_ENABLED_STATE_DISABLED, COMPONENT_ENABLED_STATE_DEFAULT)).value())

        assertEquals(pid, fixturePid(user), "The fixture process survives both changes")
    }

    private suspend fun awaitFixturePid(user: UserId): String {
        repeat(50) {
            fixturePid(user)?.let { return it }
            delay(200)
        }
        throw AssertionError("The fixture service did not start in user ${user.value}")
    }

    private fun fixturePid(user: UserId): String? = shell("ps -A -o PID,USER,NAME").lines()
        .map { it.trim().split(Regex("\\s+")) }
        .firstOrNull { it.size == 3 && it[1].startsWith("u${user.value}_") && it[2] == "$self:app_control_fixture" }
        ?.first()

    @Test
    fun givenShizukuRoot_whenStartActivities_thenExportedAndHiddenStartInOwnerAndProfile() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        val apps = PrivilegedAppControl(transport, transport)
        listOf(UserId.SYSTEM, otherUser).forEach { user ->
            listOf(IfwFixtureActivity::class.java.name, HiddenFixtureActivity::class.java.name).forEach { name ->
                val key = ComponentKey(ComponentId(self, name), user)
                val before = creations(key)
                assertEquals(CallResult.Success(Unit), apps.startActivity(key), "$key")
                awaitCreation(key, before)
            }
        }
        val missing = ComponentKey(ComponentId(self, "$self.NoSuchActivity"), UserId.SYSTEM)
        assertIs<CallFailure.TargetNotFound>((apps.startActivity(missing) as CallResult.Failure).reason)
    }

    @Test
    fun givenShizukuRootAndFirewallBlock_whenStartActivity_thenReportedStartedButNotCreated() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        val apps = PrivilegedAppControl(transport, transport)
        val key = ComponentKey(ComponentId(self, IfwFixtureActivity::class.java.name), UserId.SYSTEM)
        val rule = """<rules><activity block="true" log="false"><component-filter name="$self/${key.id.className}" /></activity></rules>"""
        try {
            transport.execute(ReplaceIfwRuleFile(self, rule)).value()
            // The firewall reloads its directory a moment after a change; then starts stop creating.
            val blockedFrom = awaitStartsStopCreating(apps, key)

            assertEquals(CallResult.Success(Unit), apps.startActivity(key))
            delay(2_000)
            assertEquals(blockedFrom, creations(key), "The blocked start created no activity")
        } finally {
            transport.execute(DeleteIfwRuleFile(self))
            // Leaves the next test an activity the firewall no longer blocks.
            awaitStartCreates(apps, key)
        }
    }

    private suspend fun awaitStartsStopCreating(apps: PrivilegedAppControl, key: ComponentKey): Int {
        repeat(20) {
            val before = creations(key)
            apps.startActivity(key)
            delay(1_000)
            val after = creations(key)
            if (after == before) return after
        }
        throw AssertionError("The firewall never blocked $key")
    }

    private suspend fun awaitStartCreates(apps: PrivilegedAppControl, key: ComponentKey) {
        repeat(20) {
            val before = creations(key)
            apps.startActivity(key)
            delay(1_000)
            if (creations(key) > before) return
        }
        Log.w(TAG, "$key still blocked after the rule was deleted")
    }

    @Test
    fun givenShizukuShell_whenStartActivities_thenExportedStartsAndHiddenNotPermitted() = runBlocking<Unit> {
        connect(PrivilegedIdentity.SHELL)
        val apps = PrivilegedAppControl(transport, transport)
        val exported = ComponentKey(ComponentId(self, IfwFixtureActivity::class.java.name), UserId.SYSTEM)
        val before = creations(exported)

        assertEquals(CallResult.Success(Unit), apps.startActivity(exported))
        awaitCreation(exported, before)
        val hidden = apps.startActivity(ComponentKey(ComponentId(self, HiddenFixtureActivity::class.java.name), UserId.SYSTEM))
        assertIs<CallFailure.NotPermitted>((hidden as CallResult.Failure).reason)
    }

    @Test
    fun givenShizukuShell_whenWakeWrites_thenNotPermitted() = runBlocking<Unit> {
        connect(PrivilegedIdentity.SHELL)
        val activity = ComponentName(self, ControlFixtureActivity::class.java.name)
        val writes = listOf(
            ReplaceWakeRuleFile(self, WakeRuleFileKind.MANAGED, "<rules />"),
            DeleteWakeRuleFile(self, WakeRuleFileKind.PROBE),
            RemoveWakeRuleTarget(self, WakeRuleFileKind.MANAGED, "com.example.first"),
            ReadWakeJournal(self),
            ReplaceWakeJournal(self, "{}"),
            SetComponentEnabledIfCurrent(activity, UserId.SYSTEM, COMPONENT_ENABLED_STATE_DEFAULT, COMPONENT_ENABLED_STATE_DISABLED),
        )

        writes.forEach { call ->
            val result = transport.execute(call)
            assertIs<CallFailure.NotPermitted>((result as? CallResult.Failure)?.reason, "$call: $result")
        }
        assertEquals(listOf(COMPONENT_ENABLED_STATE_DEFAULT), transport.execute(ComponentEnabledSettings(self, listOf(activity.className), UserId.SYSTEM)).value())
    }

    // [user, token, task, component, action, type, uri, flags]: am_create_activity up to Android 9,
    // wm_create_activity from Android 10, when activities moved to the window manager.
    private fun creations(key: ComponentKey): Int {
        val component = ComponentName(key.id.packageName, key.id.className).flattenToShortString()
        return shell("logcat -b events -d -s am_create_activity wm_create_activity").lines().count { "[${key.userId.value}," in it && component in it }
    }

    private suspend fun awaitCreation(key: ComponentKey, before: Int) {
        repeat(50) {
            if (creations(key) > before) return
            delay(200)
        }
        throw AssertionError("$key was not created")
    }

    private fun <R> CallResult<R>.value(): R = when (this) {
        is CallResult.Success -> value
        is CallResult.Failure -> throw AssertionError("Call failed: $reason")
    }

    private fun CallResult<*>.describe(): String = when (this) {
        is CallResult.Success -> "ok, ${(value as? ByteArray)?.size} bytes"
        is CallResult.Failure -> "failed: $reason"
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader()
        .use { it.readText() }
}
