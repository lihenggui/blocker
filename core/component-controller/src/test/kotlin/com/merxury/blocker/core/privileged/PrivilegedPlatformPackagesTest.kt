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
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.privileged.calls.ApplicationEnabledSettings
import com.merxury.blocker.core.privileged.calls.ComponentEnabledSettings
import com.merxury.blocker.core.system.PlatformPackages
import com.merxury.blocker.core.system.UserNotReachableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

private const val PKG = "com.example"
private val WORK = UserId(10)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivilegedPlatformPackagesTest {

    private val transport = FakeTransport()
    private val system = transport.system
    private val local = LocalOnlyPackages()
    private val packages = PrivilegedPlatformPackages(transport, local)

    private fun connect() = runBlocking { transport.connect() }

    @Test
    fun givenChannelReady_whenRead_thenAnotherUsersAppsReadThroughChannel() = runTest {
        connect()
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work", flags = 0x20, parent = 0)
        system.packageManager.install(10, PKG)
        system.packageManager.appSettings[PKG to 10] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        assertThat(packages.users().map { it.id }).containsExactly(UserId(0), WORK).inOrder()
        assertThat(packages.installedPackages(WORK).map { it.packageName }).containsExactly(PKG)
        assertThat(packages.packageInfo(PKG, 0, WORK).packageName).isEqualTo(PKG)
        assertThat(packages.applicationEnabledSettings(listOf(PKG), WORK))
            .containsExactly(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        assertThat(packages.componentEnabledSettings(PKG, listOf("$PKG.Main"), WORK))
            .containsExactly(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
    }

    @Test
    fun givenChannelNotReady_whenRead_thenNeverConnectsAndFailsAsUnavailable() = runTest {
        assertFailsWith<IllegalStateException> { packages.users() }
        assertFailsWith<IllegalStateException> { packages.installedPackages(WORK) }

        assertThat(transport.acquireCount).isEqualTo(0)
    }

    @Test
    fun givenPackageMissingForUser_whenReadPackage_thenNameNotFound() = runTest {
        connect()

        assertFailsWith<NameNotFoundException> { packages.packageInfo(PKG, 0, WORK) }
        assertFailsWith<NameNotFoundException> { packages.componentEnabledSettings(PKG, emptyList(), WORK) }
    }

    @Test
    fun givenUserMissing_whenListPackages_thenUserNotReachable() = runTest {
        connect()

        val thrown = assertFailsWith<UserNotReachableException> { packages.installedPackages(UserId(42)) }

        assertThat(thrown.user).isEqualTo(UserId(42))
    }

    @Test
    fun givenIdentityRefused_whenRead_thenUserNotReachable() = runTest {
        connect()
        system.packageManager.failure = SecurityException("Requires INTERACT_ACROSS_USERS_FULL")

        assertFailsWith<UserNotReachableException> { packages.packageInfo(PKG, 0, WORK) }
        assertFailsWith<UserNotReachableException> {
            packages.componentEnabledSettings(PKG, listOf("$PKG.Main"), WORK)
        }
    }

    @Test
    fun givenSystemError_whenRead_thenRuntimeExceptionForTheReader() = runTest {
        connect()
        system.packageManager.failure = IllegalStateException("odd")

        assertFailsWith<IllegalStateException> { packages.packageInfo(PKG, 0, WORK) }
    }

    @Test
    fun givenChannelDiesMidRead_whenRead_thenRuntimeExceptionAndChannelMarkedLost() = runTest {
        connect()
        transport.throwOnSend = ChannelLostException(UnavailableReason.FAILED, "gone")

        assertFailsWith<IllegalStateException> { packages.packageInfo(PKG, 0, WORK) }
        assertThat(transport.state.value).isEqualTo(ChannelState.Unavailable(UnavailableReason.FAILED))
    }

    @Test
    fun givenThousandsOfComponents_whenReadSettings_thenSplitOverCallsInOrder() = runTest {
        connect()
        system.packageManager.install(10, PKG)
        val names = (1..1_200).map { "$PKG.C$it" }
        system.packageManager.componentSettings[android.content.ComponentName(PKG, names.last()) to 10] =
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val settings = packages.componentEnabledSettings(PKG, names, WORK)

        assertThat(settings).hasSize(1_200)
        assertThat(settings.last()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        val calls = transport.sent.filterIsInstance<ComponentEnabledSettings>()
        assertThat(calls.map { it.classNames.size }).containsExactly(500, 500, 200).inOrder()
    }

    @Test
    fun givenAppMissingForUser_whenReadAppSettings_thenNullForThatAppOnly() = runTest {
        connect()
        system.packageManager.install(10, PKG)
        system.packageManager.appSettings[PKG to 10] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER

        assertThat(packages.applicationEnabledSettings(listOf("com.missing", PKG), WORK))
            .containsExactly(null, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
            .inOrder()
    }

    @Test
    fun givenHundredsOfApps_whenReadAppSettings_thenSplitOverCallsInOrder() = runTest {
        connect()
        val names = (1..1_100).map { "com.example$it" }
        names.forEach { system.packageManager.install(10, it) }
        system.packageManager.appSettings[names.last() to 10] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        val settings = packages.applicationEnabledSettings(names, WORK)

        assertThat(settings).hasSize(1_100)
        assertThat(settings.last()).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        val calls = transport.sent.filterIsInstance<ApplicationEnabledSettings>()
        assertThat(calls.map { it.packageNames.size }).containsExactly(500, 500, 100).inOrder()
    }

    @Test
    fun givenNoApps_whenReadAppSettings_thenNoCall() = runTest {
        connect()

        assertThat(packages.applicationEnabledSettings(emptyList(), WORK)).isEmpty()
        assertThat(transport.sent).isEmpty()
    }

    @Test
    fun givenReadInFlight_whenCancelled_thenCallInFlightCancelled() = runTest {
        val executor = HangingExecutor()
        val hanging = PrivilegedPlatformPackages(executor, local)

        val read = launch { hanging.packageInfo(PKG, 0, WORK) }
        runCurrent()
        read.cancelAndJoin()

        assertThat(executor.started).isEqualTo(1)
        assertThat(executor.cancelled).isEqualTo(1)
    }

    @Test
    fun whenLabelOrArchive_thenReadLocally() = runTest {
        val application = ApplicationInfo().apply { packageName = PKG }

        assertThat(packages.label(application)).isEqualTo("local label")
        assertThat(packages.archiveInfo("/data/app/base.apk", 0)?.packageName).isEqualTo("local archive")
        assertThat(transport.sent).isEmpty()
    }
}

/** Answers only what must run in this process; anything else would be a routing mistake. */
private class LocalOnlyPackages : PlatformPackages {
    override fun label(application: ApplicationInfo): String = "local label"
    override fun archiveInfo(apkPath: String, flags: Int): PackageInfo = PackageInfo().apply { packageName = "local archive" }
    override suspend fun users(): List<DeviceUser> = error("Read through the channel")
    override suspend fun installedPackages(user: UserId): List<PackageInfo> = error("Read through the channel")
    override suspend fun packageInfo(packageName: String, flags: Int, user: UserId): PackageInfo = error("Read through the channel")
    override suspend fun applicationEnabledSettings(packageNames: List<String>, user: UserId): List<Int?> = error("Read through the channel")
    override suspend fun componentEnabledSettings(packageName: String, classNames: List<String>, user: UserId): List<Int?> = error("Read through the channel")
}

/** Waits in every call until it is cancelled, counting both. */
private class HangingExecutor : PrivilegedExecutor {
    var started = 0
    var cancelled = 0

    override suspend fun <R> execute(call: PrivilegedCall<R>): CallResult<R> = executeIfReady(call)

    override suspend fun <R> executeIfReady(call: PrivilegedCall<R>): CallResult<R> {
        started++
        try {
            awaitCancellation()
        } catch (e: CancellationException) {
            cancelled++
            throw e
        }
    }
}
