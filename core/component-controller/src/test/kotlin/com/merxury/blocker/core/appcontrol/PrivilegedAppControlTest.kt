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

import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.privileged.FakeTransport
import com.merxury.blocker.core.privileged.calls.ClearAppCache
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PKG = "com.example"
private val WORK = UserId(10)
private val APP = AppKey(PKG, WORK)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivilegedAppControlTest {

    private val transport = FakeTransport()
    private val system = transport.system
    private val control = PrivilegedAppControl(transport, transport)
    private val services = PrivilegedServiceControl(transport, transport)

    init {
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work")
        system.packageManager.install(WORK.value, PKG)
        system.packageManager.install(UserId.SYSTEM.value, PKG)
    }

    @Test
    fun whenDisable_thenDisabledUserForThatUserOnlyAndReadBack() = runTest {
        val result = control.setEnabled(APP, enabled = false)

        assertThat(result).isEqualTo(CallResult.Success(AppEnabledSetting.DISABLED_USER))
        assertThat(system.packageManager.appSettings).containsExactly(PKG to 10, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
    }

    @Test
    fun whenEnable_thenEnabledReadBack() = runTest {
        assertThat(control.setEnabled(APP, enabled = true)).isEqualTo(CallResult.Success(AppEnabledSetting.ENABLED))
    }

    @Test
    fun givenSettingDoesNotStick_whenDisable_thenSystemError() = runTest {
        system.packageManager.ignoreAppSettingChanges = true

        val result = control.setEnabled(APP, enabled = false)

        assertThat((result as CallResult.Failure).reason).isInstanceOf(CallFailure.SystemError::class.java)
    }

    @Test
    fun whenForceStop_thenStoppedForThatUser() = runTest {
        assertThat(control.forceStop(APP)).isEqualTo(CallResult.Success(Unit))
        assertThat(system.activityManager.forceStopped).containsExactly(PKG to 10)
    }

    @Test
    fun givenRoot_whenClearCacheAndData_thenBothReachTheSystem() = runTest {
        assertThat(control.clearCache(APP)).isEqualTo(CallResult.Success(Unit))
        assertThat(control.clearData(APP)).isEqualTo(CallResult.Success(Unit))

        assertThat(system.packageManager.cacheCleared).containsExactly(PKG to 10)
        assertThat(system.activityManager.dataCleared).containsExactly("4 $PKG 10")
    }

    @Test
    fun givenShell_whenClearCache_thenNotPermittedWithoutAskingTheSystem() = runTest {
        transport.acquireResult = ChannelState.Ready(PrivilegedIdentity.SHELL)

        val result = control.clearCache(APP)

        assertThat((result as CallResult.Failure).reason).isInstanceOf(CallFailure.NotPermitted::class.java)
        assertThat(transport.sent.filterIsInstance<ClearAppCache>()).isEmpty()
    }

    @Test
    fun givenSystemUid_whenClearCacheAndListOtherUser_thenTheSystemIsAsked() = runTest {
        transport.acquireResult = ChannelState.Ready(PrivilegedIdentity.SYSTEM)

        assertThat(control.clearCache(APP)).isEqualTo(CallResult.Success(Unit))
        assertThat(control.runningPackages(WORK)).isEqualTo(CallResult.Success(emptySet<String>()))
        assertThat(services.runningServices(APP)).isEqualTo(CallResult.Success(emptySet<ComponentId>()))
        assertThat(system.packageManager.cacheCleared).containsExactly(PKG to 10)
    }

    @Test
    fun givenNoChannel_whenClearCache_thenChannelUnavailable() = runTest {
        transport.acquireResult = ChannelState.Unavailable(UnavailableReason.DENIED)

        assertThat(control.clearCache(APP)).isEqualTo(CallResult.Failure(CallFailure.ChannelUnavailable))
    }

    @Test
    fun givenShell_whenClearDataAndUninstall_thenTheSystemDecides() = runTest {
        transport.acquireResult = ChannelState.Ready(PrivilegedIdentity.SHELL)

        assertThat(control.clearData(APP)).isEqualTo(CallResult.Success(Unit))
        assertThat(control.uninstall(APP)).isEqualTo(CallResult.Success(Unit))
        assertThat(system.packageInstaller.uninstalls).containsExactly("VersionedPackage $PKG 0 10")
    }

    @Test
    fun givenRoot_whenListRunningInOtherUser_thenNotPermittedWithoutAskingTheSystem() = runTest {
        assertThat((control.runningPackages(WORK) as CallResult.Failure).reason).isInstanceOf(CallFailure.NotPermitted::class.java)
        assertThat((services.runningServices(APP) as CallResult.Failure).reason).isInstanceOf(CallFailure.NotPermitted::class.java)
        assertThat(transport.sent).isEmpty()
    }

    @Test
    fun givenRoot_whenRunningPackagesOfOwner_thenListed() = runTest {
        system.activityManager.processes += ActivityManager.RunningAppProcessInfo().apply {
            uid = 10_123
            pkgList = arrayOf(PKG)
        }

        assertThat(control.runningPackages(UserId.SYSTEM)).isEqualTo(CallResult.Success(setOf(PKG)))
    }

    @Test
    fun givenShell_whenRunningPackages_thenSetOfThatUser() = runTest {
        transport.acquireResult = ChannelState.Ready(PrivilegedIdentity.SHELL)
        system.activityManager.processes += ActivityManager.RunningAppProcessInfo().apply {
            uid = 1_010_123
            pkgList = arrayOf(PKG, PKG)
        }

        assertThat(control.runningPackages(WORK)).isEqualTo(CallResult.Success(setOf(PKG)))
        assertThat(control.runningPackages(UserId.SYSTEM)).isEqualTo(CallResult.Success(emptySet<String>()))
    }

    @Test
    fun givenShell_whenServiceStartedListedAndStopped_thenEachForTheKeysUser() = runTest {
        transport.acquireResult = ChannelState.Ready(PrivilegedIdentity.SHELL)
        val service = ComponentId(PKG, "$PKG.SyncService")
        system.packageManager.installed[WORK.value] = mutableListOf(
            PackageInfo().apply {
                packageName = PKG
                services = arrayOf(ServiceInfo().apply { name = service.className })
            },
        )
        system.activityManager.services += ActivityManager.RunningServiceInfo().apply {
            uid = 1_010_123
            started = true
            this.service = ComponentName(PKG, service.className)
        }

        assertThat(services.start(ComponentKey(service, WORK))).isEqualTo(CallResult.Success(Unit))
        assertThat(services.runningServices(APP)).isEqualTo(CallResult.Success(setOf(service)))
        assertThat(services.stop(ComponentKey(service, WORK))).isEqualTo(CallResult.Success(Unit))
        assertThat(system.activityManager.serviceCalls).containsExactly("start7 $PKG/.SyncService 10", "stop $PKG/.SyncService 10").inOrder()
    }
}
