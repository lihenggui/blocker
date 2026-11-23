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

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.privileged.calls.ListUsers
import com.merxury.blocker.core.system.PlatformPackages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val READY_ROOT = ChannelState.Ready(PrivilegedIdentity.ROOT)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelPlatformPackagesTest {

    private val transport = FakeTransport()
    private val privileged = NamedPackages("privileged")
    private val local = NamedPackages("local")
    private val packages = ChannelPlatformPackages(transport, privileged, local)

    @Test
    fun givenNotCheckedAndQuietAttemptGetsReady_whenRead_thenPrivileged() = runTest {
        transport.quietAcquireResult = READY_ROOT

        assertThat(packages.users().single().name).isEqualTo("privileged")

        assertThat(transport.quietAcquireCount).isEqualTo(1)
        assertThat(transport.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenNotCheckedAndQuietAttemptDenied_whenRead_thenLocalWithoutPrompt() = runTest {
        transport.quietAcquireResult = ChannelState.Unavailable(UnavailableReason.DENIED)

        assertThat(packages.users().single().name).isEqualTo("local")
        assertThat(packages.installedPackages(UserId(0)).single().packageName).isEqualTo("local")

        assertThat(transport.quietAcquireCount).isEqualTo(1)
        assertThat(transport.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenQuietAttemptInFlight_whenRead_thenWaitsAndThenChoosesPrivileged() = runTest {
        val gate = CompletableDeferred<Unit>()
        transport.quietAcquireGate = gate
        transport.quietAcquireResult = READY_ROOT
        val attempt = async { transport.connectIfGranted() }
        val read = async { packages.users() }
        runCurrent()
        assertThat(read.isActive).isTrue()

        gate.complete(Unit)

        assertThat(attempt.await()).isEqualTo(READY_ROOT)
        assertThat(read.await().single().name).isEqualTo("privileged")
        assertThat(transport.quietAcquireCount).isEqualTo(1)
        assertThat(transport.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenQuietAttemptInFlightThenDenied_whenRead_thenWaitsAndThenChoosesLocal() = runTest {
        val gate = CompletableDeferred<Unit>()
        transport.quietAcquireGate = gate
        val attempt = async { transport.connectIfGranted() }
        val read = async { packages.users() }
        runCurrent()
        assertThat(read.isActive).isTrue()

        gate.complete(Unit)
        attempt.await()

        assertThat(read.await().single().name).isEqualTo("local")
        assertThat(transport.quietAcquireCount).isEqualTo(1)
        assertThat(transport.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenQuietAttemptDeniedWithConnectQueued_whenRead_thenLocalWithoutWaitingForPrompt() = runTest {
        val quietGate = CompletableDeferred<Unit>()
        transport.quietAcquireGate = quietGate
        transport.acquireGate = CompletableDeferred()
        val attempt = async { transport.connectIfGranted() }
        val read = async { packages.users() }
        runCurrent()
        val prompt = async { transport.connect() }
        runCurrent()

        quietGate.complete(Unit)
        attempt.await()
        runCurrent()

        assertThat(read.isCompleted).isTrue()
        assertThat(read.await().single().name).isEqualTo("local")
        assertThat(prompt.isActive).isTrue()
        prompt.cancel()
    }

    @Test
    fun givenEachUnavailableReasonAfterCheck_whenRead_thenLocal() = runTest {
        transport.connectIfGranted()
        UnavailableReason.entries.filter { it != UnavailableReason.NOT_CHECKED }.forEach { reason ->
            transport.quietAcquireResult = ChannelState.Unavailable(reason)
            transport.connectIfGranted()

            assertThat(packages.users().single().name).isEqualTo("local")
        }
        assertThat(transport.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenChannelReady_whenRead_thenPrivileged() = runTest {
        transport.connect()

        assertThat(packages.users().single().name).isEqualTo("privileged")
        assertThat(packages.installedPackages(UserId(10)).single().packageName).isEqualTo("privileged")
        assertThat(packages.packageInfo("p", 0, UserId(10)).packageName).isEqualTo("privileged")
        assertThat(packages.applicationEnabledSettings(listOf("p"), UserId(10))).containsExactly(privileged.answer)
        assertThat(packages.componentEnabledSettings("p", listOf("c"), UserId(10))).containsExactly(privileged.answer)
        assertThat(transport.quietAcquireCount).isEqualTo(0)
    }

    @Test
    fun givenChannelBecomesReadyThenLost_whenRead_thenEachRequestRoutedOnItsOwn() = runTest {
        assertThat(packages.users().single().name).isEqualTo("local")

        transport.quietAcquireResult = READY_ROOT
        transport.connectIfGranted()
        assertThat(packages.users().single().name).isEqualTo("privileged")

        transport.throwOnSend = ChannelLostException(UnavailableReason.NOT_RUNNING, "gone")
        transport.executeIfReady(ListUsers)
        assertThat(packages.users().single().name).isEqualTo("local")
        assertThat(transport.quietAcquireCount).isEqualTo(2)
    }

    @Test
    fun givenChannelReady_whenLabelOrArchive_thenStillLocal() = runTest {
        transport.connect()

        assertThat(packages.label(ApplicationInfo())).isEqualTo("local")
        assertThat(packages.archiveInfo("/data/app/base.apk", 0)?.packageName).isEqualTo("local")
    }
}

/** Answers every request with its [name], so a test can tell which one was asked. */
private class NamedPackages(private val name: String) : PlatformPackages {
    val answer = name.length

    private fun info() = PackageInfo().apply { packageName = name }

    override suspend fun users() = listOf(DeviceUser(UserId(0), name, UserKind.OWNER, parent = null, running = true))
    override suspend fun installedPackages(user: UserId) = listOf(info())
    override suspend fun packageInfo(packageName: String, flags: Int, user: UserId) = info()
    override fun archiveInfo(apkPath: String, flags: Int) = info()
    override suspend fun applicationEnabledSettings(packageNames: List<String>, user: UserId) = packageNames.map { answer }
    override suspend fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId) = classNames.map { answer }
    override fun label(application: ApplicationInfo) = name
}
