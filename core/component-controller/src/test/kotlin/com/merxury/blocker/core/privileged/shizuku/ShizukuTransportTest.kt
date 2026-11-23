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

package com.merxury.blocker.core.privileged.shizuku

import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.controllers.shizuku.IShizukuInitializer
import com.merxury.blocker.core.controllers.shizuku.RegisterShizukuResult
import com.merxury.blocker.core.controllers.shizuku.ShizukuApi
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.privileged.EchoInPrivilegedProcess
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.calls.ApplicationEnabledSettings
import com.merxury.blocker.core.privileged.calls.ForceStop
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import rikka.shizuku.Shizuku
import kotlin.time.Duration.Companion.seconds

private const val PKG = "com.example"
private val WORK = UserId(10)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShizukuTransportTest {

    private val scope = TestScope()
    private val initializer = FakeShizukuInitializer()
    private val shizuku = FakeShizukuBinder()
    private val system = FakeSystemServices()
    private val connector = FakeUserServiceConnector(system)
    private val transport = ShizukuTransport(
        initializer,
        shizuku,
        system,
        ShizukuUserService(connector, attachTimeout = 15.seconds),
        StandardTestDispatcher(scope.testScheduler),
    )

    @Test
    fun givenEachRegisterResult_whenConnect_thenMatchingState() = scope.runTest {
        val cases = mapOf(
            RegisterShizukuResult.Granted(0) to ChannelState.Ready(PrivilegedIdentity.ROOT),
            RegisterShizukuResult.Granted(2000) to ChannelState.Ready(PrivilegedIdentity.SHELL),
            RegisterShizukuResult.Granted(1000) to ChannelState.Ready(PrivilegedIdentity.SYSTEM),
            RegisterShizukuResult.Granted(-1) to ChannelState.Unavailable(UnavailableReason.FAILED),
            RegisterShizukuResult.Denied to ChannelState.Unavailable(UnavailableReason.DENIED),
            RegisterShizukuResult.NotRunning to ChannelState.Unavailable(UnavailableReason.NOT_RUNNING),
            RegisterShizukuResult.UnsupportedVersion to ChannelState.Unavailable(UnavailableReason.UNSUPPORTED_VERSION),
            RegisterShizukuResult.TimedOut to ChannelState.Unavailable(UnavailableReason.TIMED_OUT),
            RegisterShizukuResult.Failed(IllegalStateException()) to ChannelState.Unavailable(UnavailableReason.FAILED),
        )

        cases.forEach { (registered, expected) ->
            initializer.result = registered
            assertThat(transport.connect()).isEqualTo(expected)
            assertThat(transport.state.value).isEqualTo(expected)
        }
    }

    @Test
    fun givenShellGranted_whenExecute_thenRunsAgainstShizukuServices() = scope.runTest {
        system.packageManager.install(10, PKG)
        system.packageManager.appSettings[PKG to 10] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val result = transport.execute(ApplicationEnabledSettings(listOf(PKG), WORK))

        assertThat(result).isEqualTo(CallResult.Success(listOf(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)))
        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SHELL))
    }

    @Test
    fun givenShizukuStopped_whenExecute_thenChannelUnavailableAndNotRunning() = scope.runTest {
        transport.connect()
        shizuku.alive = false

        assertThat(transport.execute(ForceStop(PKG, WORK))).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(system.activityManager.forceStopped).isEmpty()
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
    }

    @Test
    fun givenShizukuDiesDuringCall_whenExecute_thenChannelUnavailableAndNotRunning() = scope.runTest {
        transport.connect()
        system.packageManager.failure = IllegalStateException("binder haven't been received")
        shizuku.dieOnNextPing = true

        val result = transport.execute(ApplicationEnabledSettings(listOf(PKG), WORK))

        assertThat(result).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
    }

    @Test
    fun givenShellRefused_whenExecute_thenNotPermittedAndStillReady() = scope.runTest {
        transport.connect()
        system.packageManager.failure = SecurityException("Shell cannot change component state")

        val result = transport.execute(ApplicationEnabledSettings(listOf(PKG), WORK))

        assertThat(result).isEqualTo(CallResult.Failure(CallFailure.NotPermitted("Shell cannot change component state")))
        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SHELL))
    }

    @Test
    fun givenShell_whenCallNeedsPrivilegedProcess_thenNotPermittedWithoutStartingService() = scope.runTest {
        transport.connect()

        val result = transport.execute(EchoInPrivilegedProcess("hi"))

        assertThat((result as CallResult.Failure).reason).isInstanceOf(CallFailure.NotPermitted::class.java)
        assertThat(connector.binds).isEqualTo(0)
        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SHELL))
    }

    @Test
    fun givenRoot_whenCallNeedsPrivilegedProcess_thenRunsInUserService() = scope.runTest {
        initializer.result = RegisterShizukuResult.Granted(0)
        transport.connect()

        val result = transport.execute(EchoInPrivilegedProcess("hi"))

        assertThat(result).isEqualTo(CallResult.Success("hi for ${system.callerPackage}"))
        assertThat(connector.services.single().calls).isEqualTo(1)
    }

    @Test
    fun givenSystemUid_whenCallNeedsPrivilegedProcess_thenRunsInUserService() = scope.runTest {
        initializer.result = RegisterShizukuResult.Granted(1000)
        assertThat(transport.connect()).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SYSTEM))

        val result = transport.execute(EchoInPrivilegedProcess("hi"))

        assertThat(result).isEqualTo(CallResult.Success("hi for ${system.callerPackage}"))
        assertThat(connector.binds).isEqualTo(1)
    }

    @Test
    fun givenRoot_whenBinderCall_thenRunsInThisProcessWithoutService() = scope.runTest {
        initializer.result = RegisterShizukuResult.Granted(0)
        transport.connect()

        transport.execute(ForceStop(PKG, WORK))

        assertThat(system.activityManager.forceStopped).isNotEmpty()
        assertThat(connector.binds).isEqualTo(0)
    }

    @Test
    fun givenUserServiceNeverAttaches_whenExecute_thenChannelUnavailableAndStillReady() = scope.runTest {
        initializer.result = RegisterShizukuResult.Granted(0)
        transport.connect()
        connector.attaches = false

        val result = transport.execute(EchoInPrivilegedProcess("hi"))

        assertThat(result).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenUserServiceBound_whenShizukuStops_thenNextCallBindsANewOne() = scope.runTest {
        initializer.result = RegisterShizukuResult.Granted(0)
        transport.connect()
        transport.execute(EchoInPrivilegedProcess("hi"))

        shizuku.stop()
        shizuku.restart(asUid = 0)
        val result = transport.execute(EchoInPrivilegedProcess("hi"))

        assertThat(result).isInstanceOf(CallResult.Success::class.java)
        assertThat(connector.binds).isEqualTo(2)
    }

    @Test
    fun whenCreated_thenListenersRegisteredOnce() {
        assertThat(shizuku.deadListeners).hasSize(1)
        assertThat(shizuku.receivedListeners).hasSize(1)
    }

    @Test
    fun givenReady_whenBinderDies_thenNotRunningWithoutACall() = scope.runTest {
        transport.connect()

        shizuku.stop()

        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
    }

    @Test
    fun givenStoppedAfterReady_whenBinderReturnsGranted_thenReadyAgainWithoutPrompt() = scope.runTest {
        transport.connect()
        shizuku.stop()

        shizuku.restart()

        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.SHELL))
        assertThat(shizuku.permissionRequests).isEqualTo(0)
    }

    @Test
    fun givenShizukuRestartsAsRoot_whenBinderReturns_thenIdentityFollowsNewUid() = scope.runTest {
        transport.connect()
        shizuku.stop()

        shizuku.restart(asUid = 0)

        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenReadyAndDeathMissed_whenBinderArrivesUnderNewUid_thenIdentityRefreshed() = scope.runTest {
        transport.connect()

        shizuku.restart(asUid = 0)

        assertThat(transport.state.value).isEqualTo(ChannelState.Ready(PrivilegedIdentity.ROOT))
    }

    @Test
    fun givenPermissionRevoked_whenBinderReturns_thenStaysUnavailableWithoutPrompt() = scope.runTest {
        transport.connect()
        shizuku.stop()
        shizuku.granted = false

        shizuku.restart()

        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.NOT_RUNNING))
        assertThat(shizuku.permissionRequests).isEqualTo(0)
    }

    @Test
    fun givenNeverReady_whenBinderArrives_thenStateUntouched() = scope.runTest {
        initializer.result = RegisterShizukuResult.Denied
        transport.connect()

        shizuku.restart()

        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
        assertThat(shizuku.permissionChecks).isEqualTo(0)
    }

    @Test
    fun givenDeniedByUserAfterReady_whenBinderArrives_thenStaysDenied() = scope.runTest {
        transport.connect()
        initializer.result = RegisterShizukuResult.Denied
        transport.connect()

        shizuku.restart()

        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.DENIED))
    }
}

