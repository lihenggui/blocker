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

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ComponentOutcome
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlFailure
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.control.layersFor
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val MAIN = declaration("Main")
private val SYNC = declaration("Sync", ComponentType.SERVICE)
private val SELF_OFF = declaration("SelfOff", ComponentType.RECEIVER)
private val DATA = declaration("Data", ComponentType.PROVIDER)
private val OTHER_SELF_OFF = declaration("SelfOff", ComponentType.RECEIVER, packageName = OTHER_PKG)
private val OTHER_TRACKER = declaration("Tracker", ComponentType.SERVICE, packageName = OTHER_PKG)

private fun ComponentDeclaration.key(user: UserId = OWNER) = ComponentKey(id, user)

/**
 * The record of Blocker's PackageManager blocks as the repositories keep it: written by control,
 * pruned by refreshes and app removal, read by [ComponentStatusRepository.disabledByBlocker],
 * over a real in-memory cache and record database and a fake system.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PmBlockRecordsTest : DeviceRepositoryTestBase() {

    /**
     * Makes control change the fake system as ComponentControl would and read it back: components
     * in [refused] are left as they are and fail, and those in [unreadable] read back UNKNOWN.
     */
    private fun controlThroughSystem(
        refused: Set<ComponentId> = emptySet(),
        unreadable: Set<ComponentId> = emptySet(),
        identity: PrivilegedIdentity = PrivilegedIdentity.ROOT,
    ) {
        componentControl.apply = { request ->
            flow {
                val outcomes = request.components.map { declaration ->
                    val refusal = if (declaration.id in refused) ControlFailure.NotPermitted("protected", identity) else null
                    apply(request, declaration, refusal, declaration.id in unreadable)
                }
                outcomes.groupBy { it.key.id.packageName }.forEach { (packageName, done) -> emit(ControlEvent.PackageDone(packageName, done)) }
                emit(ControlEvent.Finished(outcomes.count { it.result == ControlResult.ACHIEVED }, 0, outcomes.count { it.result != ControlResult.ACHIEVED }))
            }
        }
    }

    private fun apply(request: ControlRequest, declaration: ComponentDeclaration, refusal: ControlFailure.NotPermitted?, unreadable: Boolean): ComponentOutcome {
        val key = ComponentKey(declaration.id, request.user)
        val layers = declaration.layersFor(request.layers, request.goal)
        val throughPm = ControlLayer.PACKAGE_MANAGER in layers
        val before = system.settings[request.user to declaration.id] ?: PmSetting.DEFAULT
        if (throughPm && refusal == null) {
            val after = when (request.goal) {
                ControlGoal.BLOCK -> PmSetting.DISABLED
                ControlGoal.ENABLE_EXPLICIT -> PmSetting.ENABLED
                ControlGoal.UNBLOCK -> if (before == PmSetting.DISABLED) PmSetting.DEFAULT else before
                ControlGoal.ENABLE -> if (declaration.enabledInManifest) PmSetting.DEFAULT else PmSetting.ENABLED
            }
            system.put(request.user, declaration, after)
        }
        val ifw = if (ControlLayer.INTENT_FIREWALL in layers && request.goal == ControlGoal.BLOCK) IfwBlock.BLOCKED else IfwBlock.NOT_BLOCKED
        return when {
            unreadable -> ComponentOutcome(key, ControlState(PmSetting.UNKNOWN, ifw), ControlResult.FAILED, mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.StateNotConfirmed))
            refusal != null && throughPm -> ComponentOutcome(key, ControlState(before, ifw), ControlResult.FAILED, mapOf(ControlLayer.PACKAGE_MANAGER to refusal))
            else -> ComponentOutcome(key, ControlState(system.settings[request.user to declaration.id] ?: PmSetting.DEFAULT, ifw), ControlResult.ACHIEVED, emptyMap())
        }
    }

    private suspend fun ComponentStatusRepository.run(user: UserId, goal: ControlGoal, layers: Set<ControlLayer>, vararg components: ComponentDeclaration) = control(ControlRequest(user, components.toList(), goal, layers)).toList()

    private suspend fun recorded(user: UserId = OWNER) = pmBlocks.of(user)

    /** Installs [PKG] with [MAIN], [SYNC], [SELF_OFF] and [DATA] and [OTHER_PKG] with its two, for [users], and reads them. */
    private suspend fun installed(scope: CoroutineScope, users: List<UserId> = listOf(OWNER)): LocalComponentStatusRepository {
        users.forEach { user ->
            system.install(installedApp(PKG, user), MAIN, SYNC, SELF_OFF, DATA)
            system.install(installedApp(OTHER_PKG, user), OTHER_SELF_OFF, OTHER_TRACKER)
        }
        val repository = components(scope)
        users.forEach { user ->
            repository.refresh(AppKey(PKG, user))
            repository.refresh(AppKey(OTHER_PKG, user))
        }
        controlThroughSystem()
        return repository
    }

    @Test
    fun givenBlockThroughPackageManager_whenControlled_thenEachDisabledComponentIsRecorded() = runTest {
        val repository = installed(backgroundScope)

        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)

        assertThat(recorded()).containsExactly(MAIN.key(), SYNC.key()).inOrder()
        assertThat(recordDao.getByUser(OWNER.value).map { it.recordedAt }.all { it > 0 }).isTrue()
    }

    @Test
    fun givenBlockThroughIntentFirewallOnly_whenControlled_thenProviderAndReceiverAreRecorded() = runTest {
        val repository = installed(backgroundScope)
        // The component was disabled by someone else before; a firewall block does not make it Blocker's.
        system.put(OWNER, SYNC, PmSetting.DISABLED)
        repository.refresh(AppKey(PKG, OWNER))

        val request = ControlRequest(OWNER, listOf(MAIN, SYNC, DATA, SELF_OFF), ControlGoal.BLOCK, setOf(ControlLayer.INTENT_FIREWALL))
        repository.control(request).test {
            assertThat(awaitItem()).isInstanceOf(ControlEvent.PackageDone::class.java)
            assertThat(recorded()).containsExactly(DATA.key(), SELF_OFF.key())
            assertThat(awaitItem()).isEqualTo(ControlEvent.Finished(achieved = 4, partial = 0, failed = 0))
            awaitComplete()
        }
        val found = repository.disabledByBlocker(OWNER) as ReadResult.Success

        assertThat(found.value.components).containsExactly(DATA.key(), SELF_OFF.key())

        repository.resetPmBlocks(OWNER, listOf(SELF_OFF)).toList()

        assertThat(recorded()).containsExactly(DATA.key())
        assertThat(system.settings[OWNER to SELF_OFF.id]).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenComponentAlreadyDisabledElsewhere_whenBlockingAndResetting_thenItStaysDisabled() = runTest {
        val repository = installed(backgroundScope)
        system.put(OWNER, SYNC, PmSetting.DISABLED)

        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)
        val found = repository.disabledByBlocker(OWNER) as ReadResult.Success
        repository.resetPmBlocks(OWNER, listOf(MAIN)).toList()

        assertThat(found.value.components).containsExactly(MAIN.key())
        assertThat(recorded()).isEmpty()
        assertThat(system.settings[OWNER to SYNC.id]).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenVerifiedWakeBlockAndExternalBlock_whenManuallyBlocking_thenOnlyWakeBlockTransfers() = runTest {
        system.install(installedApp(PKG), MAIN, SYNC)
        system.put(OWNER, MAIN, PmSetting.DISABLED)
        system.put(OWNER, SYNC, PmSetting.DISABLED)
        controlThroughSystem()
        val repository = components(backgroundScope) { user, candidates ->
            assertThat(user).isEqualTo(OWNER)
            candidates.filter { it == MAIN.id }
        }

        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)

        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenHiddenWorkApp_whenReconcilingListingAndResetting_thenItsRecordSurvivesUntilUnfrozen() = runTest {
        val app = AppKey(PKG, WORK)
        val repository = installed(backgroundScope, listOf(OWNER, WORK))
        repository.run(WORK, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)

        system.hidden += app
        repository.reconcile(WORK, deviceUser(WORK))
        assertThat(apps.app(app).first()).isNull()
        assertThat(recorded(WORK)).containsExactly(MAIN.key(WORK))
        apps.refresh(WORK)
        val frozen = repository.disabledByBlocker(WORK) as ReadResult.Success
        assertThat(frozen.value.components).isEmpty()
        assertThat(frozen.value.unreadableApps).isEmpty()
        assertThat(frozen.value.notInstalled).isEqualTo(0)
        assertThat(recorded(WORK)).containsExactly(MAIN.key(WORK))
        assertThat(system.settings[WORK to MAIN.id]).isEqualTo(PmSetting.DISABLED)

        system.hidden -= app
        val unfrozen = repository.disabledByBlocker(WORK) as ReadResult.Success
        assertThat(unfrozen.value.components).containsExactly(MAIN.key(WORK))
        repository.resetPmBlocks(WORK, listOf(MAIN)).toList()

        assertThat(system.settings[WORK to MAIN.id]).isEqualTo(PmSetting.DEFAULT)
        assertThat(recorded(WORK)).isEmpty()
    }

    @Test
    fun givenAnAppMissingFromTheList_whenItsInstallationCannotBeVerified_thenResetReportsItAsUnreadable() = runTest {
        val repository = installed(backgroundScope)
        val app = AppKey(PKG, OWNER)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        system.hidden += app
        system.installationFailures[app] = ReadFailure.ChannelUnavailable

        val found = repository.disabledByBlocker(OWNER) as ReadResult.Success

        assertThat(found.value.unreadableApps).containsExactly(app)
        assertThat(found.value.notInstalled).isEqualTo(0)
        assertThat(found.value.components).isEmpty()
        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenInterruptedPmBlockJournal_whenResetReadsSystem_thenUnsentBlockIsPruned() = runTest {
        val repository = installed(backgroundScope)
        pmBlocks.record(listOf(MAIN.key(), SYNC.key()))
        system.put(OWNER, MAIN, PmSetting.DISABLED)

        val found = repository.disabledByBlocker(OWNER) as ReadResult.Success

        assertThat(found.value.components).containsExactly(MAIN.key())
        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenRecordedBlocks_whenUnblockedOrEnabled_thenTheirRecordsAreDropped() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC, DATA)

        repository.run(OWNER, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        repository.run(OWNER, ControlGoal.ENABLE, ControlLayer.entries.toSet(), SYNC)

        assertThat(recorded()).containsExactly(DATA.key())
    }

    @Test
    fun givenUnblockOfTheFirewallOnly_whenTheSettingStaysDisabled_thenTheRecordIsKept() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, ControlLayer.entries.toSet(), MAIN)

        repository.run(OWNER, ControlGoal.UNBLOCK, setOf(ControlLayer.INTENT_FIREWALL), MAIN)

        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenSystemRefusesOneComponent_whenBlocked_thenOnlyTheOtherIsRecorded() = runTest {
        val repository = installed(backgroundScope)
        controlThroughSystem(refused = setOf(SYNC.id))

        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)

        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenRecordedBlock_whenAnUnblockReadsBackUnknown_thenTheRecordIsKept() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        controlThroughSystem(unreadable = setOf(MAIN.id))

        repository.run(OWNER, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)

        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenBlocksOfTwoUsers_whenOneUserUnblocks_thenTheOtherUsersRecordIsKept() = runTest {
        val repository = installed(backgroundScope, listOf(OWNER, WORK))
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        repository.run(WORK, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)

        repository.run(WORK, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)

        assertThat(recorded(OWNER)).containsExactly(MAIN.key(OWNER))
        assertThat(recorded(WORK)).isEmpty()
    }

    @Test
    fun givenRecordedBlocks_whenReEnabledElsewhereOrNoLongerDeclared_thenARefreshDropsThem() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC, DATA)
        // The user enabled MAIN in another app; an update dropped SYNC.
        system.put(OWNER, MAIN, PmSetting.ENABLED)
        system.install(installedApp(PKG, lastUpdateTime = 3_000).copy(versionCode = 2), MAIN, SELF_OFF, DATA)

        repository.refresh(AppKey(PKG, OWNER))

        assertThat(recorded()).containsExactly(DATA.key())
    }

    @Test
    fun givenRecordedBlock_whenTheSettingsCannotBeRead_thenARefreshKeepsIt() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        system.put(OWNER, MAIN, PmSetting.DEFAULT)
        system.pmFailures[PKG] = ReadFailure.ChannelUnavailable

        repository.refresh(AppKey(PKG, OWNER))

        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenRecordedBlock_whenTheDeclarationsCannotBeReReadAfterAnUpdate_thenARefreshKeepsIt() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        // A record the cached declarations do not hold, as a seeded one may be.
        pmBlocks.record(listOf(ComponentKey(ComponentId(PKG, "$PKG.NotCached"), OWNER)))
        system.install(installedApp(PKG, lastUpdateTime = 3_000), MAIN, SYNC, SELF_OFF, DATA)
        system.declarationFailures[PKG] = ReadFailure.ManifestUnreadable(PKG)

        repository.refresh(AppKey(PKG, OWNER))

        assertThat(recorded()).containsExactly(MAIN.key(), ComponentKey(ComponentId(PKG, "$PKG.NotCached"), OWNER)).inOrder()
    }

    @Test
    fun givenRecordedBlocksOfTwoUsers_whenTheAppIsRemovedForOne_thenOnlyThatUsersRecordsGo() = runTest {
        val repository = installed(backgroundScope, listOf(OWNER, WORK))
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        repository.run(WORK, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        repository.run(WORK, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), OTHER_TRACKER)

        system.apps.remove(AppKey(PKG, WORK))
        repository.refresh(AppKey(PKG, WORK))
        system.apps.remove(AppKey(OTHER_PKG, WORK))
        apps.refresh(WORK)

        assertThat(recorded(WORK)).isEmpty()
        assertThat(recorded(OWNER)).containsExactly(MAIN.key(OWNER))
    }

    @Test
    fun givenOrphanedRecords_whenAppListRefreshes_thenForgetOnlyUninstalledPackages() = runTest {
        val orphan = ComponentKey(ComponentId("com.gone", "com.gone.Main"), OWNER)
        val ownerRecord = MAIN.key(OWNER)
        val workRecord = MAIN.key(WORK)
        pmBlocks.record(listOf(orphan, ownerRecord, workRecord))
        system.install(installedApp(PKG), MAIN)

        apps.refresh(OWNER)

        assertThat(recorded(OWNER)).containsExactly(ownerRecord)
        assertThat(recorded(WORK)).containsExactly(workRecord)
    }

    @Test
    fun givenOrphanedRecord_whenManuallyRefreshingMissingApp_thenForgetIt() = runTest {
        val key = AppKey(PKG, OWNER)
        pmBlocks.record(listOf(MAIN.key(OWNER)))

        components(backgroundScope).refresh(key)

        assertThat(recorded(OWNER)).isEmpty()
    }

    @Test
    fun givenGoneAppAndOrphanedRecord_whenFullIndexListsApps_thenForgetBoth() = runTest {
        val repository = installed(backgroundScope)
        pmBlocks.record(listOf(MAIN.key(OWNER), ComponentKey(ComponentId("com.gone", "com.gone.Main"), OWNER), MAIN.key(WORK)))
        system.apps.remove(AppKey(PKG, OWNER))

        repository.refreshAll(OWNER).toList()

        assertThat(recorded(OWNER)).isEmpty()
        assertThat(recorded(WORK)).containsExactly(MAIN.key(WORK))
    }

    @Test
    fun givenAppRemovedWhileIndexReadsSettings_whenPackageDisappears_thenForgetRecords() = runTest {
        val repository = installed(backgroundScope)
        pmBlocks.record(listOf(MAIN.key(OWNER)))
        system.beforePmRead = { key ->
            if (key == AppKey(PKG, OWNER)) system.apps.remove(key)
        }

        repository.refreshAll(OWNER).toList()

        assertThat(recorded(OWNER)).isEmpty()
        assertThat(apps.app(AppKey(PKG, OWNER)).first()).isNull()
    }

    @Test
    fun givenBlockerAndSelfDisabledComponents_whenResetSelectsAndUnblocks_thenOnlyBlockersAreCleared() = runTest {
        val repository = installed(backgroundScope)
        // Disabled by the apps themselves, one in the same app as Blocker's blocks.
        system.put(OWNER, SELF_OFF, PmSetting.DISABLED)
        system.put(OWNER, OTHER_SELF_OFF, PmSetting.DISABLED)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)

        val found = repository.disabledByBlocker(OWNER)
        val keys = (found as ReadResult.Success).value.components
        repository.run(OWNER, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER), *listOf(MAIN, SYNC).filter { it.key() in keys }.toTypedArray())

        assertThat(found.value).isEqualTo(DisabledComponents(listOf(MAIN.key(), SYNC.key()), emptyList()))
        assertThat(system.settings[OWNER to MAIN.id]).isEqualTo(PmSetting.DEFAULT)
        assertThat(system.settings[OWNER to SYNC.id]).isEqualTo(PmSetting.DEFAULT)
        assertThat(system.settings[OWNER to SELF_OFF.id]).isEqualTo(PmSetting.DISABLED)
        assertThat(system.settings[OWNER to OTHER_SELF_OFF.id]).isEqualTo(PmSetting.DISABLED)
        assertThat(recorded()).isEmpty()
    }

    @Test
    fun givenRecordedBlockClearedElsewhere_whenSelectedForReset_thenItIsLeftOutAndForgotten() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)
        system.put(OWNER, SYNC, PmSetting.DEFAULT)

        val found = repository.disabledByBlocker(OWNER)

        assertThat(found).isEqualTo(ReadResult.Success(DisabledComponents(listOf(MAIN.key()), emptyList())))
        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenRecordsOfAnUninstalledAppAnUnreadableAppAndAnUndeclaredComponent_whenSelectedForReset_thenEachIsCounted() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, OTHER_TRACKER)
        // Records the app list cannot have dropped: an app gone before the record was made, as a
        // seed may leave, and a class the installed app does not declare.
        pmBlocks.record(listOf(ComponentKey(ComponentId("com.gone", "com.gone.A"), OWNER), ComponentKey(ComponentId("com.gone", "com.gone.B"), OWNER)))
        pmBlocks.record(listOf(ComponentKey(ComponentId(PKG, "$PKG.Undeclared"), OWNER)))
        system.pmFailures[OTHER_PKG] = ReadFailure.ChannelUnavailable

        val found = repository.disabledByBlocker(OWNER)

        assertThat(found).isEqualTo(ReadResult.Success(DisabledComponents(listOf(MAIN.key()), listOf(AppKey(OTHER_PKG, OWNER)), notInstalled = 2, notDeclared = 1)))
        // The refresh dropped the undeclared one; the unreadable app keeps its record.
        assertThat(recorded()).containsExactly(MAIN.key(), OTHER_TRACKER.key()).inOrder()
    }

    @Test
    fun givenAppListUnreadable_whenSelectedForReset_thenTheFailureIsReturnedAndRecordsKept() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN)
        system.appsFailures[OWNER] = ReadFailure.ChannelUnavailable

        assertThat(repository.disabledByBlocker(OWNER)).isEqualTo(ReadResult.Failure(ReadFailure.ChannelUnavailable))
        assertThat(recorded()).containsExactly(MAIN.key())
    }

    @Test
    fun givenPrivilegedRefusalOutsideReset_whenUnblocking_thenTheRecordIsKept() = runTest {
        val repository = installed(backgroundScope)
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)
        controlThroughSystem(refused = setOf(SYNC.id))

        repository.run(OWNER, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC)

        assertThat(recorded()).containsExactly(SYNC.key())
        assertThat(system.settings[OWNER to SYNC.id]).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenRootRefusal_whenResetting_thenTheRefusedRecordIsForgotten() = runTest {
        assertResetRecords(backgroundScope, PrivilegedIdentity.ROOT, DATA)
    }

    @Test
    fun givenSystemRefusal_whenResetting_thenTheRefusedRecordIsForgotten() = runTest {
        assertResetRecords(backgroundScope, PrivilegedIdentity.SYSTEM, DATA)
    }

    @Test
    fun givenShellRefusal_whenResetting_thenTheRefusedRecordIsKept() = runTest {
        assertResetRecords(backgroundScope, PrivilegedIdentity.SHELL, DATA, SYNC)
    }

    private suspend fun assertResetRecords(scope: CoroutineScope, identity: PrivilegedIdentity, vararg remaining: ComponentDeclaration) {
        val repository = installed(scope, listOf(OWNER, WORK))
        repository.run(OWNER, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), MAIN, SYNC, DATA)
        repository.run(WORK, ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER), SYNC)
        controlThroughSystem(refused = setOf(SYNC.id, DATA.id), unreadable = setOf(DATA.id), identity = identity)

        repository.resetPmBlocks(OWNER, listOf(MAIN, SYNC, DATA)).toList()

        assertThat(recorded()).containsExactlyElementsIn(remaining.map { it.key() })
        assertThat(recorded(WORK)).containsExactly(SYNC.key(WORK))
        assertThat(system.settings[OWNER to SYNC.id]).isEqualTo(PmSetting.DISABLED)
        val next = repository.disabledByBlocker(OWNER) as ReadResult.Success
        assertThat(next.value.components).containsExactlyElementsIn(remaining.map { it.key() })
    }
}
