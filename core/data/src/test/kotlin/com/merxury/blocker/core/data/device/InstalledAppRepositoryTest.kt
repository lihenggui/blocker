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

package com.merxury.blocker.core.data.device

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class InstalledAppRepositoryTest : DeviceRepositoryTestBase() {

    @Test
    fun givenUsersNotCached_whenRefresh_thenUsersReadFirstAndAppsStored() = runTest {
        system.install(installedApp(PKG))

        val result = apps.refresh(OWNER)

        assertThat(result).isEqualTo(ReadResult.Success(Unit))
        assertThat(system.log).containsExactly("users", "apps 0").inOrder()
        assertThat(users.users.first().map { it.id }).containsExactly(OWNER, WORK).inOrder()
        assertThat(apps.apps(OWNER).first()).containsExactly(installedApp(PKG))
    }

    @Test
    fun givenUserTheSystemDoesNotShow_whenRefresh_thenUserNotReachableAndNoAppsRead() = runTest {
        val result = apps.refresh(UserId(11))

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.UserNotReachable(UserId(11))))
        assertThat(system.reads("apps")).isEqualTo(0)
    }

    @Test
    fun givenCachedApps_whenReadFails_thenCacheUntouchedAndFailureReturned() = runTest {
        system.install(installedApp(PKG))
        apps.refresh(OWNER)
        system.apps.clear()
        system.appsFailures[OWNER] = ReadFailure.ChannelUnavailable

        val result = apps.refresh(OWNER)

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
        assertThat(apps.apps(OWNER).first()).containsExactly(installedApp(PKG))
    }

    @Test
    fun givenAppGoneFromTheSystem_whenRefresh_thenRemovedFromCache() = runTest {
        system.install(installedApp(PKG))
        system.install(installedApp(OTHER_PKG))
        apps.refresh(OWNER)
        system.apps.remove(AppKey(OTHER_PKG, OWNER))

        apps.refresh(OWNER)

        assertThat(apps.apps(OWNER).first().map { it.key.packageName }).containsExactly(PKG)
    }

    @Test
    fun givenTwoUsers_whenRefreshEach_thenEachUserSeesOnlyItsApps() = runTest {
        system.install(installedApp(PKG, OWNER))
        system.install(installedApp(OTHER_PKG, WORK))

        apps.refresh(OWNER)
        apps.refresh(WORK)

        assertThat(apps.apps(OWNER).first().map { it.key }).containsExactly(AppKey(PKG, OWNER))
        assertThat(apps.apps(WORK).first().map { it.key }).containsExactly(AppKey(OTHER_PKG, WORK))
        assertThat(apps.search(WORK, "other").first().map { it.key }).containsExactly(AppKey(OTHER_PKG, WORK))
        assertThat(apps.search(OWNER, "other").first()).isEmpty()
    }

    @Test
    fun givenAppRemovedForOneUser_whenRefreshApp_thenNullAndKeptForTheOtherUser() = runTest {
        system.install(installedApp(PKG, OWNER))
        system.install(installedApp(PKG, WORK))
        apps.refresh(OWNER)
        apps.refresh(WORK)
        system.apps.remove(AppKey(PKG, WORK))

        val result = apps.refreshApp(AppKey(PKG, WORK))

        assertThat(result).isEqualTo(ReadResult.Success(null))
        assertThat(apps.app(AppKey(PKG, WORK)).first()).isNull()
        assertThat(apps.app(AppKey(PKG, OWNER)).first()).isEqualTo(installedApp(PKG, OWNER))
    }

    @Test
    fun givenUnreachableUser_whenRefreshApp_thenFailureAndCachedAppKept() = runTest {
        system.install(installedApp(PKG, WORK))
        apps.refresh(WORK)
        system.appsFailures[WORK] = ReadFailure.UserNotReachable(WORK)

        val result = apps.refreshApp(AppKey(PKG, WORK))

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.UserNotReachable(WORK)))
        assertThat(apps.app(AppKey(PKG, WORK)).first()).isEqualTo(installedApp(PKG, WORK))
    }

    @Test
    fun givenCachedApp_whenSetEnabled_thenCallAnswerReturnedAndCacheFromFreshRead() = runTest {
        system.install(installedApp(PKG))
        apps.refresh(OWNER)
        // The call's answer differs from what the system then holds; the cache must show the latter.
        appControl.answer = AppEnabledSetting.DISABLED

        val result = apps.setEnabled(AppKey(PKG, OWNER), enabled = false)

        assertThat(result).isEqualTo(CallResult.Success(AppEnabledSetting.DISABLED))
        assertThat(apps.app(AppKey(PKG, OWNER)).first()?.enabledSetting).isEqualTo(AppEnabledSetting.DISABLED_USER)
    }

    @Test
    fun givenFailingCall_whenSetEnabled_thenFailureReturnedAndAppReadAgain() = runTest {
        system.install(installedApp(PKG))
        apps.refresh(OWNER)
        appControl.callFailure = CallFailure.NotPermitted("refused")

        val result = apps.setEnabled(AppKey(PKG, OWNER), enabled = false)

        assertThat(result).isEqualTo(CallResult.Failure(CallFailure.NotPermitted("refused")))
        assertThat(system.reads("app ")).isEqualTo(1)
        assertThat(apps.app(AppKey(PKG, OWNER)).first()?.enabledSetting).isEqualTo(AppEnabledSetting.DEFAULT)
    }

    @Test
    fun givenCachedApp_whenUninstall_thenRemovedFromCacheWithItsComponents() = runTest {
        system.install(installedApp(PKG), declaration("Main"))
        components(backgroundScope).refresh(AppKey(PKG, OWNER))

        val result = apps.uninstall(AppKey(PKG, OWNER))

        assertThat(result).isEqualTo(CallResult.Success(Unit))
        assertThat(apps.app(AppKey(PKG, OWNER)).first()).isNull()
        assertThat(dao.getDeclarations(PKG)).isEmpty()
    }
}
