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
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.rule.BlockerRule
import com.merxury.blocker.core.model.rule.ComponentRule
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeComponentStatusRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.repository.device.FakeSelectedUserRepository
import com.merxury.blocker.core.testing.storage.FakeBackupStorage
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockerRuleExporterTest {
    private val installedApps = FakeInstalledAppRepository()
    private val components = FakeComponentStatusRepository()
    private val channel = FakePrivilegedChannel()
    private val storage = FakeBackupStorage()
    private val exporter = BlockerRuleExporter(installedApps, components, channel, storeOver(FakeIfwFileSystem()), backupJson, "com.merxury.blocker.test", FakeSelectedUserRepository())

    // One component of each type, each in a different state of both layers.
    private val ownerState = listOf(
        status(BOOT, pm = PmSetting.DISABLED, ifw = IfwBlock.NOT_BLOCKED),
        status(SYNC, pm = PmSetting.DEFAULT, ifw = IfwBlock.BLOCKED),
        status(MAIN, pm = PmSetting.ENABLED, ifw = IfwBlock.CONDITIONAL),
        status(DATA, pm = PmSetting.DISABLED),
    )

    private fun decode(path: String): BlockerRule = backupJson.decodeFromString(BlockerRule.serializer(), checkNotNull(storage.fileContent(path)))

    @Test
    fun givenBlockerInstalledForBothUsers_whenExporting_thenOnlyTheOtherUsersCopyIsBackedUp() = runTest {
        val ownPackage = "com.merxury.blocker.test"
        installedApps.sendApps(listOf(installedApp(), installedApp(ownPackage, user = OWNER), installedApp(ownPackage, user = WORK)))
        val main = declaration("Main", BOOT.type, packageName = ownPackage)
        components.sendComponents(OWNER, listOf(status(BOOT), status(main)))
        components.sendComponents(WORK, listOf(status(main)))

        val progress = mutableListOf<Triple<String, Int, Int>>()
        val owner = exporter.export(storage, OWNER) { name, done, total -> progress += Triple(name, done, total) }
        val work = exporter.export(storage, WORK)

        assertThat(owner.summary.skipped).isEmpty()
        assertThat(owner.summary.apps).isEqualTo(1)
        assertThat(progress).containsExactly(Triple(APP, 0, 1))
        assertThat(storage.read(BackupSet(OWNER).fileOf(ownPackage))).isNull()
        assertThat(work.summary.apps).isEqualTo(1)
        assertThat(storage.read(BackupSet(WORK).fileOf(ownPackage))).isNotNull()
    }

    @Test
    fun givenBlockerIsASystemApp_whenExportingThirdPartyApps_thenOnlyOtherSystemAppsAreCounted() = runTest {
        installedApps.sendApps(
            listOf(installedApp("com.merxury.blocker.test", isSystem = true), installedApp("com.other.system", isSystem = true)),
        )

        val result = exporter.export(storage, OWNER)

        assertThat(result.summary.skipped).containsExactly(SkipReason.SYSTEM_APP, 1)
        assertThat(storage.allFiles).isEmpty()
    }

    @Test
    fun givenOwnerState_whenExporting_thenTheFileEqualsTheGoldenText() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, ownerState)

        val result = exporter.export(storage, OWNER)

        assertThat(storage.fileContent("$APP.json")).isEqualTo(resource("blocker-backup/owner_export.json").trimEnd())
        assertThat(result.summary.apps).isEqualTo(1)
        assertThat(result.summary.applied).isEqualTo(7)
        assertThat(result.summary.firewallUnavailable).isFalse()
        assertThat(channel.connectCount).isEqualTo(1)
    }

    @Test
    fun givenWorkProfile_whenExporting_thenTheFileGoesToItsUserFolderAndTheOwnerSetIsUntouched() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp(user = WORK)))
        components.sendComponents(OWNER, ownerState)
        components.sendComponents(WORK, listOf(status(BOOT), status(SYNC), status(MAIN), status(DATA)))

        exporter.export(storage, WORK)

        assertThat(storage.allFiles.keys).containsExactly("user-10/$APP.json")
        assertThat(decode("user-10/$APP.json").components.all { it.state }).isTrue()
    }

    @Test
    fun givenFirewallUnreadableAndAnExistingBackup_whenExporting_thenItsIfwEntriesAreKept() = runTest {
        // Issue #1611: an export without root used to drop the IFW entries of an existing backup.
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT, ifw = IfwBlock.UNKNOWN), status(DATA, pm = PmSetting.DISABLED)))
        components.setFirewallReadable(false)
        val existing = BlockerRule(
            packageName = APP,
            components = mutableListOf(
                ComponentRule(APP, BOOT.id.className, false, BOOT.type, ControllerType.IFW),
                ComponentRule(APP, BOOT.id.className, false, BOOT.type, ControllerType.PM),
                ComponentRule(APP, "$APP.GoneService", false, SYNC.type, ControllerType.IFW),
            ),
        )
        storage.putFile("$APP.json", backupJson.encodeToString(BlockerRule.serializer(), existing))

        val result = exporter.export(storage, OWNER)

        assertThat(decode("$APP.json").components).containsExactly(
            ComponentRule(APP, BOOT.id.className, false, BOOT.type, ControllerType.IFW),
            ComponentRule(APP, BOOT.id.className, true, BOOT.type, ControllerType.PM),
            ComponentRule(APP, DATA.id.className, false, DATA.type, ControllerType.PM),
            ComponentRule(APP, "$APP.GoneService", false, SYNC.type, ControllerType.IFW),
        ).inOrder()
        assertThat(result.summary.firewallUnavailable).isTrue()
    }

    @Test
    fun givenFirewallUnreadableAndNoBackup_whenExporting_thenOnlyPmEntriesAreWritten() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT, ifw = IfwBlock.UNKNOWN)))
        components.setFirewallReadable(false)

        exporter.export(storage, OWNER)

        assertThat(decode("$APP.json").components).containsExactly(ComponentRule(APP, BOOT.id.className, true, BOOT.type, ControllerType.PM))
    }

    @Test
    fun givenFirewallUnreadableAndAnUnparsableBackup_whenExporting_thenTheBackupIsKept() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT, ifw = IfwBlock.UNKNOWN)))
        components.setFirewallReadable(false)
        storage.putFile("$APP.json", "{ \"components\": [ { \"method\": \"ROOT\" } ] }")

        val result = exporter.export(storage, OWNER)

        assertThat(storage.writes).isEmpty()
        assertThat(result.summary.failed).containsExactly(FailureReason.BACKUP_UNREADABLE, 1)
    }

    @Test
    fun givenOneDamagedRuleFile_whenExporting_thenThatAppIsSkippedAndReported() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp("com.other")))
        components.sendComponents(OWNER, listOf(status(BOOT, ifw = IfwBlock.UNKNOWN), status(declaration("Sync", SYNC.type, "com.other"))))
        storage.putFile("$APP.json", "old backup")

        val result = exporter.export(storage, OWNER)

        assertThat(storage.fileContent("$APP.json")).isEqualTo("old backup")
        assertThat(storage.fileContent("com.other.json")).isNotNull()
        assertThat(result.summary.failed).containsExactly(FailureReason.RULES_UNREADABLE, 1)
        assertThat(result.summary.apps).isEqualTo(1)
    }

    @Test
    fun givenAppsWithoutComponentsOrSystemApps_whenExporting_thenNoFileIsWrittenForThem() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp("com.empty"), installedApp("com.system", isSystem = true)))
        components.sendComponents(OWNER, listOf(status(BOOT), status(declaration("Sync", SYNC.type, "com.system"))))

        val result = exporter.export(storage, OWNER)

        assertThat(storage.allFiles.keys).containsExactly("$APP.json")
        assertThat(result.summary.skipped).containsExactly(SkipReason.NO_COMPONENTS, 1, SkipReason.SYSTEM_APP, 1)
    }

    @Test
    fun givenSystemAppsIncludedOrOnePackage_whenExporting_thenThoseAreWritten() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp("com.system", isSystem = true)))
        components.sendComponents(OWNER, listOf(status(BOOT), status(declaration("Sync", SYNC.type, "com.system"))))

        exporter.export(storage, OWNER, packageName = "com.system")
        assertThat(storage.allFiles.keys).containsExactly("com.system.json")

        exporter.export(storage, OWNER, includeSystemApps = true)
        assertThat(storage.allFiles.keys).containsExactly("$APP.json", "com.system.json")
    }

    @Test
    fun givenNoChannel_whenExportingTheOwnUser_thenPmEntriesAreStillWritten() = runTest {
        // A backup without root keeps working, as issue #1611 asks, and says IFW was unavailable.
        channel.connectResult = ChannelState.Unavailable(UnavailableReason.DENIED)
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT, ifw = IfwBlock.UNKNOWN)))
        components.setFirewallReadable(false)

        val result = exporter.export(storage, OWNER)

        assertThat(decode("$APP.json").components).containsExactly(ComponentRule(APP, BOOT.id.className, true, BOOT.type, ControllerType.PM))
        assertThat(result.summary.firewallUnavailable).isTrue()
    }

    @Test
    fun givenUnknownPmSettings_whenExporting_thenTheAppFailsAndItsBackupIsKept() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        components.sendComponents(OWNER, listOf(status(BOOT, pm = PmSetting.UNKNOWN)))

        val result = exporter.export(storage, OWNER)

        assertThat(storage.writes).isEmpty()
        assertThat(result.summary.failed).containsExactly(FailureReason.STATE_UNREADABLE, 1)
    }

    @Test
    fun givenUserNotReachable_whenExporting_thenTheRunFailsForPrivilege() = runTest {
        installedApps.refreshFailure = ReadFailure.UserNotReachable(WORK)

        assertThat(exporter.export(storage, WORK)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
    }

    @Test
    fun givenAWriteFails_whenExporting_thenTheOtherAppsAreStillWritten() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp("com.other")))
        components.sendComponents(OWNER, listOf(status(BOOT), status(declaration("Sync", SYNC.type, "com.other"))))
        storage.unwritablePaths += "$APP.json"

        val result = exporter.export(storage, OWNER)

        assertThat(storage.allFiles.keys).containsExactly("com.other.json")
        assertThat(result.summary.failed).containsExactly(FailureReason.WRITE_FAILED, 1)
    }
}
