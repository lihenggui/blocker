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

package com.merxury.blocker.core.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ServiceInfo
import android.os.UserManager
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.UserKind
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess

private const val PKG = "com.example"
private const val APP_ID = 10_123

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class LocalPlatformPackagesTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun platformFor(userId: Int): LocalPlatformPackages {
        ShadowProcess.setUid(userId * 100_000 + APP_ID)
        shadowOf(context.getSystemService(UserManager::class.java)).addUser(userId, "User $userId", 0)
        return LocalPlatformPackages(context)
    }

    private fun installExample() {
        val service = ServiceInfo().apply {
            packageName = PKG
            name = "$PKG.Sync"
        }
        val info = PackageInfo().apply {
            packageName = PKG
            applicationInfo = ApplicationInfo().apply {
                packageName = PKG
                nonLocalizedLabel = "Example"
            }
            services = arrayOf(service)
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    @Test
    fun givenRunningAsOwner_whenReadUsers_thenOnlyOwner() = runTest {
        val user = platformFor(userId = 0).users().single()

        assertThat(user.id).isEqualTo(UserId.SYSTEM)
        assertThat(user.kind).isEqualTo(UserKind.OWNER)
        assertThat(user.parent).isNull()
        assertThat(user.running).isTrue()
    }

    @Test
    fun givenRunningInAnotherUser_whenReadUsers_thenThatUserFromUid() = runTest {
        val user = platformFor(userId = 11).users().single()

        assertThat(user.id).isEqualTo(UserId(11))
        assertThat(user.kind).isEqualTo(UserKind.SECONDARY)
    }

    @Test
    fun givenRunningInWorkProfile_whenReadUsers_thenWorkProfile() = runTest {
        val platform = platformFor(userId = 10)
        shadowOf(context.getSystemService(UserManager::class.java)).setManagedProfile(true)

        assertThat(platform.users().single().kind).isEqualTo(UserKind.WORK_PROFILE)
    }

    @Test
    @Config(sdk = [28], manifest = Config.NONE)
    fun givenAndroidBefore11InWorkProfile_whenReadUsers_thenReportedAsSecondary() = runTest {
        val platform = platformFor(userId = 10)
        shadowOf(context.getSystemService(UserManager::class.java)).setManagedProfile(true)

        assertThat(platform.users().single().kind).isEqualTo(UserKind.SECONDARY)
    }

    @Test
    fun givenAnotherUser_whenRead_thenUserNotReachable() {
        val platform = platformFor(userId = 0)
        val other = UserId(10)

        assertThat(assertThrows(UserNotReachableException::class.java) { runBlocking { platform.installedPackages(other) } }.user)
            .isEqualTo(other)
        assertThrows(UserNotReachableException::class.java) { runBlocking { platform.packageInfo(PKG, 0, other) } }
        assertThrows(UserNotReachableException::class.java) {
            runBlocking { platform.applicationEnabledSettings(listOf(PKG), other) }
        }
        assertThrows(UserNotReachableException::class.java) {
            runBlocking { platform.componentEnabledSettings(PKG, listOf("$PKG.Sync"), other) }
        }
    }

    @Test
    fun givenInstalledPackage_whenReadOwnUser_thenPackageManagerAnswers() = runTest {
        installExample()
        val platform = platformFor(userId = 0)
        val component = ComponentName(PKG, "$PKG.Sync")
        context.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )

        assertThat(platform.installedPackages(UserId.SYSTEM).map { it.packageName }).contains(PKG)
        val flags = PackageManager.GET_SERVICES or PackageManager.MATCH_DISABLED_COMPONENTS
        assertThat(platform.packageInfo(PKG, flags, UserId.SYSTEM).services!!.single().name).isEqualTo("$PKG.Sync")
        assertThat(platform.componentEnabledSettings(PKG, listOf("$PKG.Sync", "$PKG.Other"), UserId.SYSTEM))
            .containsExactly(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            .inOrder()
        assertThat(platform.applicationEnabledSettings(listOf(PKG, "com.missing"), UserId.SYSTEM))
            .containsExactly(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, null)
            .inOrder()
        assertThat(platform.label(platform.packageInfo(PKG, 0, UserId.SYSTEM).applicationInfo!!)).isEqualTo("Example")
    }

    @Test
    fun givenMissingPackage_whenRead_thenNameNotFoundOrUnreadable() = runTest {
        val platform = platformFor(userId = 0)

        assertThrows(NameNotFoundException::class.java) { runBlocking { platform.packageInfo(PKG, 0, UserId.SYSTEM) } }
        assertThrows(NameNotFoundException::class.java) {
            runBlocking { platform.componentEnabledSettings(PKG, listOf("$PKG.Sync"), UserId.SYSTEM) }
        }
        assertThat(platform.applicationEnabledSettings(listOf(PKG), UserId.SYSTEM)).containsExactly(null)
    }
}
