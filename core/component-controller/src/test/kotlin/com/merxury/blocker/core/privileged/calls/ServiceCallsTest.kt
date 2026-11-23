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

import android.app.ActivityManager
import android.content.ComponentName
import android.content.pm.PackageInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ServiceInfo
import android.os.Parcel
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

private const val PKG = "com.example"
private const val SERVICE = "$PKG.SyncService"
private val COMPONENT = ComponentName(PKG, SERVICE)
private val WORK = UserId(10)

// A uid of user 10: the user times 100000 plus the app id.
private const val WORK_UID = 1_010_123
private const val OWNER_UID = 10_123

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServiceCallsTest {

    private val system = FakeSystemServices()
    private val am = system.activityManager

    @Before
    fun setUp() {
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work")
        system.packageManager.installed[WORK.value] = mutableListOf(
            PackageInfo().apply {
                packageName = PKG
                services = arrayOf(ServiceInfo().apply { name = SERVICE })
            },
        )
    }

    @Test
    fun givenProcessesInTwoUsers_whenRunningPackages_thenOnlyThatUsersPackagesOnce() {
        am.processes += process(WORK_UID, PKG, "$PKG.shared")
        am.processes += process(WORK_UID + 1, PKG)
        am.processes += process(OWNER_UID, "com.owner")

        assertThat(RunningPackages(WORK).run(system)).containsExactly(PKG, "$PKG.shared")
        assertThat(RunningPackages(UserId.SYSTEM).run(system)).containsExactly("com.owner")
    }

    @Test
    fun givenUserThatDoesNotExist_whenRunningPackagesOrServices_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) { RunningPackages(UserId(42)).run(system) }
        assertThrows(IllegalArgumentException::class.java) { RunningServices(PKG, UserId(42)).run(system) }
    }

    @Test
    fun givenServicesOfSeveralUsersAndApps_whenRunningServices_thenStartedOnesOfThatAppAndUser() {
        am.services += service(WORK_UID, SERVICE, started = true)
        am.services += service(WORK_UID, "$PKG.BoundOnly", started = false)
        am.services += service(OWNER_UID, "$PKG.OwnerService", started = true)
        am.services += service(WORK_UID, "com.other.Service", started = true, packageName = "com.other")

        val services = RunningServices(PKG, WORK).run(system)

        assertThat(services).isEqualTo(RunningServices.Services(listOf(SERVICE), capped = false))
        assertThat(am.maxServicesAsked).isEqualTo(RunningServices.MAX_SERVICES)
    }

    @Test
    fun givenAsManyServicesAsTheCap_whenRunningServices_thenCappedAndSurvivesParcel() {
        repeat(RunningServices.MAX_SERVICES) { am.services += service(OWNER_UID, "$PKG.S$it", started = true) }
        val call = RunningServices(PKG, WORK)

        val services = call.run(system)
        val parcel = Parcel.obtain()
        call.writeResult(services, parcel)
        parcel.setDataPosition(0)

        assertThat(services.capped).isTrue()
        assertThat(call.readResult(parcel)).isEqualTo(services)
        parcel.recycle()
    }

    @Test
    fun givenAndroid15_whenStartService_thenSevenArgumentOverloadForThatUser() {
        StartService(COMPONENT, WORK).run(system)

        assertThat(am.serviceCalls).containsExactly("start7 $PKG/.SyncService 10")
    }

    @Test
    @Config(sdk = [28])
    fun givenAndroid9_whenStartService_thenSixArgumentOverload() {
        StartService(COMPONENT, WORK).run(system)

        assertThat(am.serviceCalls).containsExactly("start6 $PKG/.SyncService 10")
    }

    @Test
    @Config(sdk = [24])
    fun givenAndroid7_whenStartService_thenFiveArgumentOverload() {
        StartService(COMPONENT, WORK).run(system)

        assertThat(am.serviceCalls).containsExactly("start5 $PKG/.SyncService 10")
    }

    @Test
    fun givenCodedRefusals_whenStartService_thenNotPermittedOrSystemErrorWithReason() {
        assertThat(startFailure(ComponentName("!", "not exported from uid 1010123")))
            .isEqualTo(CallFailure.NotPermitted("Not allowed to start $PKG/.SyncService: requires not exported from uid 1010123"))
        assertThat(startFailure(ComponentName("?", "app is in background uid null")))
            .isEqualTo(CallFailure.NotPermitted("Not allowed to start $PKG/.SyncService: app is in background uid null"))

        val error = startFailure(ComponentName("!!", "foreground not allowed as per app op")) as CallFailure.SystemError
        assertThat(error.message).contains("foreground not allowed as per app op")
    }

    @Test
    fun givenNoAnswerForDeclaredService_whenStartService_thenSystemError() {
        am.startAnswersNull = true

        val thrown = assertThrows(IllegalStateException::class.java) { StartService(COMPONENT, WORK).run(system) }

        assertThat(thrown.toCallFailure()).isInstanceOf(CallFailure.SystemError::class.java)
    }

    @Test
    fun givenServiceNotDeclaredOrAppNotInUser_whenStartOrStop_thenNameNotFoundAndSystemNotAsked() {
        val undeclared = ComponentName(PKG, "$PKG.Missing")
        assertThrows(NameNotFoundException::class.java) { StartService(undeclared, WORK).run(system) }
        assertThrows(NameNotFoundException::class.java) { StopService(undeclared, WORK).run(system) }
        assertThrows(NameNotFoundException::class.java) { StartService(COMPONENT, UserId.SYSTEM).run(system) }

        assertThat(am.serviceCalls).isEmpty()
    }

    @Test
    fun givenStoppedOrNotRunning_whenStopService_thenSucceeds() {
        StopService(COMPONENT, WORK).run(system)
        am.stopAnswer = 0
        StopService(COMPONENT, WORK).run(system)

        assertThat(am.serviceCalls).containsExactly("stop $PKG/.SyncService 10", "stop $PKG/.SyncService 10")
    }

    @Test
    fun givenNotAccessible_whenStopService_thenNotPermitted() {
        am.stopAnswer = -1

        val thrown = assertThrows(SecurityException::class.java) { StopService(COMPONENT, WORK).run(system) }

        assertThat(thrown.toCallFailure()).isInstanceOf(CallFailure.NotPermitted::class.java)
    }

    private fun startFailure(answer: ComponentName): CallFailure {
        am.startAnswer = answer
        val thrown = assertThrows(Exception::class.java) { StartService(COMPONENT, WORK).run(system) }
        return thrown.toCallFailure()
    }

    private fun process(uid: Int, vararg packages: String) = ActivityManager.RunningAppProcessInfo().apply {
        this.uid = uid
        pkgList = arrayOf(*packages)
    }

    private fun service(uid: Int, className: String, started: Boolean, packageName: String = PKG) = ActivityManager.RunningServiceInfo().apply {
        this.uid = uid
        this.started = started
        service = ComponentName(packageName, className)
    }
}
