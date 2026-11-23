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
import android.content.pm.PackageManager.NameNotFoundException
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.privileged.calls.ApplicationEnabledSettings
import com.merxury.blocker.core.privileged.calls.ComponentEnabledSettings
import com.merxury.blocker.core.privileged.calls.ForceStop
import com.merxury.blocker.core.privileged.calls.GetPackageInfo
import com.merxury.blocker.core.privileged.calls.InstalledPackages
import com.merxury.blocker.core.privileged.calls.ListUsers
import com.merxury.blocker.core.privileged.calls.SetApplicationEnabledSetting
import com.merxury.blocker.core.privileged.calls.SetComponentEnabledSettings
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PKG = "com.example"
private const val FLAG_MANAGED_PROFILE = 0x20
private val OWNER = UserId(0)
private val WORK = UserId(10)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivilegedCallsTest {

    private val system = FakeSystemServices()
    private val pm = system.packageManager

    private fun component(name: String) = ComponentName(PKG, "$PKG.$name")

    @Test
    fun givenOwnerWorkProfileAndSecondary_whenListUsers_thenKindParentAndRunningFilled() {
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work", FLAG_MANAGED_PROFILE, parent = 0)
        system.userManager.addUser(11, "Guest")
        system.userManager.addUser(12, "Clone", parent = 0)
        system.activityManager.runningUsers += 10

        val users = ListUsers.run(system)

        assertThat(users).containsExactly(
            DeviceUser(OWNER, "Owner", UserKind.OWNER, parent = null, running = true),
            DeviceUser(WORK, "Work", UserKind.WORK_PROFILE, parent = OWNER, running = true),
            DeviceUser(UserId(11), "Guest", UserKind.SECONDARY, parent = null, running = false),
            DeviceUser(UserId(12), "Clone", UserKind.OTHER_PROFILE, parent = OWNER, running = false),
        ).inOrder()
        assertThat(system.userManager.getUsersArity).isEqualTo(3)
    }

    @Test
    @Config(sdk = [29])
    fun givenAndroid10_whenListUsers_thenOneArgumentGetUsersUsed() {
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work", FLAG_MANAGED_PROFILE, parent = 0)

        val users = ListUsers.run(system)

        assertThat(users.map { it.kind }).containsExactly(UserKind.OWNER, UserKind.WORK_PROFILE).inOrder()
        assertThat(system.userManager.getUsersArity).isEqualTo(1)
    }

    @Test
    @Config(sdk = [28])
    fun givenProfileParentRefused_whenListUsers_thenParentFromProfileGroup() {
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work", FLAG_MANAGED_PROFILE, parent = 0)
        system.userManager.addUser(11, "Guest")
        system.userManager.refuseProfileParent = true

        val users = ListUsers.run(system)

        assertThat(users.map { it.parent }).containsExactly(null, OWNER, null).inOrder()
        assertThat(users.map { it.kind }).containsExactly(UserKind.OWNER, UserKind.WORK_PROFILE, UserKind.SECONDARY).inOrder()
    }

    @Test
    fun givenPackagesInTwoUsers_whenListInstalledPackages_thenOnlyThatUsersPackages() {
        system.userManager.addUser(0, "Owner")
        system.userManager.addUser(10, "Work")
        pm.install(0, PKG)
        pm.install(10, "com.work")

        assertThat(InstalledPackages(WORK).run(system).map { it.packageName }).containsExactly("com.work")
    }

    @Test
    fun givenUserThatDoesNotExist_whenListInstalledPackages_thenRejectedInsteadOfEmpty() {
        system.userManager.addUser(0, "Owner")

        assertThrows(IllegalArgumentException::class.java) { InstalledPackages(UserId(42)).run(system) }
    }

    @Test
    @Config(sdk = [32])
    fun givenAndroid12L_whenListInstalledPackages_thenIntFlagsOverloadAnswers() {
        system.userManager.addUser(0, "Owner")
        pm.install(0, PKG)

        assertThat(InstalledPackages(OWNER).run(system).map { it.packageName }).containsExactly(PKG)
    }

    @Test
    fun givenPackageInOtherUserOnly_whenGetPackageInfo_thenNameNotFound() {
        pm.install(10, PKG)

        assertThat(GetPackageInfo(PKG, PackageManager.GET_SERVICES, WORK).run(system).packageName).isEqualTo(PKG)
        assertThat(pm.flagsSeen.last()).isEqualTo(PackageManager.GET_SERVICES.toLong())
        assertThrows(NameNotFoundException::class.java) { GetPackageInfo(PKG, 0, OWNER).run(system) }
    }

    @Test
    fun givenOneAppMissing_whenReadAppSettings_thenNullForThatAppOnlyInOrder() {
        pm.install(10, PKG)
        pm.install(10, "com.odd")
        pm.appSettings[PKG to 10] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        pm.appSettings["com.odd" to 10] = 42

        val settings = ApplicationEnabledSettings(listOf("com.missing", PKG, "com.odd"), WORK).run(system)

        assertThat(settings).containsExactly(null, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, null).inOrder()
    }

    @Test
    fun givenSystemRefuses_whenReadAppSettings_thenWholeCallFails() {
        pm.install(10, PKG)
        pm.failure = SecurityException("Requires INTERACT_ACROSS_USERS_FULL")

        assertThrows(SecurityException::class.java) { ApplicationEnabledSettings(listOf(PKG), WORK).run(system) }
    }

    @Test
    fun givenStoredAndUnreadableSettings_whenReadComponentSettings_thenOneEntryPerNameInOrder() {
        pm.install(10, PKG)
        pm.componentSettings[component("Disabled") to 10] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.componentSettings[component("Enabled") to 10] = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        pm.componentSettings[component("Odd") to 10] = PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        pm.rejectedComponents += component("Rejected")
        val names = listOf("Default", "Disabled", "Rejected", "Enabled", "Odd").map { "$PKG.$it" }

        val settings = ComponentEnabledSettings(PKG, names, WORK).run(system)

        assertThat(settings).containsExactly(
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            null,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            null,
        ).inOrder()
    }

    @Test
    fun givenPackageNotInstalledForUser_whenReadComponentSettings_thenNameNotFound() {
        pm.install(0, PKG)

        assertThrows(NameNotFoundException::class.java) {
            ComponentEnabledSettings(PKG, listOf("$PKG.Main"), WORK).run(system)
        }
    }

    @Test
    fun givenAndroid15_whenSetComponents_thenOneBatchCallWithCallingPackage() {
        SetComponentEnabledSettings(
            listOf(component("A"), component("B")),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            WORK,
        ).run(system)

        assertThat(pm.changes).containsExactly("setComponentEnabledSettings/${system.callerPackage}")
        assertThat(pm.componentSettings).containsExactly(
            component("A") to 10,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            component("B") to 10,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        )
    }

    @Test
    @Config(sdk = [33])
    fun givenAndroid13_whenSetComponents_thenOneBatchCallWithoutCallingPackage() {
        SetComponentEnabledSettings(listOf(component("A")), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, WORK)
            .run(system)

        assertThat(pm.changes).containsExactly("setComponentEnabledSettings")
    }

    @Test
    @Config(sdk = [29])
    fun givenAndroid10_whenSetComponents_thenOneCallPerComponent() {
        SetComponentEnabledSettings(
            listOf(component("A"), component("B")),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            WORK,
        ).run(system)

        assertThat(pm.changes).containsExactly("setComponentEnabledSetting", "setComponentEnabledSetting")
        assertThat(pm.componentSettings[component("B") to 10]).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    @Test
    fun givenNoComponents_whenSetComponents_thenNothingSent() {
        SetComponentEnabledSettings(emptyList(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, WORK).run(system)

        assertThat(pm.changes).isEmpty()
    }

    @Test
    fun givenAppOnlyState_whenCreateSetComponents_thenRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SetComponentEnabledSettings(listOf(component("A")), PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, WORK)
        }
    }

    @Test
    fun whenSetApplicationEnabledSetting_thenSetForThatUserWithCallingPackage() {
        SetApplicationEnabledSetting(PKG, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, WORK).run(system)

        assertThat(pm.changes).containsExactly("setApplicationEnabledSetting/${system.callerPackage}")
        assertThat(pm.appSettings[PKG to 10]).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
    }

    @Test
    fun whenForceStop_thenStoppedInThatUser() {
        ForceStop(PKG, WORK).run(system)

        assertThat(system.activityManager.forceStopped).containsExactly(PKG to 10)
    }
}
