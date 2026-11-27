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

package com.merxury.blocker.core.database.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OperationHistorySchemaTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun givenExportedVersionOneSchema_whenOpened_thenRoomValidatesTheTables() {
        val schema = File("schemas/com.merxury.blocker.core.database.history.OperationHistoryDatabase/1.json")
        val exported = JSONObject(schema.readText()).getJSONObject("database")
        val entities = exported.getJSONArray("entities")
        val tables = List(entities.length()) { entities.getJSONObject(it) }
        assertThat(exported.getInt("version")).isEqualTo(1)
        assertThat(tables.map { it.getString("tableName") }).containsExactly("operation", "operation_entry")

        val file = File(folder.root, OPERATION_HISTORY_DATABASE_NAME)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            for (table in tables) {
                val tableName = table.getString("tableName")
                database.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = table.getJSONArray("indices")
                for (i in 0 until indices.length()) {
                    database.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", tableName))
                }
            }
            // Leave out the Room identity table so opening the file validates its actual schema.
            database.version = exported.getInt("version")
        }

        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OperationHistoryDatabase::class.java, file.path)
            .addMigrations(*OPERATION_HISTORY_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val database = room.openHelper.readableDatabase
            assertThat(database.version).isEqualTo(1)
            database.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo(exported.getString("identityHash"))
            }
        } finally {
            room.close()
        }
    }
}