private class FakeShizukuInitializer : IShizukuInitializer {
    var result: RegisterShizukuResult = RegisterShizukuResult.Granted(2000)

    override suspend fun registerShizuku(): RegisterShizukuResult = result
    override suspend fun awaitBinder() = Unit
    override fun hasPermission(): Boolean = result is RegisterShizukuResult.Granted
    override fun getUid(): Int = (result as? RegisterShizukuResult.Granted)?.uid ?: -1
}

/**
 * The binder state and its events. Registration itself goes through the initializer; [restart]
 * and [stop] act like Shizuku stopping and starting again.
 */
private class FakeShizukuBinder : ShizukuApi {
    var alive = true

    /** The binder dies after the first ping, as when Shizuku stops while a call runs. */
    var dieOnNextPing = false
    var granted = true
    var serverUid = 2000
    var permissionChecks = 0
    var permissionRequests = 0
    val deadListeners = mutableListOf<Shizuku.OnBinderDeadListener>()
    val receivedListeners = mutableListOf<Shizuku.OnBinderReceivedListener>()

    fun stop() {
        alive = false
        deadListeners.forEach { it.onBinderDead() }
    }

    fun restart(asUid: Int = serverUid) {
        serverUid = asUid
        alive = true
        receivedListeners.forEach { it.onBinderReceived() }
    }

    override fun pingBinder(): Boolean {
        val wasAlive = alive
        if (dieOnNextPing) alive = false
        return wasAlive
    }

    override fun checkSelfPermission(): Int {
        permissionChecks++
        return if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
    }

    override fun requestPermission(requestCode: Int) {
        permissionRequests++
    }

    override fun getUid(): Int = serverUid
    override fun isPreV11(): Boolean = false
    override fun shouldShowRequestPermissionRationale(): Boolean = false
    override fun addBinderReceivedListenerSticky(listener: Shizuku.OnBinderReceivedListener) = Unit
    override fun addBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        receivedListeners += listener
    }
    override fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) = Unit
    override fun addRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) = Unit
    override fun removeRequestPermissionResultListener(listener: Shizuku.OnRequestPermissionResultListener) = Unit
    override fun addBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        deadListeners += listener
    }
}
