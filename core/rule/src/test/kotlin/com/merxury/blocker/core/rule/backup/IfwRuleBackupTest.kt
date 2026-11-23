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
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
import com.merxury.blocker.core.testing.controller.FakePrivilegedChannel
import com.merxury.blocker.core.testing.repository.device.FakeDeviceUserRepository
import com.merxury.blocker.core.testing.repository.device.FakeInstalledAppRepository
import com.merxury.blocker.core.testing.storage.FakeBackupStorage
import com.merxury.core.ifw.model.IfwFilter
import com.merxury.core.ifw.xml.IfwXmlDeserializer
import com.merxury.core.ifw.xml.IfwXmlSerializer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val BLOCKER_RULE = """<rules>
  <activity block="true" log="false">
    <component-filter name="$APP/$APP.MainActivity" />
  </activity>
</rules>"""

private const val CONDITIONAL_RULE = """<rules>
  <broadcast block="true" log="true">
    <component-filter name="$APP/$APP.BootReceiver" />
    <not>
      <sender type="system" />
    </not>
  </broadcast>
</rules>"""

// An element Blocker's parser does not know, so the file cannot be read.
private const val DAMAGED_RULE = """<rules><activity block="true" log="true"><mystery /></activity></rules>"""

/** Intent Firewall backups over a real rule store and an in-memory rule directory. */
@RunWith(RobolectricTestRunner::class)
class IfwRuleBackupTest {
    private val fileSystem = FakeIfwFileSystem()
    private val store = storeOver(fileSystem)
    private val storage = FakeBackupStorage()
    private val deviceUsers = FakeDeviceUserRepository().apply {
        systemUsers = listOf(DeviceUser(OWNER, null, UserKind.OWNER, null, true), DeviceUser(WORK, null, UserKind.WORK_PROFILE, OWNER, true))
    }
    private val installedApps = FakeInstalledAppRepository()
    private val channel = FakePrivilegedChannel()
    private val exporter = IfwRuleExporter(store, channel)
    private val importer = IfwRuleImporter(store, deviceUsers, installedApps, channel, IfwXmlDeserializer(), IfwXmlSerializer())

    private fun rulesOn(name: String) = fileSystem.fileContent(name)?.let { IfwXmlDeserializer().deserialize(it) }

    @Test
    fun givenRuleFiles_whenExporting_thenEachIsCopiedAsStoredWithoutTheWakeFeatures() = runTest {
        fileSystem.putFile(APP, BLOCKER_RULE)
        fileSystem.putFile("com.broken", DAMAGED_RULE)
        fileSystem.putFile("my-rules", CONDITIONAL_RULE)
        fileSystem.putFile("blocker-managed-wake", BLOCKER_RULE)

        val result = exporter.export(storage)

        assertThat(storage.allFiles).containsExactly(
            "ifw/$APP.xml",
            BLOCKER_RULE,
            "ifw/com.broken.xml",
            DAMAGED_RULE,
            "ifw/my-rules.xml",
            CONDITIONAL_RULE,
        )
        assertThat(result.summary.apps).isEqualTo(3)
    }

    @Test
    fun givenOnePackage_whenExporting_thenOnlyItsFileIsCopiedAndAMissingOneIsNoFailure() = runTest {
        fileSystem.putFile(APP, BLOCKER_RULE)
        fileSystem.putFile("com.other", BLOCKER_RULE)

        assertThat(exporter.export(storage, packageName = APP).summary.apps).isEqualTo(1)
        assertThat(exporter.export(storage, packageName = "com.missing").summary.failedCount).isEqualTo(0)
        assertThat(storage.allFiles.keys).containsExactly("ifw/$APP.xml")
    }

