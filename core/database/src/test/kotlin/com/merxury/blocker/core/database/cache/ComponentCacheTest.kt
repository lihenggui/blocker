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

package com.merxury.blocker.core.database.cache

import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.UserKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ComponentCacheTest : ComponentCacheTestBase() {

    private suspend fun stateOf(packageName: String, className: String, userId: Int): ControlState = dao.observeComponent(packageName, className, userId).first()!!.asExternalModel().second

    @Test
    fun givenDeclarationWithNothingRead_whenObserving_thenBothPartsAreUnknown() = runTest {
        install("com.example")
        dao.declare("com.example", declaration("com.example", type = ComponentType.SERVICE, exported = true, enabledInManifest = false))

        val (declared, state) = dao.observeComponents("com.example", OWNER).first().single().asExternalModel()

        assertThat(declared).isEqualTo(ComponentDeclaration(ComponentId("com.example", "com.example.Main"), ComponentType.SERVICE, exported = true, enabledInManifest = false))
        assertThat(state).isEqualTo(ControlState.UNKNOWN)
    }

    @Test
    fun givenStoredUnknown_whenReading_thenItIsAStoredRowThatReadsAsUnknown() = runTest {
        install("com.example")
        dao.declare("com.example", declaration("com.example"))

        dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.Main" to PmSetting.UNKNOWN))
        dao.upsertIfwBlocks("com.example", mapOf("com.example.Main" to IfwBlock.UNKNOWN))

        val row = dao.observeComponent("com.example", "com.example.Main", OWNER).first()!!
        assertThat(row.pmSetting).isEqualTo(PmSetting.UNKNOWN)
        assertThat(row.ifwBlock).isEqualTo(IfwBlock.UNKNOWN)
        assertThat(dao.getPmSettings(OWNER, PmSetting.UNKNOWN).map { it.className }).containsExactly("com.example.Main")
    }

    @Test
    fun givenEachEnumValue_whenStoredAndRead_thenItRoundTripsByName() = runTest {
        install()
        UserKind.entries.forEachIndexed { index, kind ->
            dao.replaceUsers(listOf(user(OWNER), user(WORK), user(20 + index, kind = kind)))
            assertThat(dao.observeUsers().first().single { it.userId == 20 + index }.kind).isEqualTo(kind)
        }
        AppEnabledSetting.entries.forEach { setting ->
            dao.upsertApp(app("com.example", OWNER, enabledSetting = setting))
            assertThat(dao.getApps(OWNER).single().enabledSetting).isEqualTo(setting)
        }
        ComponentType.entries.forEach { type ->
            dao.declare("com.example", declaration("com.example", type = type))
            assertThat(dao.getDeclarations("com.example").single().type).isEqualTo(type)
        }
        PmSetting.entries.forEach { setting ->
            dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.Main" to setting))
            assertThat(stateOf("com.example", "com.example.Main", OWNER).pm).isEqualTo(setting)
        }
        IfwBlock.entries.forEach { block ->
            dao.upsertIfwBlocks("com.example", mapOf("com.example.Main" to block))
            assertThat(stateOf("com.example", "com.example.Main", OWNER).ifw).isEqualTo(block)
        }
    }

    @Test
    fun givenStoredEnums_whenReadingRawColumns_thenNamesAreStored() = runTest {
        install("com.example")
        dao.declare("com.example", declaration("com.example", type = ComponentType.RECEIVER))
        dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.Main" to PmSetting.DISABLED))
        dao.upsertIfwBlocks("com.example", mapOf("com.example.Main" to IfwBlock.CONDITIONAL))

        val raw = db.openHelper.readableDatabase.query(
            "SELECT d.type, p.setting, i.block, u.kind, a.enabled_setting FROM component_declaration d " +
                "JOIN component_pm_setting p USING (package_name, class_name) " +
                "JOIN component_ifw_block i USING (package_name, class_name) " +
                "JOIN app a ON a.package_name = d.package_name AND a.user_id = p.user_id " +
                "JOIN device_user u ON u.user_id = a.user_id",
        ).use { cursor ->
            cursor.moveToFirst()
            List(cursor.columnCount) { cursor.getString(it) }
        }

        assertThat(raw).containsExactly("RECEIVER", "DISABLED", "CONDITIONAL", "OWNER", "DEFAULT").inOrder()
    }

    @Test
    fun givenSettingsForTwoUsers_whenObserving_thenPmIsPerUserAndIfwIsSharedByAll() = runTest {
        install("com.example")
        dao.declare("com.example", declaration("com.example"))

        dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.Main" to PmSetting.DISABLED))
        dao.upsertIfwBlocks("com.example", mapOf("com.example.Main" to IfwBlock.BLOCKED))

        assertThat(stateOf("com.example", "com.example.Main", OWNER)).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED))
        assertThat(stateOf("com.example", "com.example.Main", WORK)).isEqualTo(ControlState(PmSetting.UNKNOWN, IfwBlock.BLOCKED))
        dao.upsertPmSettings("com.example", WORK, mapOf("com.example.Main" to PmSetting.ENABLED))
        assertThat(stateOf("com.example", "com.example.Main", OWNER).pm).isEqualTo(PmSetting.DISABLED)
        assertThat(stateOf("com.example", "com.example.Main", WORK).pm).isEqualTo(PmSetting.ENABLED)
    }

    @Test
    fun givenStoredDeclarations_whenReplaceDeclarations_thenRemovedLoseTheirStateAndKeptKeepIt() = runTest {
        install("com.example")
        val kept = declaration("com.example", "com.example.Kept")
        val gone = declaration("com.example", "com.example.Gone")
        dao.declare("com.example", kept, gone)
        dao.upsertPmSettings("com.example", OWNER, mapOf(kept.className to PmSetting.DISABLED, gone.className to PmSetting.DISABLED))
        dao.upsertIfwBlocks("com.example", mapOf(kept.className to IfwBlock.BLOCKED, gone.className to IfwBlock.BLOCKED))
        val changed = kept.copy(exported = true)
        val added = declaration("com.example", "com.example.Added")

        val written = dao.replaceDeclarations("com.example", listOf(changed, added), Instant.fromEpochMilliseconds(5_000))

        assertThat(written).isTrue()
        assertThat(dao.getDeclarations("com.example")).containsExactly(added, changed).inOrder()
        assertThat(dao.scanMarker("com.example")).isEqualTo(Instant.fromEpochMilliseconds(5_000))
        assertThat(stateOf("com.example", kept.className, OWNER)).isEqualTo(ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED))
        assertThat(stateOf("com.example", added.className, OWNER)).isEqualTo(ControlState.UNKNOWN)
        assertThat(dao.getPmSettings(OWNER, PmSetting.DISABLED).map { it.className }).containsExactly(kept.className)
        dao.replaceDeclarations("com.example", listOf(gone), Instant.fromEpochMilliseconds(6_000))
        assertThat(stateOf("com.example", gone.className, OWNER)).isEqualTo(ControlState.UNKNOWN)
    }

    @Test
    fun givenPackageNoUserHas_whenReplaceDeclarations_thenNothingIsWritten() = runTest {
        install()

        val written = dao.replaceDeclarations("com.absent", listOf(declaration("com.absent")), Instant.fromEpochMilliseconds(1))

        assertThat(written).isFalse()
        assertThat(dao.getDeclarations("com.absent")).isEmpty()
        assertThat(dao.scanMarker("com.absent")).isNull()
    }

    @Test
    fun givenUndeclaredClassOrMissingApp_whenUpsertingState_thenThoseEntriesAreSkipped() = runTest {
        install("com.example", userIds = listOf(OWNER))
        dao.declare("com.example", declaration("com.example"))

        dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.Main" to PmSetting.DISABLED, "com.example.Undeclared" to PmSetting.DISABLED))
        dao.upsertPmSettings("com.example", WORK, mapOf("com.example.Main" to PmSetting.DISABLED))
        dao.upsertIfwBlocks("com.example", mapOf("com.example.Undeclared" to IfwBlock.BLOCKED))
        dao.upsertIfwBlocks("com.absent", mapOf("com.absent.Main" to IfwBlock.BLOCKED))

        assertThat(dao.getPmSettings(OWNER, PmSetting.DISABLED).map { it.className }).containsExactly("com.example.Main")
        assertThat(dao.getPmSettings(WORK, PmSetting.DISABLED)).isEmpty()
        assertThat(stateOf("com.example", "com.example.Main", OWNER).ifw).isEqualTo(IfwBlock.UNKNOWN)
    }

    @Test
    fun givenIfwState_whenClearIfwBlocks_thenThatPackageReadsAsNotReadAgain() = runTest {
        install("com.example", "com.other")
        dao.declare("com.example", declaration("com.example"))
        dao.declare("com.other", declaration("com.other"))
        dao.upsertIfwBlocks("com.example", mapOf("com.example.Main" to IfwBlock.BLOCKED))
        dao.upsertIfwBlocks("com.other", mapOf("com.other.Main" to IfwBlock.NOT_BLOCKED))

        dao.clearIfwBlocks("com.example")

        assertThat(dao.observeComponent("com.example", "com.example.Main", OWNER).first()!!.ifwBlock).isNull()
        assertThat(stateOf("com.other", "com.other.Main", OWNER).ifw).isEqualTo(IfwBlock.NOT_BLOCKED)
    }

    @Test
    fun givenPackageForTwoUsers_whenDeletePackage_thenEverythingAboutItGoes() = runTest {
        install("com.example", "com.other")
        dao.declare("com.example", declaration("com.example"))
        dao.declare("com.other", declaration("com.other"))
        dao.upsertPmSettings("com.example", WORK, mapOf("com.example.Main" to PmSetting.DISABLED))
        dao.upsertIfwBlocks("com.example", mapOf("com.example.Main" to IfwBlock.BLOCKED))

        dao.deletePackage("com.example")

        assertThat(dao.getApps(OWNER).map { it.packageName }).containsExactly("com.other")
        assertThat(dao.getApps(WORK).map { it.packageName }).containsExactly("com.other")
        assertThat(dao.getDeclarations("com.example")).isEmpty()
        assertThat(dao.scanMarker("com.example")).isNull()
        assertThat(dao.getPmSettings(WORK, PmSetting.DISABLED)).isEmpty()
        assertThat(dao.getDeclarations("com.other")).isNotEmpty()
    }

    @Test
    fun givenPackagesForDifferentUsers_whenObserveAllComponents_thenOnlyThatUsersPackagesAreIncluded() = runTest {
        install("com.shared")
        dao.upsertApp(app("com.work.only", WORK))
        dao.declare("com.shared", declaration("com.shared", "com.shared.B"), declaration("com.shared", "com.shared.A"))
        dao.declare("com.work.only", declaration("com.work.only"))
        dao.upsertPmSettings("com.shared", WORK, mapOf("com.shared.A" to PmSetting.DISABLED))

        val owner = dao.observeAllComponents(OWNER).first()
        val work = dao.observeAllComponents(WORK).first()

        assertThat(owner.map { it.declaration.className }).containsExactly("com.shared.A", "com.shared.B").inOrder()
        assertThat(owner.map { it.pmSetting }).containsExactly(null, null)
        assertThat(work.map { it.declaration.className }).containsExactly("com.shared.A", "com.shared.B", "com.work.only.Main").inOrder()
        assertThat(work.first().pmSetting).isEqualTo(PmSetting.DISABLED)
    }

    @Test
    fun givenClassNames_whenSearchComponents_thenMatchesAreLiteralCaseInsensitiveAndPerUser() = runTest {
        install("com.example")
        dao.upsertApp(app("com.work.only", WORK))
        dao.declare("com.example", declaration("com.example", "com.example.Push_Receiver"), declaration("com.example", "com.example.PushXReceiver"))
        dao.declare("com.work.only", declaration("com.work.only", "com.work.only.Push_Receiver"))

        assertThat(dao.searchComponents(OWNER, "push_").first().map { it.declaration.className }).containsExactly("com.example.Push_Receiver")
        assertThat(dao.searchComponents(OWNER, "PUSH").first()).hasSize(2)
        assertThat(dao.searchComponents(WORK, "push_").first().map { it.declaration.packageName }).containsExactly("com.example", "com.work.only")
        assertThat(dao.searchComponents(OWNER, "%").first()).isEmpty()
    }

    @Test
    fun givenSettingsOfSeveralKinds_whenGetPmSettings_thenOnlyThatUserAndSettingAreReturned() = runTest {
        install("com.example")
        dao.declare("com.example", declaration("com.example", "com.example.A"), declaration("com.example", "com.example.B"))
        dao.upsertPmSettings("com.example", OWNER, mapOf("com.example.A" to PmSetting.DISABLED, "com.example.B" to PmSetting.ENABLED))
        dao.upsertPmSettings("com.example", WORK, mapOf("com.example.B" to PmSetting.DISABLED))

        assertThat(dao.getPmSettings(OWNER, PmSetting.DISABLED)).containsExactly(ComponentPmSettingEntity("com.example", "com.example.A", OWNER, PmSetting.DISABLED))
        assertThat(dao.getPmSettings(WORK, PmSetting.DISABLED)).containsExactly(ComponentPmSettingEntity("com.example", "com.example.B", WORK, PmSetting.DISABLED))
        assertThat(dao.getPmSettings(OWNER, PmSetting.DEFAULT)).isEmpty()
    }

    @Test
    fun givenThreeThousandComponents_whenWritingAndShrinking_thenEveryStatementStaysWithinBindLimits() = runTest {
        install("com.big")
        val declarations = (0 until 3_000).map { declaration("com.big", "com.big.C%04d".format(it)) }
        dao.declare("com.big", *declarations.toTypedArray())
        dao.upsertPmSettings("com.big", OWNER, declarations.associate { it.className to PmSetting.DISABLED })
        dao.upsertIfwBlocks("com.big", declarations.associate { it.className to IfwBlock.BLOCKED })
        assertThat(dao.observeComponents("com.big", OWNER).first()).hasSize(3_000)
        assertThat(dao.getPmSettings(OWNER, PmSetting.DISABLED)).hasSize(3_000)

        dao.declare("com.big", *declarations.take(1_000).toTypedArray())

        val remaining = dao.observeComponents("com.big", OWNER).first()
        assertThat(remaining).hasSize(1_000)
        assertThat(remaining.all { it.pmSetting == PmSetting.DISABLED && it.ifwBlock == IfwBlock.BLOCKED }).isTrue()
        assertThat(dao.getPmSettings(OWNER, PmSetting.DISABLED)).hasSize(1_000)
        dao.deleteApp("com.big", OWNER)
        dao.deleteApp("com.big", WORK)
        assertThat(dao.getDeclarations("com.big")).isEmpty()
    }

    @Test
    fun givenNoScan_whenReadingScanMarker_thenItIsNull() = runTest {
        install("com.example")

        assertThat(dao.scanMarker("com.example")).isNull()
        assertThat(dao.getDeclarations("com.example")).isEmpty()
    }
}
