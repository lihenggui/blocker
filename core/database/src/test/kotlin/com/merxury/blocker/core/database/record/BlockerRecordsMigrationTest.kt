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

package com.merxury.blocker.core.database.record

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opens databases created from the exported schemas with the migrations the app uses, so records
 * written by an earlier version must survive. A new version adds its exported schema, its
 * migration to [BLOCKER_RECORDS_MIGRATIONS] and a test here that opens each earlier version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class BlockerRecordsMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    private val schemas = File("schemas/com.merxury.blocker.core.database.record.BlockerRecordsDatabase")

    private fun schema(version: Int) = JSONObject(File(schemas, "$version.json").readText()).getJSONObject("database")

    /** Creates a database file as version [version] of the exported schema left it, running [fill] on it. */
    private fun createVersion(version: Int, fill: (SQLiteDatabase) -> Unit): File {
        val file = folder.newFile(BLOCKER_RECORDS_DATABASE_NAME)
        file.delete()
        val database = schema(version)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = database.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices")
                for (j in 0 until (indices?.length() ?: 0)) {
                    db.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                }
            }
            val setup = database.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            fill(db)
            db.version = version
        }
        return file
    }

    private fun open(file: File) = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BlockerRecordsDatabase::class.java, file.path)
        .addMigrations(*BLOCKER_RECORDS_MIGRATIONS)
        .allowMainThreadQueries()
        .build()

    @Test
    fun givenVersionOneFileWithRecords_whenOpened_thenTheRecordsAreRead() = runTest {
        val file = createVersion(1) { it.execSQL("INSERT INTO pm_block_record VALUES ('com.example', 'com.example.Tracker', 10, 1234)") }

        val room = open(file)
        try {
            assertThat(room.pmBlockRecordDao().getByUser(10)).containsExactly(PmBlockRecordEntity("com.example", "com.example.Tracker", 10, 1234))
            assertThat(room.openHelper.readableDatabase.version).isEqualTo(1)
        } finally {
            room.close()
        }
    }

    @Test
    fun givenTheMigrations_whenListed_thenTheyLeadFromVersionOneToTheCurrentVersionWithoutGaps() {
        val current = schemas.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.max()
        val steps = BLOCKER_RECORDS_MIGRATIONS.map { it.startVersion to it.endVersion }

        val fresh = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BlockerRecordsDatabase::class.java).allowMainThreadQueries().build()
        val version = try {
            fresh.openHelper.writableDatabase.version
        } finally {
            fresh.close()
        }

        assertThat(current).isEqualTo(version)
        assertThat(steps).containsExactlyElementsIn((1 until current).map { it to it + 1 }).inOrder()
    }
}