    @Test
    fun givenNoAccess_whenExporting_thenTheRunFailsForPrivilege() = runTest {
        fileSystem.noAccess = true

        assertThat(exporter.export(storage)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
    }

    @Test
    fun givenPlainOldBackupAndBlockersRuleOnTheDevice_whenImporting_thenOneRuleHoldsBothComponents() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        fileSystem.putFile(APP, BLOCKER_RULE)
        storage.putFile(
            "ifw/$APP.xml",
            """<rules><activity block="true" log="false"><component-filter name="$APP/$APP.SettingsActivity" /><component-filter name="$APP/.MainActivity" /></activity></rules>""",
        )

        val result = importer.import(storage)

        val rules = checkNotNull(rulesOn(APP)).rules
        assertThat(rules).hasSize(1)
        assertThat(rules.single().filters).containsExactly(
            IfwFilter.ComponentFilter("$APP/$APP.MainActivity"),
            IfwFilter.ComponentFilter("$APP/$APP.SettingsActivity"),
        ).inOrder()
        assertThat(result.summary.apps).isEqualTo(1)
        assertThat(result.summary.changedPackages).containsExactly(APP)
    }

    @Test
    fun givenConditionalBackup_whenImporting_thenTheRuleIsAddedWithItsConditionAndOnlyOnce() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        fileSystem.putFile(APP, BLOCKER_RULE)
        storage.putFile("ifw/$APP.xml", CONDITIONAL_RULE)

        importer.import(storage)
        val second = importer.import(storage)

