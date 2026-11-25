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

package com.merxury.blocker.core.rule.backup

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
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
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.rule.BlockerRule
import com.merxury.blocker.core.model.rule.ComponentRule
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.storage.FakeBackupStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockerRuleImporterTest {
    private val installedApps = FakeInstalledAppRepository()
    private val components = FakeComponentStatusRepository()
    private val storage = FakeBackupStorage()
    private val channel = FakePrivilegedChannel()
    private val importer = BlockerRuleImporter(installedApps, components, channel, backupJson)

    private val allRunnable = listOf(status(BOOT), status(SYNC), status(MAIN), status(DATA))

    private suspend fun stateOf(user: UserId, declaration: ComponentDeclaration): ControlState? = components.component(ComponentKey(declaration.id, user)).first()?.state

    @Test
    fun givenBackupThatBlocksProtectedComponent_whenRestoring_thenItCountsAsSkipped() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT)))
        storage.putFile(
            BackupSet(OWNER).fileOf(APP),
            backupJson.encodeToString(
                BlockerRule.serializer(),
                BlockerRule(APP, components = mutableListOf(ComponentRule(APP, BOOT.id.className, false, BOOT.type, ControllerType.PM))),
            ),
        )
        components.controlEvents = { request ->
            listOf(
                ControlEvent.PackageDone(
                    APP,
                    listOf(
                        ComponentOutcome(
                            ComponentKey(BOOT.id, request.user),
                            status(BOOT).state,
                            ControlResult.SKIPPED,
                            mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.ProtectedComponent),
                        ),
                    ),
                ),
                ControlEvent.Finished(0, 0, 0, skipped = 1),
            )
        }

        val result = importer.import(storage, BackupSet(OWNER), OWNER)

        assertThat(result.summary.skipped).containsExactly(SkipReason.PROTECTED_COMPONENT, 1)
        assertThat(result.summary.failed).isEmpty()
        assertThat(result.summary.changedPackages).isEmpty()
    }

    @Test
    fun givenOldFormatOwnerBackup_whenImporting_thenEachEntryGoesToItsLayerWithTheDeclaredType() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, allRunnable)
        storage.putFile("$APP.json", resource("blocker-backup/old_owner_backup.json"))

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        // Batched per goal and layers in the order they first appear. MainActivity says SERVICE and
        // SHIZUKU, so it is a PM block of the declared activity; the provider entry with method IFW
        // is a block, which the control layer applies to its PackageManager setting.
        assertThat(components.requests).containsExactly(
            ControlRequest(OWNER, listOf(BOOT, DATA), ControlGoal.BLOCK, setOf(ControlLayer.INTENT_FIREWALL)),
            ControlRequest(OWNER, listOf(BOOT), ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER)),
            ControlRequest(OWNER, listOf(SYNC), ControlGoal.UNBLOCK, setOf(ControlLayer.INTENT_FIREWALL)),
            ControlRequest(OWNER, listOf(SYNC, MAIN), ControlGoal.BLOCK, setOf(ControlLayer.PACKAGE_MANAGER)),
        ).inOrder()
        assertThat(stateOf(OWNER, BOOT)).isEqualTo(ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED))
        assertThat(stateOf(OWNER, SYNC)).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED))
        assertThat(stateOf(OWNER, MAIN)?.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(stateOf(OWNER, DATA)?.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(result.summary.skipped).containsExactly(SkipReason.NOT_DECLARED, 1)
        assertThat(result.summary.apps).isEqualTo(1)
        assertThat(result.summary.changedPackages).containsExactly(APP)
    }

    @Test
    fun givenEntriesAlreadyInTheirState_whenImporting_thenTheyCountAsUnchanged() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT, pm = PmSetting.DISABLED), status(SYNC)))
        storage.putFile("$APP.json", rule(entry(BOOT, false, "PM"), entry(SYNC, false, "PM")))

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        assertThat(result.summary.unchanged).isEqualTo(1)
        assertThat(result.summary.applied).isEqualTo(1)
    }

    @Test
    fun givenPmEntryNotDisabled_whenImporting_thenOnlyADisabledSettingIsCleared() = runTest {
        // A backup writes state=true for DEFAULT too; clearing DEFAULT would set ENABLED for a
        // component the manifest ships disabled.
        val shippedOff = declaration("Off", SYNC.type, enabledInManifest = false)
        val disabled = declaration("Disabled", SYNC.type, enabledInManifest = false)
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(shippedOff), status(disabled, pm = PmSetting.DISABLED)))
        storage.putFile("$APP.json", rule(entry(shippedOff, true, "PM"), entry(disabled, true, "IFW_PLUS_PM")))

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        // Restores unblock, which leaves a DEFAULT setting alone and clears DISABLED to DEFAULT.
        assertThat(components.requests.map { it.goal }.distinct()).containsExactly(ControlGoal.UNBLOCK)
        assertThat(stateOf(OWNER, shippedOff)?.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(stateOf(OWNER, disabled)?.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(result.summary.unchanged).isEqualTo(1)
        assertThat(result.summary.applied).isEqualTo(1)
    }

    @Test
    fun givenWorkProfileSet_whenImportingIntoAnotherUser_thenOnlyThatUserChanges() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp(user = WORK)))
        components.sendComponents(OWNER, listOf(status(BOOT)))
        components.sendComponents(WORK, listOf(status(BOOT)))
        storage.putFile("user-11/$APP.json", rule(entry(BOOT, false, "PM")))
        storage.putFile("$APP.json", rule(entry(BOOT, true, "PM")))

        importer.import(storage, BackupSet(UserId(11)), WORK)

        assertThat(stateOf(WORK, BOOT)?.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(stateOf(OWNER, BOOT)?.pm).isEqualTo(PmSetting.DEFAULT)
        assertThat(components.requests.map { it.user }).containsExactly(WORK)
    }

    @Test
    fun givenUnknownEnumNameInOneFile_whenImporting_thenThatFileFailsAndTheOthersContinue() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp("com.other")))
        val other = declaration("Sync", SYNC.type, "com.other")
        components.sendComponents(OWNER, listOf(status(BOOT), status(other)))
        storage.putFile("$APP.json", rule(entry(BOOT, false, "ROOT")))
        storage.putFile("com.other.json", rule(entry(other, false, "PM"), packageName = "com.other"))

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        assertThat(result.summary.failed).containsExactly(FailureReason.BACKUP_UNREADABLE, 1)
        assertThat(stateOf(OWNER, other)?.pm).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenAppsNotInstalledOrSystem_whenImporting_thenTheirFilesAreSkipped() = runTest {
        installedApps.sendApps(listOf(installedApp("com.system", isSystem = true)))
        components.sendComponents(OWNER, listOf(status(declaration("Sync", SYNC.type, "com.system"))))
        storage.putFile("$APP.json", rule(entry(BOOT, false, "PM")))
        storage.putFile("com.system.json", rule(entry(declaration("Sync", SYNC.type, "com.system"), false, "PM"), packageName = "com.system"))

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        assertThat(result.summary.skipped).containsExactly(SkipReason.APP_NOT_INSTALLED, 1, SkipReason.SYSTEM_APP, 1)
        assertThat(components.requests).isEmpty()

        importer.import(storage, BackupSet.OWNER, OWNER, packageName = "com.system")
        assertThat(components.requests).hasSize(1)
    }

    @Test
    fun givenFourComponentsOfOneAppAndFilesSkippedWhole_whenImporting_thenComponentsAreCounted() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, allRunnable)
        storage.putFile("$APP.json", rule(entry(BOOT, false, "PM"), entry(SYNC, false, "PM"), entry(MAIN, false, "IFW"), entry(DATA, false, "PM")))
        storage.putFile("com.gone.json", rule(entry(declaration("A", SYNC.type, "com.gone"), false, "PM"), entry(declaration("B", SYNC.type, "com.gone"), false, "PM"), packageName = "com.gone"))
        storage.putFile("com.named.json", rule(entry(BOOT, false, "PM"), entry(SYNC, false, "PM"), entry(MAIN, false, "PM")))

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        assertThat(result.summary.applied).isEqualTo(4)
        assertThat(result.summary.apps).isEqualTo(1)
        assertThat(result.summary.skipped).containsExactly(SkipReason.APP_NOT_INSTALLED, 2, SkipReason.OTHER_APP, 3)
    }

    @Test
    fun givenRefusedComponent_whenImporting_thenItCountsAsFailedForPrivilegeAndOthersApply() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT), status(SYNC)))
        storage.putFile("$APP.json", rule(entry(BOOT, false, "PM"), entry(SYNC, false, "PM")))
        components.controlEvents = { request ->
            val outcomes = request.components.map { declaration ->
                val key = ComponentKey(declaration.id, request.user)
                if (declaration == BOOT) {
                    ComponentOutcome(key, ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED), ControlResult.FAILED, mapOf(ControlLayer.PACKAGE_MANAGER to ControlFailure.NotPermitted("protected")))
                } else {
                    ComponentOutcome(key, ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED), ControlResult.ACHIEVED, emptyMap())
                }
            }
            listOf(ControlEvent.PackageDone(APP, outcomes), ControlEvent.Finished(1, 0, 1))
        }

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        assertThat(result.summary.applied).isEqualTo(1)
        assertThat(result.summary.failed).containsExactly(FailureReason.NO_PRIVILEGE, 1)
    }

    @Test
    fun givenRelativeNamesAndBlankPackages_whenImporting_thenTheyResolveAgainstTheFilesPackage() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT)))
        storage.putFile(
            "$APP.json",
            """{"packageName": "$APP", "components": [{"packageName": "", "name": ".BootReceiver", "state": false, "type": "RECEIVER", "method": "PM"}]}""",
        )

        importer.import(storage, BackupSet.OWNER, OWNER)

        assertThat(stateOf(OWNER, BOOT)?.pm).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenFileNamingAnotherPackage_whenImporting_thenItIsNotAppliedToThatApp() = runTest {
        val other = declaration("Sync", SYNC.type, "com.other")
        installedApps.sendApps(listOf(installedApp(), installedApp("com.other")))
        components.sendComponents(OWNER, listOf(status(BOOT), status(other)))
        // A file named after one app whose content names another, in a full and a single-app restore.
        storage.putFile("$APP.json", rule(entry(other, false, "PM"), packageName = "com.other"))

        val full = importer.import(storage, BackupSet.OWNER, OWNER)
        val single = importer.import(storage, BackupSet.OWNER, OWNER, packageName = APP)

        assertThat(components.requests).isEmpty()
        assertThat(full.summary.skipped).containsExactly(SkipReason.OTHER_APP, 1)
        assertThat(single.summary.skipped).containsExactly(SkipReason.OTHER_APP, 1)
        assertThat(stateOf(OWNER, other)?.pm).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenEntryNamingAnotherPackage_whenImporting_thenOnlyThatEntryIsSkipped() = runTest {
        val other = declaration("Sync", SYNC.type, "com.other")
        installedApps.sendApps(listOf(installedApp(), installedApp("com.other")))
        components.sendComponents(OWNER, listOf(status(BOOT), status(other)))
        storage.putFile("$APP.json", rule(entry(BOOT, false, "PM"), entry(other, false, "PM")))

        val result = importer.import(storage, BackupSet.OWNER, OWNER, packageName = APP)

        assertThat(components.requests.flatMap { request -> request.components }).containsExactly(BOOT)
        assertThat(result.summary.skipped).containsExactly(SkipReason.OTHER_APP, 1)
        assertThat(stateOf(OWNER, other)?.pm).isEqualTo(PmSetting.DEFAULT)
    }

    @Test
    fun givenNoChannel_whenImporting_thenTheRunFailsForPrivilege() = runTest {
        channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)

        assertThat(importer.import(storage, BackupSet.OWNER, OWNER)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
    }

    @Test
    fun givenStorageUnavailable_whenImporting_thenTheRunFailsForStorage() = runTest {
        storage.unavailable = true

        assertThat(importer.import(storage, BackupSet.OWNER, OWNER)).isEqualTo(RuleRunResult.Failed(FailureReason.STORAGE_UNAVAILABLE))
    }

    @Test
    fun givenNewOwnerExport_whenImportedBack_thenNothingChanges() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT, pm = PmSetting.DISABLED), status(SYNC, ifw = IfwBlock.BLOCKED)))
        val exporter = BlockerRuleExporter(installedApps, components, FakePrivilegedChannel(), storeOver(FakeIfwFileSystem()), backupJson, "com.merxury.blocker.test", FakeSelectedUserRepository())
        exporter.export(storage, OWNER)

        val result = importer.import(storage, BackupSet.OWNER, OWNER)

        assertThat(result.summary.applied).isEqualTo(0)
        assertThat(result.summary.unchanged).isEqualTo(4)
        assertThat(components.components(AppKey(APP, OWNER)).first().map { it.state }).containsExactly(
            ControlState(PmSetting.DISABLED, IfwBlock.NOT_BLOCKED),
            ControlState(PmSetting.DEFAULT, IfwBlock.BLOCKED),
        )
    }

    private fun entry(declaration: ComponentDeclaration, state: Boolean, method: String) = """{"packageName": "${declaration.id.packageName}", "name": "${declaration.id.className}", "state": $state, "type": "${declaration.type}", "method": "$method"}"""

    private fun rule(vararg entries: String, packageName: String = APP) = """{"packageName": "$packageName", "components": [${entries.joinToString()}]}"""
}
