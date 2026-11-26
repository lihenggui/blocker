// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.database.wake

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
class WakeHistoryMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    private val schemas = File("schemas/com.merxury.blocker.core.database.wake.WakeHistoryDatabase")

    private fun schema(version: Int) = JSONObject(File(schemas, "$version.json").readText()).getJSONObject("database")

    /** Table and index statements of an exported schema, in declaration order. */
    private fun statements(database: JSONObject): List<String> {
        val entities = database.getJSONArray("entities")
        return (0 until entities.length()).map { entities.getJSONObject(it) }.flatMap { entity ->
            val indices = entity.optJSONArray("indices")
            val sql = listOf(entity.getString("createSql")) + List(indices?.length() ?: 0) { indices!!.getJSONObject(it).getString("createSql") }
            sql.map { it.replace("\${TABLE_NAME}", entity.getString("tableName")) }
        }
    }

    @Test fun givenVersionOneHistory_whenOpeningVersionThree_thenHistoryIsKeptAndDetectionTablesWork() = runTest {
        val file = folder.newFile("wake_history")
        file.delete()
        val v1 = schema(1)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            statements(v1).forEach(db::execSQL)
            val setup = v1.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO WakeRecord VALUES ('kept','boot',1,2,'statsd','ProcessStartTime',NULL,10123,NULL,'pkg',NULL,NULL,3,NULL,NULL,'{}')",
            )
            db.version = 1
        }
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java, file.path)
            .addMigrations(WAKE_HISTORY_MIGRATION_1_2, WAKE_HISTORY_MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            assertThat(room.history().recent().first().map { it.id }).containsExactly("kept")
            val detection = room.detection()
            detection.replaceDetection(5, 5, listOf(finding), listOf(verdict), 0)
            assertThat(detection.findingsInvolving("pkg", 0).first()).containsExactly(finding)
            assertThat(detection.verdictsSince(0).first()).containsExactly(verdict)
            assertThat(room.openHelper.readableDatabase.version).isEqualTo(3)
        } finally {
            room.close()
        }
    }

    @Test fun givenExportedSchema_whenComparingMigration_thenItCreatesExactlyTheNewTables() {
        val old = statements(schema(1)).toSet()
        val added = statements(schema(2)).filterNot { it in old }
        assertThat(WAKE_DETECTION_TABLES).containsExactlyElementsIn(added)
    }

    @Test fun givenVersionTwoHistoryAndDetection_whenMigrating_thenExistingRowsAndNewBlockAreReadable() = runTest {
        val file = folder.newFile("wake_history_v2")
        file.delete()
        val v2 = schema(2)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            statements(v2).forEach(db::execSQL)
            val setup = v2.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO WakeSourceStatus VALUES ('detection', 123, 'OK')")
            db.execSQL("INSERT INTO WakeExemption VALUES ('kept.pkg', NULL, 10, NULL, 123)")
            db.version = 2
        }
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), WakeHistoryDatabase::class.java, file.path)
            .addMigrations(WAKE_HISTORY_MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            val db = room.openHelper.readableDatabase
            assertThat(db.version).isEqualTo(3)
            db.query("SELECT status FROM WakeSourceStatus").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("OK")
            }
            db.query("SELECT target FROM WakeExemption").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("kept.pkg")
            }
            val block = WakeBlockEntity("path", "kept.pkg", "COMPONENT", null, null, "kept.pkg.Wake", null, 123L)
            room.blocks().upsert(listOf(block))
            assertThat(room.blocks().all()).containsExactly(block)
        } finally {
            room.close()
        }
    }

    @Test fun givenVersionThreeSchema_whenComparingMigration_thenItCreatesExactlyTheBlockTableAndIndex() {
        val old = statements(schema(2)).toSet()
        assertThat(WAKE_BLOCK_TABLES).containsExactlyElementsIn(statements(schema(3)).filterNot { it in old })
    }

    private val finding = WakeFindingDay(
        "id", 5, "pkg", "PEER_SERVICE|a|pkg|x", "PEER_SERVICE", null, "a", "x", true, "empty #3", "NONE", "JOINED", "PEER_SERVICE|root|a|", 2, 2, 0, 0, 9, 1,
    )
    private val verdict = WakeVerdictDay(5, "pkg", "SUSPICIOUS", 2, 2, 2, 2, 0, 0, 0, 3)
}
