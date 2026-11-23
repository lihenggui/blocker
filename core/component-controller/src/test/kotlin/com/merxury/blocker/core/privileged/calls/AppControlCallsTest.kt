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

package com.merxury.blocker.core.privileged.calls

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager.NameNotFoundException
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.privileged.FakeSystemServices
import com.merxury.blocker.core.privileged.toCallFailure
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeoutException

private const val PKG = "com.example"
private val WORK = UserId(10)
private const val SHORT_WAIT = 50L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppControlCallsTest {

    private val system = FakeSystemServices()
    private val pm = system.packageManager
    private val am = system.activityManager
    private val installer = system.packageInstaller

    @Before
    fun installInWorkProfile() {
        pm.install(WORK.value, PKG)
    }

    @Test
    fun givenSystemReportsSuccess_whenClearData_thenThatUsersDataClearedWithKeepStateOverload() {
        ClearAppData(PKG, WORK).run(system)

        assertThat(am.dataCleared).containsExactly("4 $PKG 10")
    }

    @Test
    @Config(sdk = [27])
    fun givenAndroid8_1_whenClearData_thenThreeArgumentOverloadUsed() {
        ClearAppData(PKG, WORK).run(system)

        assertThat(am.dataCleared).containsExactly("3 $PKG 10")
    }

    @Test
    fun givenSystemReportsFailure_whenClearData_thenSystemError() {
        am.clearDataReply = false

        val thrown = assertThrows(IllegalStateException::class.java) { ClearAppData(PKG, WORK).run(system) }

        assertThat(thrown.toCallFailure()).isInstanceOf(CallFailure.SystemError::class.java)
    }

    @Test
    fun givenSystemNeverAnswers_whenClearData_thenTimesOutAsSystemError() {
        am.clearDataReply = null

        val thrown = assertThrows(TimeoutException::class.java) { ClearAppData(PKG, WORK, timeoutMillis = SHORT_WAIT).run(system) }

        val failure = thrown.toCallFailure() as CallFailure.SystemError
        assertThat(failure.message).contains("timed out")
    }

    @Test
    fun givenSystemDoesNotAcceptPackage_whenClearData_thenNameNotFound() {
        am.clearDataAccepted = false

        assertThrows(NameNotFoundException::class.java) { ClearAppData(PKG, WORK).run(system) }
    }

    @Test
    fun givenAppOnlyInOtherUser_whenClearDataOrCache_thenNameNotFoundAndSystemNotAsked() {
        assertThrows(NameNotFoundException::class.java) { ClearAppData(PKG, UserId.SYSTEM).run(system) }
        assertThrows(NameNotFoundException::class.java) { ClearAppCache(PKG, UserId.SYSTEM).run(system) }

        assertThat(am.dataCleared).isEmpty()
        assertThat(pm.cacheCleared).isEmpty()
    }

    @Test
    fun givenSystemReportsSuccess_whenClearCache_thenThatUsersCacheCleared() {
        ClearAppCache(PKG, WORK).run(system)

        assertThat(pm.cacheCleared).containsExactly(PKG to 10)
    }

    @Test
    fun givenRequestIgnored_whenClearCache_thenTimesOut() {
        pm.cacheReply = null

        assertThrows(TimeoutException::class.java) { ClearAppCache(PKG, WORK, timeoutMillis = SHORT_WAIT).run(system) }
    }

    @Test
    fun givenUserApp_whenUninstall_thenRemovedForThatUserOnly() {
        UninstallApp(PKG, WORK).run(system)

        assertThat(installer.uninstalls).containsExactly("VersionedPackage $PKG 0 10")
    }

    @Test
    fun givenSystemApp_whenUninstall_thenDeleteSystemAppFlagForThatUser() {
        pm.installed[WORK.value] = mutableListOf(systemApp())

        UninstallApp(PKG, WORK).run(system)

        assertThat(installer.uninstalls).containsExactly("VersionedPackage $PKG 4 10")
    }

    @Test
    @Config(sdk = [25])
    fun givenAndroid7_1_whenUninstall_thenPackageNameOverloadAndOldSendAnswers() {
        UninstallApp(PKG, WORK).run(system)

        assertThat(installer.uninstalls).containsExactly("String $PKG 0 10")
    }

    @Test
    @Config(sdk = [26])
    fun givenAndroid8_whenUninstall_thenVersionedPackageOverload() {
        UninstallApp(PKG, WORK).run(system)

        assertThat(installer.uninstalls).containsExactly("VersionedPackage $PKG 0 10")
    }

    @Test
    fun givenBlockedOrNeedsConfirmation_whenUninstall_thenNotPermitted() {
        installer.status = PackageInstaller.STATUS_FAILURE_BLOCKED
        installer.statusMessage = "DELETE_FAILED_DEVICE_POLICY_MANAGER"
        val blocked = assertThrows(SecurityException::class.java) { UninstallApp(PKG, WORK).run(system) }
        assertThat(blocked.message).contains("DELETE_FAILED_DEVICE_POLICY_MANAGER")

        installer.status = PackageInstaller.STATUS_PENDING_USER_ACTION
        assertThrows(SecurityException::class.java) { UninstallApp(PKG, WORK).run(system) }
    }

    @Test
    fun givenOtherFailureOrNoAnswer_whenUninstall_thenSystemErrorOrTimeout() {
        installer.status = PackageInstaller.STATUS_FAILURE
        installer.statusMessage = "DELETE_FAILED_INTERNAL_ERROR"
        val failed = assertThrows(IllegalStateException::class.java) { UninstallApp(PKG, WORK).run(system) }
        assertThat(failed.message).contains("DELETE_FAILED_INTERNAL_ERROR")

        installer.status = null
        assertThrows(TimeoutException::class.java) { UninstallApp(PKG, WORK, timeoutMillis = SHORT_WAIT).run(system) }
    }

    @Test
    fun givenAppNotInstalledForUser_whenUninstall_thenNameNotFoundAndSystemNotAsked() {
        assertThrows(NameNotFoundException::class.java) { UninstallApp(PKG, UserId.SYSTEM).run(system) }

        assertThat(installer.uninstalls).isEmpty()
    }

    private fun systemApp() = PackageInfo().apply {
        packageName = PKG
        applicationInfo = ApplicationInfo().apply { flags = ApplicationInfo.FLAG_SYSTEM }
    }
}
