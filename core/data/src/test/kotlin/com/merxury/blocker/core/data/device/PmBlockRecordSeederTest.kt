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

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private val TRACKER = declaration("Tracker", ComponentType.SERVICE)
private val SELF_OFF = declaration("SelfOff", ComponentType.RECEIVER)
private val DATA = declaration("Data", ComponentType.PROVIDER)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PmBlockRecordSeederTest : DeviceRepositoryTestBase() {
    private val own = ownUser()
    private val legacyDb = applicationContext.getDatabasePath("installed_app")
    private val schemas = File("../database/schemas/com.merxury.blocker.core.database.app.InstalledAppDatabase")

    private fun ComponentDeclaration.key() = ComponentKey(id, own)

    @Test
    fun givenVersionOneFile_whenSeeded_thenOnlyFlaggedRowsAreRecordedForTheOwnUser() = runTest {
        assertSeedFrom(1)
    }

    @Test
    fun givenVersionTwoFile_whenSeeded_thenOnlyFlaggedRowsAreRecordedForTheOwnUser() = runTest {
        assertSeedFrom(2)
    }

    private suspend fun assertSeedFrom(version: Int) {
        createDatabase(version, TRACKER to true, SELF_OFF to true, DATA to false)

        seeder.seedOnce()
        val first = recordDao.getByUser(own.value)
        assertThat(pmBlocks.of(own)).containsExactly(SELF_OFF.key(), TRACKER.key()).inOrder()
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
        assertThat(legacyDb.exists()).isFalse()

        // A separate seeder on the next start keeps the earlier records and finishing cleanup
        // after a crash between setting the flag and deleting the file cannot seed it twice.
        createDatabase(version, DATA to true)
        val restarted = InstalledAppDbPmBlockRecordSeeder(applicationContext, Dispatchers.IO, BlockerAppPropertiesDataStore(appProperties), pmBlocks, wakeDb.history())
        restarted.seedOnce()

        assertThat(recordDao.getByUser(own.value)).isEqualTo(first)
        assertThat(legacyDb.exists()).isFalse()
    }

    @Test
    fun givenComponentClaimedByWake_whenSeeded_thenItIsNotTakenForAUserBlock() = runTest {
        createDatabase(2, TRACKER to true, SELF_OFF to true)
        val claim = WakeProtection("path", PKG, PKG, TRACKER.id.className, "", "proof", false, true, "PM", previousState = 0)
        val released = claim.copy(pathId = "released", component = SELF_OFF.id.className, configured = false)
        wakeDb.history().putProtections(listOf(claim, released))

        seeder.seedOnce()

        assertThat(pmBlocks.of(own)).containsExactly(SELF_OFF.key())
    }

    @Test
    fun givenStartupAndResetSeedAtOnce_whenBothRun_thenAnEarlierRecordKeepsItsTime() = runTest {
        createDatabase(2, TRACKER to true)
        pmBlocks.record(listOf(TRACKER.key()))
        val recordedAt = recordDao.getByUser(own.value).single().recordedAt

        listOf(async { seeder.seedOnce() }, async { seeder.seedOnce() }).awaitAll()

        assertThat(recordDao.getByUser(own.value).single().recordedAt).isEqualTo(recordedAt)
        assertThat(legacyDb.exists()).isFalse()
    }

    @Test
    fun givenUpgradeWithoutStartupSeed_whenSelectedForReset_thenOnlyStillDisabledComponentsAreListed() = runTest {
        system.install(installedApp(PKG, own), TRACKER, SELF_OFF)
        createDatabase(1, TRACKER to true, SELF_OFF to true)
        system.put(own, TRACKER, PmSetting.DISABLED)

        val found = components(backgroundScope).disabledByBlocker(own)

        assertThat(found).isEqualTo(ReadResult.Success(DisabledComponents(listOf(TRACKER.key()), emptyList())))
        assertThat(pmBlocks.of(own)).containsExactly(TRACKER.key())
    }

    @Test
    fun givenNoLegacyFile_whenSeeded_thenMarksSeededAndLeavesOtherDatabases() = runTest {
        val retained = applicationContext.getDatabasePath("keep")
        SQLiteDatabase.openOrCreateDatabase(retained, null).close()

        seeder.seedOnce()

        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
        assertThat(pmBlocks.of(own)).isEmpty()
        assertThat(retained.exists()).isTrue()
    }

    @Test
    fun givenNoLegacyTable_whenSeeded_thenMarksSeededAndDeletesTheDatabase() = runTest {
        SQLiteDatabase.openOrCreateDatabase(legacyDb, null).use { it.execSQL("CREATE TABLE unrelated (value INTEGER)") }
        val journal = File(legacyDb.path + "-journal")
        journal.createNewFile()

        seeder.seedOnce()

        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
        assertThat(pmBlocks.of(own)).isEmpty()
        assertThat(legacyDb.exists()).isFalse()
        assertThat(journal.exists()).isFalse()
    }

    @Test
    fun givenUnreadableDatabase_whenSeeded_thenKeepsItAndRetriesAfterRecovery() = runTest {
        legacyDb.writeText("damaged SQLite file")

        seeder.seedOnce()

        assertThat(legacyDb.readText()).isEqualTo("damaged SQLite file")
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isFalse()
        assertThat(pmBlocks.of(own)).isEmpty()

        legacyDb.delete()
        createDatabase(2, TRACKER to true)
        seeder.seedOnce()

        assertThat(pmBlocks.of(own)).containsExactly(TRACKER.key())
        assertThat(legacyDb.exists()).isFalse()
    }

    @Test
    fun givenUnexpectedLegacyTable_whenSeeded_thenKeepsTheDatabaseForRetry() = runTest {
        SQLiteDatabase.openOrCreateDatabase(legacyDb, null).use { it.execSQL("CREATE TABLE app_component (name TEXT)") }

        seeder.seedOnce()

        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isFalse()
        assertThat(legacyDb.exists()).isTrue()
        assertThat(pmBlocks.of(own)).isEmpty()
    }

    private fun createDatabase(version: Int, vararg rows: Pair<ComponentDeclaration, Boolean>) {
        val schema = JSONObject(schemas.resolve("$version.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(legacyDb, null).use { database ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun execute(sql: String) = database.execSQL(sql.replace("\${TABLE_NAME}", entity.getString("tableName")))
                execute(entity.getString("createSql"))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) execute(indices.getJSONObject(j).getString("createSql"))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) database.execSQL(setup.getString(i))
            database.version = version
            for ((component, blocked) in rows) {
                database.execSQL(
                    "INSERT INTO app_component VALUES (?, ?, 0, ?, ?, 1)",
                    arrayOf<Any>(component.id.packageName, component.id.className, if (blocked) 1 else 0, component.type.name),
                )
            }
        }
    }
}
