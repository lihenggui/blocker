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
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val MAIN = declaration("Main")
private val SYNC = declaration("Sync", ComponentType.SERVICE)
private val FILES = declaration("Files", ComponentType.PROVIDER)
private val OWNER_APP = AppKey(PKG, OWNER)
private val WORK_APP = AppKey(PKG, WORK)

internal fun status(declaration: ComponentDeclaration, pm: PmSetting, ifw: IfwBlock) = ComponentStatus(declaration, ControlState(pm, ifw))

/** [ComponentStatusRepository.refresh] of one app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ComponentRefreshTest : DeviceRepositoryTestBase() {

    @Test
    fun givenUnscannedApp_whenRefresh_thenDeclarationsSettingsAndRulesStored() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN, SYNC, FILES)
        system.put(OWNER, SYNC, PmSetting.DISABLED)
        store.setBlocked(PKG, mapOf(MAIN to true))

        val result = repository.refresh(OWNER_APP)

        assertThat(result).isEqualTo(ReadResult.Success(Unit))
        assertThat(repository.components(OWNER_APP).first()).containsExactly(
            status(FILES, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
            status(MAIN, PmSetting.DEFAULT, IfwBlock.BLOCKED),
            status(SYNC, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED),
        ).inOrder()
        assertThat(system.log).containsExactly("users", "app $OWNER_APP", "declarations $PKG", "pm $OWNER_APP").inOrder()
    }

    @Test
    fun givenMarkerMatchesLastUpdateTime_whenRefresh_thenDeclarationsNotReadAgainButStateIs() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        system.put(OWNER, MAIN, PmSetting.DISABLED)

        repository.refresh(OWNER_APP)

        assertThat(system.reads("declarations")).isEqualTo(1)
        assertThat(repository.components(OWNER_APP).first()).containsExactly(status(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenAppUpdated_whenRefresh_thenDeclarationsReadAgainAndRemovedOnesGone() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN, SYNC)
        repository.refresh(OWNER_APP)
        system.install(installedApp(PKG, lastUpdateTime = 3_000), MAIN, FILES)

        repository.refresh(OWNER_APP)

        assertThat(system.reads("declarations")).isEqualTo(2)
        assertThat(repository.components(OWNER_APP).first().map { it.declaration }).containsExactly(FILES, MAIN).inOrder()
        assertThat(dao.scanMarker(PKG)).isEqualTo(installedApp(PKG, lastUpdateTime = 3_000).lastUpdateTime)
    }

    @Test
    fun givenUpdatedAppWithUnreadableManifest_whenRefresh_thenOldDeclarationsKeptWithStateAndFailureReturned() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        system.install(installedApp(PKG, lastUpdateTime = 3_000))
        system.declarationFailures[PKG] = ReadFailure.ManifestUnreadable(PKG)
        system.put(OWNER, MAIN, PmSetting.DISABLED)

        val result = repository.refresh(OWNER_APP)

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.ManifestUnreadable(PKG)))
        assertThat(repository.components(OWNER_APP).first()).containsExactly(status(MAIN, PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        // The old marker stays, so the next refresh tries again.
        assertThat(dao.scanMarker(PKG)).isEqualTo(installedApp(PKG).lastUpdateTime)
    }

    @Test
    fun givenTwoUsers_whenRefreshOne_thenOnlyThatUsersSettingsChange() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG, OWNER), MAIN)
        system.install(installedApp(PKG, WORK), MAIN)
        system.put(WORK, MAIN, PmSetting.DISABLED)
        repository.refresh(OWNER_APP)
        repository.refresh(WORK_APP)
        system.put(OWNER, MAIN, PmSetting.ENABLED)
        system.put(WORK, MAIN, PmSetting.DEFAULT)

        repository.refresh(OWNER_APP)

        assertThat(repository.component(ComponentKey(MAIN.id, OWNER)).first()?.state?.pm).isEqualTo(PmSetting.ENABLED)
        assertThat(repository.component(ComponentKey(MAIN.id, WORK)).first()?.state?.pm).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenPmSettingsUnreadable_whenRefresh_thenStoredUnknownRulesStillStoredAndFailureReturned() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN, SYNC)
        system.put(OWNER, MAIN, PmSetting.DISABLED)
        repository.refresh(OWNER_APP)
        store.setBlocked(PKG, mapOf(SYNC to true))
        system.pmFailures[PKG] = ReadFailure.ChannelUnavailable

        val result = repository.refresh(OWNER_APP)

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
        assertThat(repository.components(OWNER_APP).first()).containsExactly(
            status(MAIN, PmSetting.UNKNOWN, IfwBlock.NOT_BLOCKED),
            status(SYNC, PmSetting.UNKNOWN, IfwBlock.BLOCKED),
        ).inOrder()
    }

    @Test
    fun givenRulesUnreadable_whenRefresh_thenFilteredComponentsUnknownAndRulesUnreadableReturned() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN, FILES)
        system.put(OWNER, MAIN, PmSetting.DISABLED)
        files.noAccess = true

        val result = repository.refresh(OWNER_APP)

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.RulesUnreadable(PKG)))
        assertThat(repository.components(OWNER_APP).first()).containsExactly(
            status(FILES, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
            status(MAIN, PmSetting.DISABLED, IfwBlock.UNKNOWN),
        ).inOrder()
    }

    @Test
    fun givenAppRemovedForOneUser_whenRefresh_thenGoneForThatUserAndKeptForTheOther() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG, OWNER), MAIN)
        system.install(installedApp(PKG, WORK), MAIN)
        repository.refresh(OWNER_APP)
        repository.refresh(WORK_APP)
        system.apps.remove(WORK_APP)

        val result = repository.refresh(WORK_APP)

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.PackageNotFound(WORK_APP)))
        assertThat(apps.app(WORK_APP).first()).isNull()
        assertThat(repository.allComponents(WORK).first()).isEmpty()
        assertThat(repository.components(OWNER_APP).first()).containsExactly(status(MAIN, PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenAppRemovedForItsOnlyUser_whenRefresh_thenDeclarationsRemoved() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG), MAIN)
        repository.refresh(OWNER_APP)
        system.apps.remove(OWNER_APP)

        repository.refresh(OWNER_APP)

        assertThat(repository.components(OWNER_APP).first()).isEmpty()
        assertThat(dao.scanMarker(PKG)).isNull()
    }

    @Test
    fun givenUnreachableUser_whenRefresh_thenCachedDeclarationsKeptAndStateUnknown() = runTest {
        val repository = components(backgroundScope)
        system.install(installedApp(PKG, WORK), MAIN)
        system.put(WORK, MAIN, PmSetting.DISABLED)
        repository.refresh(WORK_APP)
        system.appsFailures[WORK] = ReadFailure.UserNotReachable(WORK)
        system.pmFailures[PKG] = ReadFailure.UserNotReachable(WORK)

        val result = repository.refresh(WORK_APP)

        assertThat(result).isEqualTo(ReadResult.Failure(ReadFailure.UserNotReachable(WORK)))
        assertThat(repository.components(WORK_APP).first()).containsExactly(status(MAIN, PmSetting.UNKNOWN, IfwBlock.NOT_BLOCKED))
    }

    @Test
    fun givenComponentsOfTwoApps_whenSearch_thenMatchesOfThatUserOnly() = runTest {
        val repository = components(backgroundScope)
        val other = declaration("MainSettings", packageName = OTHER_PKG)
        system.install(installedApp(PKG), MAIN, SYNC)
        system.install(installedApp(OTHER_PKG, WORK), other)
        repository.refresh(OWNER_APP)
        repository.refresh(AppKey(OTHER_PKG, WORK))

        assertThat(repository.search(OWNER, "main").first().map { it.declaration }).containsExactly(MAIN)
        assertThat(repository.search(WORK, "main").first().map { it.declaration }).containsExactly(other)
    }
}
