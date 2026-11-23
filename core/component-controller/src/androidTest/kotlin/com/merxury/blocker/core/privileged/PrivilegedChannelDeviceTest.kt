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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.merxury.blocker.core.controllers.shizuku.DefaultShizukuApi
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import com.merxury.blocker.core.controllers.wake.WakeFixtureService
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.model.system.getOrNull
import com.merxury.blocker.core.privileged.calls.ApplicationEnabledSettings
import com.merxury.blocker.core.privileged.calls.ComponentEnabledSettings
import com.merxury.blocker.core.privileged.calls.GetPackageInfo
import com.merxury.blocker.core.privileged.calls.InstalledPackages
import com.merxury.blocker.core.privileged.calls.ListUsers
import com.merxury.blocker.core.privileged.calls.SetApplicationEnabledSetting
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledSettings
import com.merxury.blocker.core.privileged.root.RootTransport
import com.merxury.blocker.core.privileged.shizuku.ShizukuSystemServices
import com.merxury.blocker.core.privileged.shizuku.ShizukuTransport
import com.merxury.blocker.core.root.BlockerRootSession
import com.merxury.blocker.core.system.LocalPlatformPackages
import com.merxury.blocker.core.system.PlatformSystemPackageReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private const val TAG = "PrivilegedChannelTest"

private val SHIZUKU_WAIT = 2.minutes

// RootCallReply sends larger outcomes through a pipe.
private const val PIPED_ABOVE = 64 * 1024

/**
 * Runs the privileged calls against the real system, as root and as the shell, and reads and
 * changes another user's apps.
 *
 * Arguments: `privilegedRootTest=true` runs the root tests, through su granted to this test
 * package, or with `rootVia=shizuku` through a Shizuku server running as root;
 * `privilegedShellTest=true` needs Shizuku started through adb with this package granted, and
 * waits for its binder, which may need a restart of Shizuku once the run has started;
 * `otherUser=<id>` names a started profile or user that has this test package installed, for
 * example after `pm install-existing --user <id> <this package>`.
 */
class PrivilegedChannelDeviceTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val self = context.packageName
    private val fixture = ComponentName(context, WakeFixtureService::class.java)

    private val otherUser: UserId
        get() = UserId(requireNotNull(arguments.getString("otherUser")) { "Pass -e otherUser <id>" }.toInt())

    @Before
    fun allowHiddenApis() {
        // The Shizuku transport calls hidden interfaces in this process, as the app does.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("")
    }

    private fun rootTransport(): AbstractPrivilegedTransport {
        assumeTrue(arguments.getString("privilegedRootTest") == "true")
        if (arguments.getString("rootVia") == "shizuku") return shizukuTransport(PrivilegedIdentity.ROOT)
        return RootTransport(BlockerRootSession(context), Dispatchers.IO).also {
            assertEquals(ChannelState.Ready(PrivilegedIdentity.ROOT), runBlocking { it.connect() })
        }
    }

    private fun shellTransport(): ShizukuTransport {
        assumeTrue(arguments.getString("privilegedShellTest") == "true")
        return shizukuTransport(PrivilegedIdentity.SHELL)
    }

    private fun shizukuTransport(expected: PrivilegedIdentity): ShizukuTransport {
        val api = DefaultShizukuApi()
        val initializer = ShizukuInitializer(api)
        // Shizuku hands its binder to a granted app when the server starts, but not reliably to an
        // instrumentation process started later; restarting the server during this wait sends it.
        runBlocking { withTimeout(SHIZUKU_WAIT) { initializer.awaitBinder() } }
        return ShizukuTransport(initializer, api, ShizukuSystemServices(context), Dispatchers.IO).also {
            assertEquals(ChannelState.Ready(expected), runBlocking { it.connect() })
        }
    }

    @Test
    fun givenRoot_whenListUsers_thenOwnerAndOtherUserWithKindAndParent() = runBlocking<Unit> {
        val users = rootTransport().execute(ListUsers).value()

        assertEquals(UserKind.OWNER, users.single { it.id == UserId.SYSTEM }.kind)
        val other = users.single { it.id == otherUser }
        assertTrue(other.running, "$other")
        if (other.kind == UserKind.WORK_PROFILE) assertEquals(UserId.SYSTEM, other.parent)
    }

    @Test
    fun givenRoot_whenReadOtherUser_thenPackagesAndComponentSettingsRead() = runBlocking<Unit> {
        val root = rootTransport()

        assertTrue(root.execute(InstalledPackages(otherUser)).value().any { it.packageName == self })
        val settings = root.execute(ComponentEnabledSettings(self, listOf(fixture.className, "$self.NotDeclared"), otherUser)).value()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, settings[0])
        Log.i(TAG, "API ${Build.VERSION.SDK_INT}: a name the app does not declare reads as ${settings[1]}")
        // Android 14 answers DEFAULT for a name the app does not declare; older releases reject it.
        assertTrue(settings[1] == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || settings[1] == null, "$settings")
    }

    @Test
    fun givenRoot_whenSetComponentInOtherUser_thenOnlyThatUserChanges() = runBlocking<Unit> {
        val root = rootTransport()
        val read = ComponentEnabledSettings(self, listOf(fixture.className), otherUser)
        try {
            root.execute(SetComponentEnabledSettings(listOf(fixture), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, otherUser)).value()

            assertEquals(listOf(PackageManager.COMPONENT_ENABLED_STATE_DISABLED), root.execute(read).value())
            assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, context.packageManager.getComponentEnabledSetting(fixture))
        } finally {
            root.execute(SetComponentEnabledSettings(listOf(fixture), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, otherUser)).value()
        }
        assertEquals(listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT), root.execute(read).value())
    }

    @Test
    fun givenRoot_whenListEveryPackage_thenLargeAnswerArrivesWhole() = runBlocking<Unit> {
        val root = rootTransport()

        val call = InstalledPackages(UserId.SYSTEM)
        val packages = root.execute(call).value()
        val expected = shell("pm list packages --user 0").lines().count { it.startsWith("package:") }
        assertEquals(expected, packages.size)
        val listBytes = parceledSize { call.writeResult(packages, it) }
        Log.i(TAG, "${packages.size} packages took $listBytes bytes")
        assertTrue(listBytes > PIPED_ABOVE, "Too small to take the pipe: $listBytes")

        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS
        val frameworkCall = GetPackageInfo("android", flags, UserId.SYSTEM)
        val android = root.execute(frameworkCall).value()
        assertTrue(android.receivers.orEmpty().isNotEmpty() && android.activities.orEmpty().isNotEmpty())
        Log.i(TAG, "The components of android took ${parceledSize { frameworkCall.writeResult(android, it) }} bytes")
    }

    @Test
    fun givenRoot_whenReaderReadsOtherUser_thenAppsAndSettingsOfThatUser() = runBlocking<Unit> {
        val root = rootTransport()
        val reader = PlatformSystemPackageReader(PrivilegedPlatformPackages(root, LocalPlatformPackages(context)), Dispatchers.IO)

        assertContains(reader.users().getOrNull()!!.map { it.id }, otherUser)
        val app = reader.apps(otherUser).getOrNull()!!.single { it.key == AppKey(self, otherUser) }
        assertTrue(app.apkPath!!.endsWith(".apk"))
        val id = ComponentId(self, fixture.className)
        assertEquals(mapOf(id to PmSetting.DEFAULT), reader.pmSettings(AppKey(self, otherUser), listOf(id)).getOrNull())
    }

    @Test
    fun givenShell_whenReadOtherUser_thenUsersPackagesAndSettingsRead() = runBlocking<Unit> {
        val shell = shellTransport()

        assertContains(shell.execute(ListUsers).value().map { it.id }, otherUser)
        assertTrue(shell.execute(InstalledPackages(otherUser)).value().any { it.packageName == self })
        assertEquals(
            listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT),
            shell.execute(ComponentEnabledSettings(self, listOf(fixture.className), otherUser)).value(),
        )
    }

    @Test
    fun givenShell_whenSetComponent_thenNotPermittedAndChannelStillReady() = runBlocking<Unit> {
        val shell = shellTransport()

        val result = shell.execute(SetComponentEnabledSettings(listOf(fixture), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, otherUser))

        assertIs<CallFailure.NotPermitted>((result as CallResult.Failure).reason)
        assertEquals(ChannelState.Ready(PrivilegedIdentity.SHELL), shell.state.value)
        assertEquals(
            listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT),
            shell.execute(ComponentEnabledSettings(self, listOf(fixture.className), otherUser)).value(),
        )
    }

    @Test
    fun givenShell_whenSetAppInOtherUser_thenChangedAndRestored() = runBlocking<Unit> {
        val shell = shellTransport()
        val read = ApplicationEnabledSettings(listOf(self, "$self.not.installed"), otherUser)
        try {
            shell.execute(SetApplicationEnabledSetting(self, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, otherUser)).value()

            assertEquals(listOf(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, null), shell.execute(read).value())
        } finally {
            shell.execute(SetApplicationEnabledSetting(self, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, otherUser)).value()
        }
        assertEquals(listOf(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, null), shell.execute(read).value())
    }

    private fun <R> CallResult<R>.value(): R = when (this) {
        is CallResult.Success -> value
        is CallResult.Failure -> throw AssertionError("Call failed: $reason")
    }

    private fun parceledSize(write: (Parcel) -> Unit): Int {
        val parcel = Parcel.obtain()
        try {
            write(parcel)
            return parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader()
        .use { it.readText() }
}
