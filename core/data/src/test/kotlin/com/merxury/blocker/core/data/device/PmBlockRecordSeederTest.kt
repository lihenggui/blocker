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
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.database.wake.WakeProtection
import com.merxury.blocker.core.datastore.AppProperties
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.utils.currentUserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertThrows
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
    private val own = currentUserId()
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

        assertThat(seeder.seedOnce()).isTrue()
        val first = recordDao.getByUser(own.value)
        assertThat(pmBlocks.of(own)).containsExactly(SELF_OFF.key(), TRACKER.key()).inOrder()
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
        assertThat(legacyDb.exists()).isFalse()

        // A separate seeder on the next start keeps the earlier records and finishing cleanup
        // after a crash between setting the flag and deleting the file cannot seed it twice.
        createDatabase(version, DATA to true)
        val restarted = restartSeeder()
        assertThat(restarted.seedOnce()).isTrue()

        assertThat(recordDao.getByUser(own.value)).isEqualTo(first)
        assertThat(legacyDb.exists()).isFalse()
    }

    @Test
    fun givenLegacyDatabaseAndRootController_whenQuietRootChecksFirst_thenRemembersGrantOnce() = runTest {
        for (type in listOf(ControllerType.PM, ControllerType.IFW, ControllerType.IFW_PLUS_PM)) {
            preferences.setControllerType(type)
            appProperties.updateData { AppProperties.getDefaultInstance() }
            createDatabase(2, TRACKER to true)
            val memory = DataStoreRootGrantMemory(properties, restartSeeder())

            assertThat(memory.isRemembered()).isTrue()
            assertThat(appProperties.data.value.rootGrantRemembered).isTrue()
            assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()

            memory.remember(false)
            // Simulate a restart after marking the upgrade but before deleting the old file.
            createDatabase(2, TRACKER to true)
            assertThat(DataStoreRootGrantMemory(properties, restartSeeder()).isRemembered()).isFalse()
            assertThat(legacyDb.exists()).isFalse()
        }
    }

    @Test
    fun givenRootDeniedBeforeMigrationSucceeds_whenSeedingRetries_thenGrantStaysDenied() = runTest {
        preferences.setControllerType(ControllerType.PM)
        SQLiteDatabase.openOrCreateDatabase(legacyDb, null).use { it.execSQL("CREATE TABLE app_component (name TEXT)") }
        val memory = DataStoreRootGrantMemory(properties, seeder)
        assertThat(memory.isRemembered()).isFalse()
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isFalse()
        assertThat(appProperties.data.value.hasRootGrantRemembered()).isFalse()
        memory.remember(false)
        assertThat(appProperties.data.value.hasRootGrantRemembered()).isTrue()
        val denied = AppProperties.parseFrom(appProperties.data.value.toByteArray())
        appProperties.updateData { denied }

        SQLiteDatabase.openDatabase(legacyDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.execSQL("DROP TABLE app_component") }
        createDatabase(2, TRACKER to true)
        val restarted = DataStoreRootGrantMemory(properties, restartSeeder())

        assertThat(restarted.isRemembered()).isFalse()
        assertThat(appProperties.data.value.hasRootGrantRemembered()).isTrue()
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
        assertThat(pmBlocks.of(own)).containsExactly(TRACKER.key())
        assertThat(legacyDb.exists()).isFalse()
    }

    @Test
    fun givenLegacyDatabaseAndShizuku_whenSeeded_thenRootGrantStaysUnset() = runTest {
        preferences.setControllerType(ControllerType.SHIZUKU)
        createDatabase(2, TRACKER to true)

        assertThat(seeder.seedOnce()).isTrue()

        assertThat(appProperties.data.value.rootGrantRemembered).isFalse()
        assertThat(pmBlocks.of(own)).containsExactly(TRACKER.key())
    }

    @Test
    fun givenFreshInstallAndRootController_whenQuietRootChecksFirst_thenRootGrantStaysUnset() = runTest {
        preferences.setControllerType(ControllerType.PM)

        assertThat(DataStoreRootGrantMemory(properties, seeder).isRemembered()).isFalse()
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
    }

    private fun restartSeeder() = InstalledAppDbPmBlockRecordSeeder(applicationContext, Dispatchers.IO, properties, preferences, pmBlocks, wakeDb.history())

    @Test
    fun givenHotRollbackJournal_whenSeeded_thenRecoversTheCommittedBlocks() = runTest {
        createDatabase(1, TRACKER to true)
        val copy = legacyDb.resolveSibling("interrupted")
        val journal = File(legacyDb.path + "-journal")
        val copiedJournal = File(copy.path + "-journal")

        SQLiteDatabase.openDatabase(legacyDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.rawQuery("PRAGMA journal_mode=TRUNCATE", null).use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("truncate")
            }
            database.execSQL("PRAGMA synchronous=FULL")
            database.execSQL("CREATE TABLE journal_padding (value BLOB)")
            repeat(32) { database.execSQL("INSERT INTO journal_padding VALUES (zeroblob(8192))") }
            database.execSQL("PRAGMA cache_size=3")
            database.beginTransaction()
            try {
                database.execSQL("UPDATE app_component SET pm_blocked = 0")
                // Spill dirty pages so SQLite syncs a real journal before committing.
                database.execSQL("UPDATE journal_padding SET value = randomblob(8192)")
                legacyDb.copyTo(copy)
                journal.copyTo(copiedJournal)
            } finally {
                database.endTransaction()
            }
        }
        copy.copyTo(legacyDb, overwrite = true)
        copiedJournal.copyTo(journal, overwrite = true)

        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        val failure = assertThrows(SQLiteReadOnlyDatabaseException::class.java) {
            SQLiteDatabase.openDatabase(legacyDb.path, null, flags).use { database ->
                database.rawQuery("SELECT pm_blocked FROM app_component", null).use { it.moveToFirst() }
            }
        }
        assertThat(failure).hasMessageThat().contains("SQLITE_READONLY_ROLLBACK")
        assertThat(seeder.seedOnce()).isTrue()
        assertThat(pmBlocks.of(own)).containsExactly(TRACKER.key())
        assertThat(legacyDb.exists()).isFalse()
        assertThat(journal.exists()).isFalse()
    }

    @Test
    fun givenUncheckpointedWal_whenSeeded_thenIncludesBlocksFromCommittedFrames() = runTest {
        createDatabase(2, TRACKER to false)
        val copy = legacyDb.resolveSibling("uncheckpointed")
        val suffixes = listOf("", "-wal", "-shm")

        SQLiteDatabase.openDatabase(legacyDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            assertThat(database.enableWriteAheadLogging()).isTrue()
            database.rawQuery("PRAGMA wal_autocheckpoint=0", null).use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getInt(0)).isEqualTo(0)
            }
            val before = legacyDb.readBytes()
            database.execSQL("UPDATE app_component SET pm_blocked = 1")
            assertThat(legacyDb.readBytes()).isEqualTo(before)
            assertThat(File(legacyDb.path + "-wal").length()).isGreaterThan(32L)
            suffixes.forEach { File(legacyDb.path + it).copyTo(File(copy.path + it)) }
        }
        suffixes.forEach { File(copy.path + it).copyTo(File(legacyDb.path + it), overwrite = true) }

        assertThat(seeder.seedOnce()).isTrue()
        assertThat(pmBlocks.of(own)).containsExactly(TRACKER.key())
        suffixes.forEach { assertThat(File(legacyDb.path + it).exists()).isFalse() }
    }

    @Test
    fun givenAnInvalidRowAndValidBlocks_whenSeeded_thenOnlyValidBlocksAreRecorded() = runTest {
        createDatabase(2, TRACKER to true, SELF_OFF to true)
        SQLiteDatabase.openDatabase(legacyDb.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.execSQL("INSERT INTO app_component VALUES ('', 'Invalid', 0, 1, 'SERVICE', 1)")
            database.execSQL("INSERT INTO app_component VALUES ('com.example', 'bad/name', 0, 1, 'SERVICE', 1)")
        }

        assertThat(seeder.seedOnce()).isTrue()
        assertThat(pmBlocks.of(own)).containsExactly(SELF_OFF.key(), TRACKER.key())
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isTrue()
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
    fun givenSeededBlocks_whenReadingDisabledComponents_thenOnlyStillDisabledComponentsAreListed() = runTest {
        system.install(installedApp(PKG, own), TRACKER, SELF_OFF)
        createDatabase(1, TRACKER to true, SELF_OFF to true)
        system.put(own, TRACKER, PmSetting.DISABLED)
        assertThat(seeder.seedOnce()).isTrue()

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

        assertThat(seeder.seedOnce()).isFalse()
        assertThat(legacyDb.readText()).isEqualTo("damaged SQLite file")
        assertThat(appProperties.data.value.pmBlockRecordsSeeded).isFalse()
        assertThat(pmBlocks.of(own)).isEmpty()

        legacyDb.delete()
        createDatabase(2, TRACKER to true)
        assertThat(seeder.seedOnce()).isTrue()

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
