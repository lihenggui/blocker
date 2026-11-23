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

package com.merxury.blocker.core.appcontrol

import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.controllers.wake.WakeFixtureService
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.privileged.AbstractPrivilegedTransport
import com.merxury.blocker.core.privileged.calls.SetApplicationEnabledSetting
import com.merxury.blocker.core.privileged.connectedShizukuTransport
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.root.BlockerRootSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val TAG = "AppControlDeviceTest"

/**
 * Runs [AppControl] and [ServiceControl] against the real system. Most tests act on this test
 * package as installed in another user, while the copy in the owner, which runs the test, must
 * stay as it is. Clearing data stops the app in every user, so those tests act on a separate
 * debuggable app in the owner, whose files `run-as` can write and read.
 *
 * Arguments: `appControlTest=root` runs as root, through su granted to this package or, with
 * `rootVia=shizuku`, through a Shizuku server running as root; `appControlTest=shell` needs
 * Shizuku started through adb. Shizuku must be granted to this package, and its binder may need a
 * restart of Shizuku once the run has started. `otherUser=<id>` names a started work profile or
 * user that has this package, for example after `pm install-existing --user <id> <package>`.
 * `dataFixture=<package>` names an installed debuggable app whose data the tests may clear. Any
 * debuggable app works, even one without code that never runs: the tests write its marker files
 * themselves with `run-as <package> touch files/marker cache/marker`, so the files belong to the
 * app's uid as if it had written them. It must be installed for the owner only, with
 * `pm install --user 0`, or uninstalled from the other user with `pm uninstall --user <id>`: a
 * plain install also reaches every existing user.
 *
 * Shizuku reads an app's grant only when its server starts, so the order is: install this APK,
 * `pm grant <package> moe.shizuku.manager.permission.API_V23`, then start or restart the Shizuku
 * server, then run. A grant or reinstall while the server runs is not seen.
 */
class AppControlDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val self = context.packageName

    private val otherUser: UserId
        get() = UserId(requireNotNull(arguments.getString("otherUser")) { "Pass -e otherUser <id>" }.toInt())

    private val dataFixture: String
        get() = requireNotNull(arguments.getString("dataFixture")) { "Pass -e dataFixture <package>" }

    private val app get() = AppKey(self, otherUser)
    private val exported get() = ComponentKey(ComponentId(self, AppControlFixtureService::class.java.name), otherUser)
    private val notExported get() = ComponentKey(ComponentId(self, WakeFixtureService::class.java.name), otherUser)

    private lateinit var transport: AbstractPrivilegedTransport
    private lateinit var apps: AppControl
    private lateinit var services: ServiceControl

    @Before
    fun allowHiddenApis() {
        // The Shizuku transport calls hidden interfaces in this process, as the app does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    @After
    fun restoreOtherUser() = runBlocking<Unit> {
        if (!::transport.isInitialized) return@runBlocking
        if (shell("pm list packages --user ${otherUser.value} $self").isBlank()) {
            shell("pm install-existing --user ${otherUser.value} $self")
        }
        transport.execute(SetApplicationEnabledSetting(self, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, otherUser))
        apps.forceStop(app)
        shell("dumpsys deviceidle whitelist -$self")
    }

    private fun connect(identity: PrivilegedIdentity) {
        assumeTrue(arguments.getString("appControlTest") == identity.name.lowercase())
        transport = if (identity == PrivilegedIdentity.ROOT && arguments.getString("rootVia") != "shizuku") {
            RootTransport(BlockerRootSession(context), Dispatchers.IO).also {
                assertEquals(ChannelState.Ready(identity), runBlocking { it.connect() })
            }
        } else {
            connectedShizukuTransport(context, identity)
        }
        apps = PrivilegedAppControl(transport, transport)
        services = PrivilegedServiceControl(transport, transport)
        // Lets the fixture start while its app is in the background, as an app the user just used.
        shell("dumpsys deviceidle whitelist +$self")
    }

    @Test
    fun givenRoot_whenDisableAndEnableInOtherUser_thenOwnersSettingUnchanged() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        val owners = context.packageManager.getApplicationEnabledSetting(self)

        assertEquals(AppEnabledSetting.DISABLED_USER, apps.setEnabled(app, enabled = false).value())
        assertEquals(owners, context.packageManager.getApplicationEnabledSetting(self))
        assertEquals(AppEnabledSetting.ENABLED, apps.setEnabled(app, enabled = true).value())
        assertEquals(owners, context.packageManager.getApplicationEnabledSetting(self))
        assertNotStoredForAppMissingInOtherUser()
    }

    @Test
    fun givenRoot_whenStartStopAndForceStopInOtherUser_thenServiceFollows() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)

        startFixture()
        services.stop(exported).value()
        awaitRunning(false)

        startFixture()
        apps.forceStop(app).value()
        awaitRunning(false)
        // The goal of a stop holds already.
        services.stop(exported).value()
    }

    @Test
    fun givenRoot_whenListRunning_thenOwnerListedAndOtherUserNotPermitted() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        startFixture()

        assertTrue(self in apps.runningPackages(UserId.SYSTEM).value())
        logged("running packages of other user", apps.runningPackages(otherUser)).also { assertIs<CallFailure.NotPermitted>(it.failure()) }
        logged("running services of other user", services.runningServices(app)).also { assertIs<CallFailure.NotPermitted>(it.failure()) }
    }

    @Test
    fun givenRoot_whenClearCacheThenData_thenFilesTheAppWroteRemoved() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)
        val fixture = AppKey(dataFixture, UserId.SYSTEM)
        writeFixtureFiles()

        apps.clearCache(fixture).value()
        assertEquals(listOf(true, false), fixtureFiles())

        apps.clearData(fixture).value()
        assertEquals(listOf(false, false), fixtureFiles())
    }

    @Test
    fun givenRoot_whenUninstallForOtherUser_thenOwnerKeepsApp() = runBlocking<Unit> {
        connect(PrivilegedIdentity.ROOT)

        apps.uninstall(app).value()

        assertTrue(shell("pm list packages --user ${otherUser.value} $self").isBlank())
        assertEquals(self, context.packageManager.getPackageInfo(self, 0).packageName)
        assertIs<CallFailure.TargetNotFound>(apps.uninstall(app).failure())
    }

    @Test
    fun givenShell_whenEachOperation_thenSystemDecides() = runBlocking<Unit> {
        connect(PrivilegedIdentity.SHELL)

        assertEquals(AppEnabledSetting.DISABLED_USER, apps.setEnabled(app, enabled = false).value())
        assertEquals(AppEnabledSetting.ENABLED, apps.setEnabled(app, enabled = true).value())
        logged("start not exported", services.start(notExported)).also { assertIs<CallFailure.NotPermitted>(it.failure()) }
        logged("stop not exported", services.stop(notExported)).also { assertIs<CallFailure.NotPermitted>(it.failure()) }

        startFixture()
        // The service is listed as soon as it is asked to start; its process shows once it has attached.
        awaitTrue("the fixture process is listed") { self in apps.runningPackages(otherUser).value() }
        services.stop(exported).value()
        awaitRunning(false)
        startFixture()
        apps.forceStop(app).value()
        awaitRunning(false)
        assertFalse(self in apps.runningPackages(otherUser).value())

        val fixture = AppKey(dataFixture, UserId.SYSTEM)
        writeFixtureFiles()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            logged("clear cache", apps.clearCache(fixture)).also { assertIs<CallFailure.NotPermitted>(it.failure()) }
            assertEquals(listOf(true, true), fixtureFiles())
        } else {
            // Up to Android 8.1 the shell's DELETE_CACHE_FILES is enough.
            apps.clearCache(fixture).value()
            assertEquals(listOf(true, false), fixtureFiles())
        }
        apps.clearData(fixture).value()
        assertEquals(listOf(false, false), fixtureFiles())

        assertNotStoredForAppMissingInOtherUser()

        apps.uninstall(app).value()
        assertTrue(shell("pm list packages --user ${otherUser.value} $self").isBlank())
        assertEquals(self, context.packageManager.getPackageInfo(self, 0).packageName)
    }

    /**
     * The data fixture is installed for the owner only. Changing its setting for the other user
     * must fail and store nothing there: the identities the channel runs as are not held to
     * package visibility, so the system itself would accept it.
     */
    private suspend fun assertNotStoredForAppMissingInOtherUser() {
        val missing = AppKey(dataFixture, otherUser)
        assertIs<CallFailure.TargetNotFound>(apps.setEnabled(missing, enabled = false).failure())
        // The first such line is the user's package state; a later one lists its permissions.
        val userLine = shell("dumpsys package $dataFixture").lines().map { it.trim() }
            .first { it.startsWith("User ${otherUser.value}:") }
        assertTrue("installed=false" in userLine && "enabled=0" in userLine, userLine)
    }

    private suspend fun startFixture() {
        services.start(exported).value()
        awaitRunning(true)
    }

    // Root cannot list another user's services, so for root this reads them from dumpsys.
    private suspend fun awaitRunning(running: Boolean) = awaitTrue("the fixture service running is $running") { isFixtureRunning() == running }

    private suspend fun awaitTrue(what: String, condition: suspend () -> Boolean) {
        val met = withTimeoutOrNull(10.seconds) {
            while (!condition()) delay(100)
        }
        if (met == null) throw AssertionError("Not true within 10 s: $what")
    }

    private suspend fun isFixtureRunning(): Boolean = if (transport.state.value == ChannelState.Ready(PrivilegedIdentity.SHELL)) {
        exported.id in services.runningServices(app).value()
    } else {
        shell("dumpsys activity services $self").contains("u${otherUser.value} ${exported.id.flatten()}")
    }

    // A file in the data fixture's files and one in its cache, written as the app.
    private fun writeFixtureFiles() {
        shell("run-as $dataFixture mkdir -p files cache")
        shell("run-as $dataFixture touch files/marker cache/marker")
        assertEquals(listOf(true, true), fixtureFiles())
    }

    /** Whether the data fixture's files marker and cache marker exist. */
    private fun fixtureFiles(): List<Boolean> = listOf("files/marker", "cache/marker").map { path ->
        shell("run-as $dataFixture ls $path").trim() == path
    }

    private fun <R> logged(what: String, result: CallResult<R>): CallResult<R> = result.also {
        Log.i(TAG, "API ${Build.VERSION.SDK_INT} as ${(transport.state.value as? ChannelState.Ready)?.identity}: $what -> $it")
    }

    private fun <R> CallResult<R>.value(): R = when (this) {
        is CallResult.Success -> value
        is CallResult.Failure -> throw AssertionError("Call failed: $reason")
    }

    private fun CallResult<*>.failure(): CallFailure = (this as? CallResult.Failure)?.reason ?: throw AssertionError("Expected a failure, got $this")

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader()
        .use { it.readText() }
}
