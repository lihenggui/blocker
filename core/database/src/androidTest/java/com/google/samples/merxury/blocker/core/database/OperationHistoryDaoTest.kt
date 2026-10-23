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

package com.google.samples.merxury.blocker.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.merxury.blocker.core.database.history.OperationEntity
import com.merxury.blocker.core.database.history.OperationEntryEntity
import com.merxury.blocker.core.database.history.OperationHistoryDao
import com.merxury.blocker.core.database.history.OperationHistoryDatabase
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.OperationSource.APP_DETAIL
import com.merxury.blocker.core.model.data.OperationSource.RESET_IFW
import com.merxury.blocker.core.model.data.OperationSource.UNDO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class OperationHistoryDaoTest {
    private lateinit var dao: OperationHistoryDao
    private lateinit var db: OperationHistoryDatabase
    private val timestamp = Instant.parse("2026-10-23T12:00:00Z")
    private val entry = OperationEntryEntity(
        operationId = 0,
        packageName = "com.example.app",
        componentName = "com.example.app.Service",
        componentType = SERVICE,
        beforePm = false,
        beforeIfw = false,
        afterPm = true,
        afterIfw = false,
    )

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OperationHistoryDatabase::class.java,
        ).build()
        dao = db.operationHistoryDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun insertOperation_roundTripsTimestampEnumsAndSnapshots() = runTest {
        val id = dao.insertOperation(OperationEntity(timestamp = timestamp, source = APP_DETAIL, ref = entry.packageName), listOf(entry))
        val operation = dao.observeOperations().first().single()
        assertEquals(id, operation.id)
        assertEquals(timestamp, operation.timestamp)
        assertEquals(APP_DETAIL, operation.source)
        assertEquals(entry.packageName, operation.ref)
        assertEquals(1, operation.entryCount)
        assertEquals(listOf(entry.copy(operationId = id)), dao.getEntries(id))
    }

    @Test
    fun incrementalEntries_preserveFirstBeforeAndLatestAfter() = runTest {
        val id = dao.insertOperation(OperationEntity(timestamp = timestamp, source = APP_DETAIL), listOf(entry))
        dao.insertEntries(listOf(entry.copy(operationId = id, beforePm = true, afterIfw = true)))
        assertEquals(listOf(entry.copy(operationId = id, afterIfw = true)), dao.getEntries(id))
        assertEquals(1, dao.observeOperations().first().single().entryCount)
        dao.insertEntries(listOf(entry.copy(operationId = id, beforePm = true, beforeIfw = true, afterPm = false)))
        assertTrue(dao.getEntries(id).isEmpty())
    }

    @Test
    fun incrementalEntries_removeUnchangedEntriesOnlyFromWrittenOperations() = runTest {
        val untouched = dao.insertOperation(OperationEntity(timestamp = timestamp, source = APP_DETAIL), listOf(entry))
        val first = dao.insertOperation(OperationEntity(timestamp = timestamp, source = APP_DETAIL), listOf(entry))
        val second = dao.insertOperation(OperationEntity(timestamp = timestamp, source = APP_DETAIL), listOf(entry))
        // Seed an unchanged entry outside the flush to detect table-wide cleanup.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE operation_entry SET after_pm = before_pm WHERE operation_id = ?",
            arrayOf(untouched),
        )
        dao.insertEntries(
            listOf(
                entry.copy(operationId = first, beforePm = true, afterPm = false),
                entry.copy(operationId = second, beforePm = true, afterPm = false),
            ),
        )
        assertTrue(dao.getEntries(first).isEmpty())
        assertTrue(dao.getEntries(second).isEmpty())
        assertEquals(listOf(entry.copy(operationId = untouched, afterPm = false)), dao.getEntries(untouched))
    }

    @Test
    fun operations_orderNewestFirstIncludingLogOnlyEntries() = runTest {
        val older = dao.insertOperation(OperationEntity(timestamp = Instant.parse("2026-10-22T12:00:00Z"), source = APP_DETAIL), listOf(entry))
        val newer = dao.insertOperation(OperationEntity(timestamp = timestamp, source = RESET_IFW), emptyList())
        assertEquals(listOf(newer, older), dao.observeOperations().first().map { it.id })
        assertEquals(0, dao.observeOperations().first().first().entryCount)
    }

    @Test
    fun entriesSince_excludesUndoneAndUndoAndIncludesBoundary() = runTest {
        val older = dao.insertOperation(OperationEntity(timestamp = Instant.parse("2026-10-22T12:00:00Z"), source = APP_DETAIL), listOf(entry))
        val id = dao.insertOperation(OperationEntity(timestamp = timestamp, source = APP_DETAIL), listOf(entry))
        val undo = dao.insertOperation(OperationEntity(timestamp = timestamp, source = UNDO), listOf(entry))
        assertEquals(listOf(id), dao.getEntriesSince(timestamp).map { it.operationId })
        dao.markUndone(listOf(id), undo)
        assertTrue(dao.getEntriesSince(timestamp).isEmpty())
        assertEquals(undo, dao.observeOperations().first().find { it.id == id }?.undoneByOperationId)
        assertEquals(listOf(older), dao.getEntriesSince(Instant.parse("2026-10-22T12:00:00Z")).map { it.operationId })
        // Undone snapshots remain available for timeline expansion.
        assertEquals(1, dao.getEntries(id).size)
    }

    @Test
    fun retention_deletesOnlyOlderOperationsAndCascadesEntries() = runTest {
        val older = dao.insertOperation(OperationEntity(timestamp = Instant.parse("2026-10-22T12:00:00Z"), source = APP_DETAIL), listOf(entry))
        val retained = dao.insertOperation(OperationEntity(timestamp = timestamp, source = APP_DETAIL), listOf(entry))
        dao.deleteOlderThan(timestamp)
        assertEquals(listOf(retained), dao.observeOperations().first().map { it.id })
        assertTrue(dao.getEntries(older).isEmpty())
        assertEquals(1, dao.getEntries(retained).size)
    }
}
