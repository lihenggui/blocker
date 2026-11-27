// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.device

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.appcontrol.BackgroundUsageControl
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.BackgroundUsage
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackgroundUsageRepositoryTest {
    private val app = AppKey("com.example.background", UserId(10))
    private val channel = FakePrivilegedChannel(ChannelState.Ready(PrivilegedIdentity.SHELL))
    private val control = FakeBackgroundUsageControl()
    private val repository = LocalBackgroundUsageRepository(channel, control)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenTwoObservers_whenUsageIsWrittenOrRead_thenBothReceiveTheLatestReadBack() = runTest {
        val allowed = BackgroundUsage(allowed = true, changeable = true)
        val restricted = allowed.copy(allowed = false)

        repository.observe(app).test firstObserver@{
            assertThat(awaitItem()).isEqualTo(allowed)
            repository.observe(app).test {
                assertThat(awaitItem()).isEqualTo(allowed)
                runCurrent()
                assertThat(channel.quietConnectCount).isEqualTo(2)
                assertThat(channel.connectCount).isEqualTo(0)

                control.readResult = CallResult.Success(restricted)
                assertThat(repository.setAllowed(app, false)).isEqualTo(BackgroundUsageResult.Success(restricted))
                assertThat(awaitItem()).isEqualTo(restricted)
                assertThat(this@firstObserver.awaitItem()).isEqualTo(restricted)

                control.readResult = CallResult.Success(allowed)
                assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Success(allowed))
                assertThat(awaitItem()).isEqualTo(allowed)
                assertThat(this@firstObserver.awaitItem()).isEqualTo(allowed)
                assertThat(channel.connectCount).isEqualTo(1)
                assertThat(channel.quietConnectCount).isEqualTo(3)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenUncachedRestrictedApp_whenObserving_thenWaitsForTheFirstRead() = runTest {
        val restricted = BackgroundUsage(allowed = false, changeable = true)
        control.readResult = CallResult.Success(restricted)
        val firstRead = CompletableDeferred<Unit>()
        control.beforeRead = { firstRead.await() }

        repository.observe(app).test {
            runCurrent()
            assertThat(control.calls).containsExactly("read $app")
            expectNoEvents()

            firstRead.complete(Unit)
            assertThat(awaitItem()).isEqualTo(restricted)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenExternalSettingChange_whenResubscribing_thenEmitsTheCurrentUsage() = runTest {
        val allowed = BackgroundUsage(allowed = true, changeable = true)
        val restricted = allowed.copy(allowed = false)
        assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Success(allowed))
        control.readResult = CallResult.Success(restricted)
        val refresh = CompletableDeferred<Unit>()
        control.beforeRead = { refresh.await() }

        repository.observe(app).test {
            assertThat(awaitItem()).isEqualTo(allowed)
            runCurrent()
            assertThat(control.calls).containsExactly("read $app", "read $app")
            refresh.complete(Unit)
            assertThat(awaitItem()).isEqualTo(restricted)
        }
        assertThat(control.calls).containsExactly("read $app", "read $app")
        assertThat(channel.connectCount).isEqualTo(0)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenUnavailableChannel_whenItBecomesReadyDuringObservation_thenEmitsTheCurrentUsage() = runTest {
        channel.setState(ChannelState.Unavailable(UnavailableReason.DENIED))

        repository.observe(app).test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            assertThat(control.calls).isEmpty()

            channel.setState(ChannelState.Ready(PrivilegedIdentity.SHELL))

            assertThat(awaitItem()).isEqualTo(BackgroundUsage(allowed = true, changeable = true))
            assertThat(control.calls).containsExactly("read $app")
            assertThat(channel.connectCount).isEqualTo(0)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenUncachedFailedRead_whenObserving_thenEmitsNullWithoutPrompting() = runTest {
        control.readResult = CallResult.Failure(CallFailure.SystemError("app ops unavailable"))

        repository.observe(app).test {
            assertThat(awaitItem()).isNull()
            runCurrent()
            expectNoEvents()
        }
        assertThat(channel.quietConnectCount).isEqualTo(1)
        assertThat(channel.connectCount).isEqualTo(0)
        assertThat(control.calls).containsExactly("read $app")
    }

    @Test
    fun givenCachedRestriction_whenAccessOrAvailabilityIsLost_thenAQuietReadClearsTheObserver() = runTest {
        val ready = ChannelState.Ready(PrivilegedIdentity.SHELL)
        val denied = ChannelState.Unavailable(UnavailableReason.DENIED)
        val restricted = BackgroundUsage(allowed = false, changeable = true)
        control.readResult = CallResult.Success(restricted)

        repository.observe(app).test {
            assertThat(awaitItem()).isEqualTo(restricted)

            channel.setState(denied)
            assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.NO_ACCESS))
            assertThat(awaitItem()).isNull()

            channel.setState(ready)
            repository.read(app)
            assertThat(awaitItem()).isEqualTo(restricted)
            control.readResult = CallResult.Failure(CallFailure.TargetNotFound(null))
            assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_INSTALLED))
            assertThat(awaitItem()).isNull()

            control.readResult = CallResult.Success(restricted)
            repository.read(app)
            assertThat(awaitItem()).isEqualTo(restricted)
            control.isSupported = false
            assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.UNSUPPORTED))
            assertThat(awaitItem()).isNull()
            assertThat(channel.connectCount).isEqualTo(0)
        }
    }

    @Test
    fun givenShellAccess_whenRead_thenUseOnlyQuietConnection() = runTest {
        assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Success(BackgroundUsage(true, true)))
        assertThat(channel.quietConnectCount).isEqualTo(1)
        assertThat(channel.connectCount).isEqualTo(0)
        assertThat(control.calls).containsExactly("read $app")
    }

    @Test
    fun givenAccessDenied_whenReadAndWrite_thenReportNoAccessWithoutCallingSystem() = runTest {
        val denied = ChannelState.Unavailable(UnavailableReason.DENIED)
        channel.setState(denied)
        channel.connectResult = denied

        assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.NO_ACCESS))
        assertThat(repository.setAllowed(app, false)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.NO_ACCESS))
        assertThat(channel.quietConnectCount).isEqualTo(1)
        assertThat(channel.connectCount).isEqualTo(1)
        assertThat(control.calls).isEmpty()
    }

    @Test
    fun givenUnsupportedRelease_whenReadAndWrite_thenReportUnsupportedBeforeConnecting() = runTest {
        control.isSupported = false

        assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.UNSUPPORTED))
        assertThat(repository.setAllowed(app, false)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.UNSUPPORTED))
        assertThat(channel.quietConnectCount).isEqualTo(0)
        assertThat(channel.connectCount).isEqualTo(0)
        assertThat(control.calls).isEmpty()
    }

    @Test
    fun givenSystemAllowlistEntry_whenReadAndWrite_thenShowProtectedAndReportRefusal() = runTest {
        val protected = BackgroundUsage(allowed = true, changeable = false)
        control.readResult = CallResult.Success(protected)
        control.writeResult = CallResult.Success(false)

        assertThat(repository.read(app)).isEqualTo(BackgroundUsageResult.Success(protected))
        assertThat(repository.setAllowed(app, false)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_CHANGEABLE))
        assertThat(control.calls).containsExactly("read $app", "set $app false").inOrder()
    }

    @Test
    fun givenSystemStillReportsAllowed_whenRestrict_thenReturnReadBackRatherThanRequestedValue() = runTest {
        assertThat(repository.setAllowed(app, false)).isEqualTo(BackgroundUsageResult.Success(BackgroundUsage(true, true)))

        assertThat(channel.connectCount).isEqualTo(1)
        assertThat(channel.quietConnectCount).isEqualTo(0)
        assertThat(control.calls).containsExactly("set $app false", "read $app").inOrder()
    }

    @Test
    fun givenSystemReportsRestricted_whenWriteSucceeds_thenReturnRestrictedReadBack() = runTest {
        val restricted = BackgroundUsage(allowed = false, changeable = true)
        control.readResult = CallResult.Success(restricted)

        assertThat(repository.setAllowed(app, false)).isEqualTo(BackgroundUsageResult.Success(restricted))
        assertThat(control.calls).containsExactly("set $app false", "read $app").inOrder()
    }

    @Test
    fun givenPackageDisappearsAfterWrite_whenReadBack_thenReportNotInstalled() = runTest {
        control.readResult = CallResult.Failure(CallFailure.TargetNotFound(null))

        assertThat(repository.setAllowed(app, true)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.NOT_INSTALLED))
        assertThat(control.calls).containsExactly("set $app true", "read $app").inOrder()
    }

    @Test
    fun givenSystemWriteError_whenSetAllowed_thenReportErrorWithoutReadingBack() = runTest {
        control.writeResult = CallResult.Failure(CallFailure.SystemError("device idle unavailable"))

        assertThat(repository.setAllowed(app, true)).isEqualTo(BackgroundUsageResult.Failure(BackgroundUsageFailure.SYSTEM_ERROR))
        assertThat(control.calls).containsExactly("set $app true")
    }

    @Test
    fun givenCancelledReadBack_whenSetAllowed_thenPropagateCancellation() = runTest {
        val cancelled = CancellationException("read cancelled")
        control.readCancellation = cancelled

        val result = try {
            repository.setAllowed(app, true)
        } catch (e: CancellationException) {
            e
        }

        assertThat(result).isSameInstanceAs(cancelled)
    }
}

private class FakeBackgroundUsageControl : BackgroundUsageControl {
    override var isSupported = true
    var readResult: CallResult<BackgroundUsage> = CallResult.Success(BackgroundUsage(allowed = true, changeable = true))
    var writeResult: CallResult<Boolean> = CallResult.Success(true)
    var readCancellation: CancellationException? = null
    var beforeRead: suspend () -> Unit = {}
    val calls = mutableListOf<String>()

    override suspend fun read(app: AppKey): CallResult<BackgroundUsage> {
        calls += "read $app"
        beforeRead()
        readCancellation?.let { throw it }
        return readResult
    }

    override suspend fun setAllowed(app: AppKey, allowed: Boolean): CallResult<Boolean> {
        calls += "set $app $allowed"
        return writeResult
    }
}
