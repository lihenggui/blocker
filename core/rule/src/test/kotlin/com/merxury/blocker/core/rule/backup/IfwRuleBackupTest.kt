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
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.testing.controller.FakeIfwFileSystem
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
    private val exporter = IfwRuleExporter(store)
    private val importer = IfwRuleImporter(store, deviceUsers, installedApps, IfwXmlDeserializer(), IfwXmlSerializer())

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
    fun givenPackagesInstalledForAnotherUserOnlyOrNotAtAll_whenImporting_thenOnlyInstalledOnesAreRestored() = runTest {
        installedApps.sendApps(listOf(installedApp(user = WORK), installedApp("com.system", isSystem = true)))
        storage.putFile("ifw/$APP.xml", BLOCKER_RULE)
        storage.putFile("ifw/com.gone.xml", BLOCKER_RULE)
        storage.putFile("ifw/com.system.xml", BLOCKER_RULE)

        val result = importer.import(storage)

        assertThat(fileSystem.fileContent(APP)).isNotNull()
        assertThat(fileSystem.fileContent("com.gone")).isNull()
        assertThat(fileSystem.fileContent("com.system")).isNull()
        assertThat(result.summary.skipped).containsExactly(SkipReason.APP_NOT_INSTALLED, 1, SkipReason.SYSTEM_APP, 1)
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
