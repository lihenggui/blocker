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
import com.merxury.blocker.core.database.app.AppComponentEntity
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.datastore.copy
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val TRACKER = declaration("Tracker", ComponentType.SERVICE)
private val SELF_OFF = declaration("SelfOff", ComponentType.RECEIVER)
private val DATA = declaration("Data", ComponentType.PROVIDER)
private val OTHER_SELF_OFF = declaration("SelfOff", ComponentType.RECEIVER, packageName = OTHER_PKG)

private fun ComponentDeclaration.legacyRow(pmBlocked: Boolean) = AppComponentEntity(id.packageName, id.className, ifwBlocked = false, pmBlocked = pmBlocked, type = type, exported = true)

/** The one-time seed from the old component list for installs that upgrade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PmBlockRecordSeederTest : DeviceRepositoryTestBase() {
    private val own = ownUser()

    private suspend fun markOldListInitialized() = appProperties.updateData { it.copy { componentDatabaseInitialized = true } }

    private fun ComponentDeclaration.key() = ComponentKey(id, own)

    private fun installForOwnUser() {
        system.install(installedApp(PKG, own), TRACKER, SELF_OFF, DATA)
        system.install(installedApp(OTHER_PKG, own), OTHER_SELF_OFF)
    }

    @Test
    fun givenUpgradeWithFlaggedRows_whenSeededTwice_thenTheFlaggedRowsAreRecordedOnceForTheOwnUser() = runTest {
        markOldListInitialized()
        legacyComponents.upsertComponentList(listOf(TRACKER.legacyRow(pmBlocked = true), SELF_OFF.legacyRow(pmBlocked = true), DATA.legacyRow(pmBlocked = false)))

        seeder.seedOnce()
        val first = recordDao.getByUser(own.value)
        seeder.seedOnce()

        assertThat(pmBlocks.of(own)).containsExactly(SELF_OFF.key(), TRACKER.key()).inOrder()
        assertThat(recordDao.getByUser(own.value)).isEqualTo(first)
        assertThat(legacyComponents.pmBlockedReads).isEqualTo(1)
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
    }

    @Test
    fun givenComponentTheWakeFeatureDisabled_whenSeeded_thenItIsNotTakenForAUserBlock() = runTest {
        markOldListInitialized()
        // The old list saw both disabled; the wake feature disabled the tracker and still claims it.
        legacyComponents.upsertComponentList(listOf(TRACKER.legacyRow(pmBlocked = true), SELF_OFF.legacyRow(pmBlocked = true)))
        val claim = WakeProtection("path", PKG, PKG, TRACKER.id.className, "", "proof", false, true, "PM", previousState = 0)
        val released = claim.copy(pathId = "released", component = SELF_OFF.id.className, configured = false)
        wakeDb.history().putProtections(listOf(claim, released))

        seeder.seedOnce()

        assertThat(pmBlocks.of(own)).containsExactly(SELF_OFF.key())
    }

    @Test
    fun givenStartUpAndResetSeedAtOnce_whenBothRun_thenEachRowIsRecordedOnceAndAnEarlierRecordKeepsItsTime() = runTest {
        markOldListInitialized()
        legacyComponents.upsertComponentList(listOf(TRACKER.legacyRow(pmBlocked = true)))
        pmBlocks.record(listOf(TRACKER.key()))
        val recordedAt = recordDao.getByUser(own.value).single().recordedAt

        listOf(async { seeder.seedOnce() }, async { seeder.seedOnce() }).awaitAll()

        assertThat(recordDao.getByUser(own.value).single().recordedAt).isEqualTo(recordedAt)
        assertThat(legacyComponents.pmBlockedReads).isEqualTo(1)
    }

    @Test
    fun givenUpgradeWithoutStartUpSeed_whenSelectedForReset_thenTheSeedRunsFirstAndOnlyStillDisabledOnesAreListed() = runTest {
        installForOwnUser()
        markOldListInitialized()
        // Seen not enabled by an earlier version: one still disabled, one the system has cleared since.
        legacyComponents.upsertComponentList(listOf(TRACKER.legacyRow(pmBlocked = true), SELF_OFF.legacyRow(pmBlocked = true)))
        system.put(own, TRACKER, PmSetting.DISABLED)

        val found = components(backgroundScope).disabledByBlocker(own)

        assertThat(found).isEqualTo(ReadResult.Success(DisabledComponents(listOf(TRACKER.key()), emptyList())))
        assertThat(pmBlocks.of(own)).containsExactly(TRACKER.key())
    }

    @Test
    fun givenFreshInstall_whenTheFirstScanFillsTheOldListAfterTheStartUpSeed_thenAResetTouchesNothing() = runTest {
        installForOwnUser()
        seeder.seedOnce()
        // The first scan of the app list then records what the apps disabled themselves.
        system.put(own, SELF_OFF, PmSetting.DISABLED)
        system.put(own, OTHER_SELF_OFF, PmSetting.DISABLED)
        legacyComponents.upsertComponentList(listOf(SELF_OFF.legacyRow(pmBlocked = true), OTHER_SELF_OFF.legacyRow(pmBlocked = true), TRACKER.legacyRow(pmBlocked = false)))
        markOldListInitialized()

        val found = components(backgroundScope).disabledByBlocker(own)

        assertThat(found).isEqualTo(ReadResult.Success(DisabledComponents(emptyList(), emptyList())))
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
        assertThat(pmBlocks.of(own)).isEmpty()
        assertThat(system.settings[own to SELF_OFF.id]).isEqualTo(PmSetting.DISABLED)
        assertThat(system.settings[own to OTHER_SELF_OFF.id]).isEqualTo(PmSetting.DISABLED)
    }
}