        val rules = checkNotNull(rulesOn(APP))
        assertThat(rules.rules).hasSize(2)
        assertThat(rules.rules).contains(IfwXmlDeserializer().deserialize(CONDITIONAL_RULE).rules.single())
        assertThat(second.summary.unchanged).isEqualTo(1)
        assertThat(second.summary.applied).isEqualTo(0)
        assertThat(fileSystem.changeCount).isEqualTo(1)
    }

    @Test
    fun givenBlockFalseBackup_whenImporting_thenTheAllowRuleIsKept() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        storage.putFile("ifw/$APP.xml", """<rules><service block="false" log="false"><component-filter name="$APP/$APP.SyncService" /></service></rules>""")

        importer.import(storage)

        assertThat(checkNotNull(rulesOn(APP)).rules.single().block).isFalse()
    }

    @Test
    fun givenDamagedFileOnTheDevice_whenImporting_thenThatPackageFailsAndIsNotOverwritten() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        fileSystem.putFile(APP, DAMAGED_RULE)
        storage.putFile("ifw/$APP.xml", BLOCKER_RULE)

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent(APP)).isEqualTo(DAMAGED_RULE)
        assertThat(result.summary.failed).containsExactly(FailureReason.RULES_UNREADABLE, 1)
    }

    @Test
    fun givenUnparsableBackup_whenImporting_thenItIsSkippedAndTheOthersAreRestored() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp("com.other")))
        storage.putFile("ifw/$APP.xml", DAMAGED_RULE)
        storage.putFile("ifw/com.other.xml", BLOCKER_RULE)

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent(APP)).isNull()
        assertThat(fileSystem.fileContent("com.other")).isNotNull()
        assertThat(result.summary.failed).containsExactly(FailureReason.BACKUP_UNREADABLE, 1)
    }

    @Test
    fun givenAppInstalledForAnotherUserOnlyAndSystemApp_whenImporting_thenTheFirstIsMergedAndTheSystemOneLeft() = runTest {
        installedApps.sendApps(listOf(installedApp(user = WORK), installedApp("com.system", isSystem = true)))
        storage.putFile("ifw/$APP.xml", BLOCKER_RULE)
        storage.putFile("ifw/com.system.xml", BLOCKER_RULE)

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent(APP)).isNotNull()
        assertThat(fileSystem.fileContent("com.system")).isNull()
        assertThat(result.summary.skipped).containsExactly(SkipReason.SYSTEM_APP, 1)
    }

    @Test
    fun givenChannelNotConnected_whenImporting_thenItConnectsBeforeReadingTheUsersAndRestoresAProfileOnlyApp() = runTest {
        // Before a connect the system shows only the user Blocker runs in.
        val channel = FakePrivilegedChannel(ChannelState.Unavailable(UnavailableReason.NOT_CHECKED))
        val users = object : DeviceUserRepository by deviceUsers {
            override suspend fun refresh(): ReadResult<List<DeviceUser>> = if (channel.state.value is ChannelState.Ready) {
                deviceUsers.refresh()
            } else {
                ReadResult.Success(listOf(DeviceUser(OWNER, null, UserKind.OWNER, null, true)))
            }
        }
        installedApps.sendApps(listOf(installedApp(user = WORK)))
        storage.putFile("ifw/$APP.xml", BLOCKER_RULE)

        val result = IfwRuleImporter(store, users, installedApps, channel, IfwXmlDeserializer(), IfwXmlSerializer()).import(storage)

        assertThat(channel.connectCount).isEqualTo(1)
        assertThat(result.summary.apps).isEqualTo(1)
        assertThat(rulesOn(APP)).isEqualTo(IfwXmlDeserializer().deserialize(BLOCKER_RULE))
    }

    @Test
    fun givenNoChannelOrTheShell_whenImportingOrExporting_thenTheRunFailsForPrivilege() = runTest {
        storage.putFile("ifw/$APP.xml", BLOCKER_RULE)
        for (state in listOf(ChannelState.Unavailable(UnavailableReason.DENIED), ChannelState.Ready(PrivilegedIdentity.SHELL))) {
            channel.connectResult = state

            assertThat(importer.import(storage)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
            assertThat(exporter.export(storage)).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
        }
        assertThat(fileSystem.changeCount).isEqualTo(0)
    }

    @Test
    fun givenAUsersAppsUnreadable_whenImporting_thenTheRunFailsInsteadOfMissingItsApps() = runTest {
        installedApps.sendApps(listOf(installedApp(user = WORK)))
        val users = object : DeviceUserRepository by deviceUsers {}
        val apps = object : InstalledAppRepository by installedApps {
            override suspend fun refresh(user: UserId): ReadResult<Unit> = if (user == WORK) ReadResult.Failure(ReadFailure.UserNotReachable(WORK)) else installedApps.refresh(user)
        }
        storage.putFile("ifw/$APP.xml", BLOCKER_RULE)

        val result = IfwRuleImporter(store, users, apps, channel, IfwXmlDeserializer(), IfwXmlSerializer()).import(storage)

        assertThat(result).isEqualTo(RuleRunResult.Failed(FailureReason.NO_PRIVILEGE))
        assertThat(fileSystem.fileContent(APP)).isNull()
    }

    @Test
    fun givenFilesNotOfAnInstalledApp_whenImporting_thenEachIsRestoredWholeOnlyWhereNoneExists() = runTest {
        // Names that look like packages are not taken for apps: only installed apps are merged.
        storage.putFile("ifw/privacy.rules.xml", CONDITIONAL_RULE)
        storage.putFile("ifw/my-rules.xml", BLOCKER_RULE)
        storage.putFile("ifw/com.gone.xml", BLOCKER_RULE)
        storage.putFile("ifw/com.kept.xml", BLOCKER_RULE)
        fileSystem.putFile("com.kept", "on the device")

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent("privacy.rules")).isEqualTo(CONDITIONAL_RULE)
        assertThat(fileSystem.fileContent("my-rules")).isEqualTo(BLOCKER_RULE)
        assertThat(fileSystem.fileContent("com.gone")).isEqualTo(BLOCKER_RULE)
        assertThat(fileSystem.fileContent("com.kept")).isEqualTo("on the device")
        assertThat(result.summary.apps).isEqualTo(3)
        assertThat(result.summary.skipped).containsExactly(SkipReason.FILE_NOT_RESTORED, 1)
    }

    @Test
    fun givenDamagedBackupOfAnAppNotInstalled_whenImporting_thenItIsNeverWritten() = runTest {
        // Written now, it would keep a good backup from being merged once the app is installed. A
        // file restored whole is only checked to be a complete <rules> document.
        storage.putFile("ifw/com.gone.xml", DAMAGED_RULE.dropLast("</rules>".length))
        storage.putFile("ifw/my-rules.xml", "not xml at all")

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent("com.gone")).isNull()
        assertThat(fileSystem.fileContent("my-rules")).isNull()
        assertThat(fileSystem.changeCount).isEqualTo(0)
        assertThat(result.summary.failed).containsExactly(FailureReason.BACKUP_UNREADABLE, 2)
    }

    @Test
    fun givenOwnFileWithAFilterTheModelCannotExpress_whenImporting_thenItIsRestoredAsWritten() = runTest {
        // Android reads a matcher-less <action /> as "has an action"; Blocker's model has no such filter.
        val existenceCheck = "<rules>\n  <broadcast block=\"true\" log=\"false\">\n    <action />\n  </broadcast>\n</rules>\n"
        storage.putFile("ifw/my-rules.xml", existenceCheck)

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent("my-rules")).isEqualTo(existenceCheck)
        assertThat(result.summary.applied).isEqualTo(1)
        assertThat(result.summary.failed).isEmpty()
    }

    @Test
    fun givenOwnFilesTruncatedOrWithAnotherRoot_whenImporting_thenNeitherIsWrittenAndBothCount() = runTest {
        storage.putFile("ifw/cut-rules.xml", "<rules>\n  <broadcast block=\"true\" log=\"false\">\n    <action />\n")
        storage.putFile("ifw/other-root.xml", "<intent-firewall>\n  <broadcast block=\"true\" log=\"false\" />\n</intent-firewall>\n")

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent("cut-rules")).isNull()
        assertThat(fileSystem.fileContent("other-root")).isNull()
        assertThat(fileSystem.changeCount).isEqualTo(0)
        assertThat(result.summary.failed).containsExactly(FailureReason.BACKUP_UNREADABLE, 2)
    }

    @Test
    fun givenOneAppAsked_whenImporting_thenOnlyRulesActingOnThatAppAloneAreMerged() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        storage.putFile(
            "ifw/$APP.xml",
            """<rules>
              <activity block="true" log="false">
                <component-filter name="$APP/$APP.MainActivity" />
              </activity>
              <service block="true" log="false">
                <component-filter name="$APP/$APP.SyncService" />
                <component-filter name="com.victim/com.victim.Sync" />
              </service>
              <broadcast block="true" log="true">
                <action equals="android.intent.action.BOOT_COMPLETED" />
              </broadcast>
              <activity block="true" log="false">
                <intent-filter>
                  <action name="android.intent.action.VIEW" />
                </intent-filter>
                <component-filter name="$APP/$APP.ViewActivity" />
              </activity>
            </rules>""",
        )

        val result = importer.import(storage, packageName = APP)

        assertThat(rulesOn(APP)).isEqualTo(IfwXmlDeserializer().deserialize(BLOCKER_RULE))
        assertThat(result.summary.skipped).containsExactly(SkipReason.OTHER_APP, 3)
        assertThat(result.summary.applied).isEqualTo(1)
    }

    @Test
    fun givenUsersOwnFileBackup_whenImporting_thenItIsRestoredAsWrittenOnlyWhereNoneExists() = runTest {
        storage.putFile("ifw/my-rules.xml", CONDITIONAL_RULE)
        storage.putFile("ifw/kept-rules.xml", CONDITIONAL_RULE)
        fileSystem.putFile("kept-rules", "on the device")

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent("my-rules")).isEqualTo(CONDITIONAL_RULE)
        assertThat(fileSystem.fileContent("kept-rules")).isEqualTo("on the device")
        assertThat(result.summary.skipped).containsExactly(SkipReason.FILE_NOT_RESTORED, 1)
        assertThat(result.summary.apps).isEqualTo(1)
        assertThat(channel.connectCount).isEqualTo(1)
    }

    @Test
    fun givenOnePackageAsked_whenImporting_thenOtherFilesAndUsersOwnFilesAreLeftAlone() = runTest {
        installedApps.sendApps(listOf(installedApp(), installedApp("com.other")))
        storage.putFile("ifw/$APP.xml", BLOCKER_RULE)
        storage.putFile("ifw/com.other.xml", BLOCKER_RULE)
        storage.putFile("ifw/my-rules.xml", CONDITIONAL_RULE)

        importer.import(storage, packageName = APP)

        assertThat(fileSystem.fileContent(APP)).isNotNull()
        assertThat(fileSystem.fileContent("com.other")).isNull()
        assertThat(fileSystem.fileContent("my-rules")).isNull()
    }

    @Test
    fun givenExportedFiles_whenImportedIntoAnEmptyDevice_thenTheSameRulesResult() = runTest {
        installedApps.sendApps(listOf(installedApp()))
        fileSystem.putFile(APP, CONDITIONAL_RULE)
        exporter.export(storage)
        fileSystem.deleteRules(APP)

        importer.import(storage)

        assertThat(rulesOn(APP)).isEqualTo(IfwXmlDeserializer().deserialize(CONDITIONAL_RULE))
    }
}
