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

import android.database.sqlite.SQLiteConstraintException
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
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
class UserAndAppCacheTest : ComponentCacheTestBase() {

    @Test
    fun givenDeviceUsers_whenStoredAndMapped_thenEveryFieldRoundTrips() = runTest {
        val users = listOf(
            DeviceUser(UserId(0), "Owner", UserKind.OWNER, parent = null, running = true),
            DeviceUser(UserId(10), name = null, UserKind.WORK_PROFILE, parent = null, running = false),
            DeviceUser(UserId(11), "Clone", UserKind.OTHER_PROFILE, parent = UserId(0), running = true),
        )

        dao.replaceUsers(users.map { it.asEntity() })

        assertThat(dao.observeUsers().first().map { it.asExternalModel() }).containsExactlyElementsIn(users).inOrder()
    }

    @Test
    fun givenInstalledApp_whenStoredAndMapped_thenEveryFieldRoundTrips() = runTest {
        dao.replaceUsers(listOf(user(WORK)))
        val app = InstalledApp(
            key = AppKey("com.example", UserId(WORK)),
            uid = 1_010_123,
            label = "Example",
            versionName = "2.1",
            versionCode = 21_000_000_000,
            isSystem = true,
            enabled = false,
            enabledSetting = AppEnabledSetting.DISABLED_USER,
            apkPath = null,
            firstInstallTime = Instant.fromEpochMilliseconds(123),
            lastUpdateTime = Instant.fromEpochMilliseconds(456),
            minSdk = 24,
            targetSdk = 36,
        )

        dao.upsertApp(app.asEntity())

        assertThat(dao.observeApp("com.example", WORK).first()?.asExternalModel()).isEqualTo(app)
    }

    @Test
    fun givenAppWithUnknownUid_whenStoredAndMapped_thenUidStaysUnknown() = runTest {
        dao.replaceUsers(listOf(user(WORK)))
        val app = app("com.example", WORK).copy(uid = null)

        dao.upsertApp(app)

        val stored = dao.observeApp("com.example", WORK).first()
        assertThat(stored?.uid).isNull()
        assertThat(stored?.asExternalModel()?.uid).isNull()
    }

    @Test
    fun givenAppsForTwoUsers_whenReadingOneUser_thenOnlyItsAppsAreReturned() = runTest {
        install("com.shared")
        dao.upsertApp(app("com.owner.only", OWNER))
        dao.upsertApp(app("com.work.only", WORK))

        assertThat(dao.getApps(OWNER).map { it.packageName }).containsExactly("com.owner.only", "com.shared").inOrder()
        assertThat(dao.observeApps(WORK).first().map { it.packageName }).containsExactly("com.shared", "com.work.only").inOrder()
        assertThat(dao.observeApp("com.owner.only", WORK).first()).isNull()
    }

    @Test
    fun givenUserNotStored_whenUpsertApp_thenConstraintFails() = runTest {
        val failure = runCatching { dao.upsertApp(app("com.example", WORK)) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SQLiteConstraintException::class.java)
    }

