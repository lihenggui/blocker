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

package com.merxury.blocker.core.database.generalrule

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
class GeneralRuleMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun givenVersionThreeWithCachedRules_whenOpened_thenOnlyTheMatchedCountColumnIsDropped() = runTest {
        val file = File(folder.root, "general_rule")
        val schema = JSONObject(
            File("schemas/com.merxury.blocker.core.database.generalrule.GeneralRuleDatabase/3.json").readText(),
        ).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            val table = schema.getJSONArray("entities").getJSONObject(0)
            database.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", table.getString("tableName")))
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) database.execSQL(setup.getString(i))
            database.execSQL(
                """
                INSERT INTO general_rules
                   (id, name, searchKeyword, networkSignature, contributors, matchedAppCount)
                VALUES (7, 'Tracker', '["tracker"]', '[]', '["Contributor"]', 5)
                """.trimIndent(),
            )
            database.version = 3
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = Room.databaseBuilder(context, GeneralRuleDatabase::class.java, file.path)
            .fallbackToDestructiveMigration(true)
            .allowMainThreadQueries()
            .build()
        try {
            assertThat(room.generalRuleDao().getGeneralRuleEntities().first()).containsExactly(
                GeneralRuleEntity(7, "Tracker", searchKeyword = listOf("tracker"), contributors = listOf("Contributor")),
            )
            assertThat(room.openHelper.readableDatabase.version).isEqualTo(4)
            room.openHelper.readableDatabase.query("SELECT * FROM general_rules").use { cursor ->
                assertThat(cursor.columnNames.asList()).doesNotContain("matchedAppCount")
            }
        } finally {
            room.close()
        }
    }
}