    @Test
    fun givenAppOfAnotherUser_whenReplaceApps_thenRequestIsRejected() = runTest {
        install()

        val failure = runCatching { dao.replaceApps(OWNER, listOf(app("com.example", WORK))) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun givenStoredApps_whenReplaceApps_thenChangedAreUpdatedAndMissingAreRemovedWithTheirSettings() = runTest {
        install("com.kept", "com.gone")
        dao.declare("com.kept", declaration("com.kept"))
        dao.declare("com.gone", declaration("com.gone"))
        dao.upsertPmSettings("com.kept", OWNER, mapOf("com.kept.Main" to PmSetting.DISABLED))
        dao.upsertPmSettings("com.gone", OWNER, mapOf("com.gone.Main" to PmSetting.DISABLED))
        dao.upsertPmSettings("com.gone", WORK, mapOf("com.gone.Main" to PmSetting.ENABLED))
        val updated = app("com.kept", OWNER, label = "Kept", lastUpdateTime = 3_000)

        dao.replaceApps(OWNER, listOf(updated, app("com.new", OWNER)))

        assertThat(dao.getApps(OWNER)).containsExactly(updated, app("com.new", OWNER)).inOrder()
        assertThat(dao.getApps(WORK).map { it.packageName }).containsExactly("com.gone", "com.kept")
        assertThat(dao.getPmSettings(OWNER, PmSetting.DISABLED).map { it.packageName }).containsExactly("com.kept")
        assertThat(dao.getPmSettings(WORK, PmSetting.ENABLED).map { it.packageName }).containsExactly("com.gone")
        assertThat(dao.getDeclarations("com.gone")).isNotEmpty()
    }

    @Test
    fun givenPackageOnlyOneUserHas_whenReplaceAppsDropsIt_thenItsDeclarationsAndScanMarkerAreRemoved() = runTest {
        install("com.gone", userIds = listOf(OWNER))
        dao.declare("com.gone", declaration("com.gone"))
        dao.upsertIfwBlocks("com.gone", mapOf("com.gone.Main" to IfwBlock.BLOCKED))

        dao.replaceApps(OWNER, emptyList())

        assertThat(dao.getDeclarations("com.gone")).isEmpty()
        assertThat(dao.scanMarker("com.gone")).isNull()
        assertThat(dao.observeComponents("com.gone", OWNER).first()).isEmpty()
    }

    @Test
    fun givenPackageForTwoUsers_whenDeleteAppForOne_thenOnlyThatUsersSettingsGoAndDeclarationsStay() = runTest {
        install("com.shared")
        dao.declare("com.shared", declaration("com.shared"))
        dao.upsertPmSettings("com.shared", OWNER, mapOf("com.shared.Main" to PmSetting.DISABLED))
        dao.upsertPmSettings("com.shared", WORK, mapOf("com.shared.Main" to PmSetting.DISABLED))
        dao.upsertIfwBlocks("com.shared", mapOf("com.shared.Main" to IfwBlock.BLOCKED))

        dao.deleteApp("com.shared", OWNER)

        assertThat(dao.getApps(OWNER)).isEmpty()
        assertThat(dao.getPmSettings(OWNER, PmSetting.DISABLED)).isEmpty()
        assertThat(dao.getDeclarations("com.shared")).containsExactly(declaration("com.shared"))
        assertThat(dao.scanMarker("com.shared")).isNotNull()
        assertThat(dao.observeComponents("com.shared", OWNER).first()).isEmpty()
        assertThat(dao.observeComponent("com.shared", "com.shared.Main", OWNER).first()).isNull()
        val (_, state) = dao.observeComponent("com.shared", "com.shared.Main", WORK).first()!!.asExternalModel()
        assertThat(state.pm).isEqualTo(PmSetting.DISABLED)
        assertThat(state.ifw).isEqualTo(IfwBlock.BLOCKED)
    }

    @Test
    fun givenPackageForOneUser_whenDeleteApp_thenDeclarationsScanMarkerAndIfwGo() = runTest {
        install("com.only", userIds = listOf(WORK))
        dao.declare("com.only", declaration("com.only"))
        dao.upsertIfwBlocks("com.only", mapOf("com.only.Main" to IfwBlock.BLOCKED))

        dao.deleteApp("com.only", WORK)

        assertThat(dao.getDeclarations("com.only")).isEmpty()
        assertThat(dao.scanMarker("com.only")).isNull()
        dao.upsertApp(app("com.only", WORK))
        dao.declare("com.only", declaration("com.only"))
        assertThat(dao.observeComponents("com.only", WORK).first().single().ifwBlock).isNull()
    }

    @Test
    fun givenUsers_whenReplaceUsers_thenRemovedUserLosesItsAppsSettingsAndUnsharedPackages() = runTest {
        install("com.shared")
        dao.upsertApp(app("com.work.only", WORK))
        dao.declare("com.shared", declaration("com.shared"))
        dao.declare("com.work.only", declaration("com.work.only"))
        dao.upsertPmSettings("com.shared", WORK, mapOf("com.shared.Main" to PmSetting.DISABLED))
        val renamedOwner = user(OWNER, name = "Renamed")

        dao.replaceUsers(listOf(renamedOwner))

        assertThat(dao.observeUsers().first()).containsExactly(renamedOwner)
        assertThat(dao.getApps(WORK)).isEmpty()
        assertThat(dao.getPmSettings(WORK, PmSetting.DISABLED)).isEmpty()
        assertThat(dao.getDeclarations("com.work.only")).isEmpty()
        assertThat(dao.scanMarker("com.work.only")).isNull()
        assertThat(dao.getDeclarations("com.shared")).isNotEmpty()
        assertThat(dao.getApps(OWNER).map { it.packageName }).containsExactly("com.shared")
    }

    @Test
    fun givenMoreAppsThanOneStatementCanBind_whenReplaceAppsWithNone_thenAllAreRemoved() = runTest {
        install()
        val apps = (0 until 1_200).map { app("com.example.app$it", OWNER) }
        dao.replaceApps(OWNER, apps)
        apps.forEach { dao.declare(it.packageName, declaration(it.packageName)) }

        dao.replaceApps(OWNER, emptyList())

        assertThat(dao.getApps(OWNER)).isEmpty()
        assertThat(dao.observeAllComponents(OWNER).first()).isEmpty()
        assertThat(dao.getDeclarations("com.example.app1199")).isEmpty()
    }

    @Test
    fun givenLabelsAndPackages_whenSearchApps_thenEitherMatchesIgnoringCaseForThatUserOnly() = runTest {
        install()
        dao.replaceApps(OWNER, listOf(app("com.alpha", OWNER, label = "Notes"), app("com.beta", OWNER, label = "Alpha Mail")))
        dao.upsertApp(app("com.alpha.work", WORK))

        assertThat(dao.searchApps(OWNER, "ALPHA").first().map { it.packageName }).containsExactly("com.alpha", "com.beta").inOrder()
        assertThat(dao.searchApps(OWNER, "notes").first().map { it.packageName }).containsExactly("com.alpha")
    }

    @Test
    fun givenWildcardCharacters_whenSearchApps_thenTheyMatchOnlyThemselves() = runTest {
        install()
        val apps = listOf(
            app("com.a_b", OWNER, label = "Under"),
            app("com.axb", OWNER, label = "Plain"),
            app("com.pct", OWNER, label = "100% done"),
            app("com.slash", OWNER, label = "back\\slash"),
        )
        dao.replaceApps(OWNER, apps)

        assertThat(dao.searchApps(OWNER, "a_b").first().map { it.packageName }).containsExactly("com.a_b")
        assertThat(dao.searchApps(OWNER, "%").first().map { it.packageName }).containsExactly("com.pct")
        assertThat(dao.searchApps(OWNER, "\\").first().map { it.packageName }).containsExactly("com.slash")
        assertThat(dao.searchApps(OWNER, "").first()).hasSize(4)
    }
}
