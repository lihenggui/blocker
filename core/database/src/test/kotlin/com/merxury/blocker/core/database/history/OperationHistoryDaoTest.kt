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
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.history.Operation
import com.merxury.blocker.core.model.history.OperationEntry
import com.merxury.blocker.core.model.history.OperationSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class OperationHistoryDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        OperationHistoryDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val dao = db.operationHistoryDao()

    @After
    fun close() = db.close()

    @Test
    fun givenOperationWithEntries_whenInserted_thenEntriesRoundTripWithExactStates() = runTest {
        val states = PmSetting.entries.flatMap { pm -> IfwBlock.entries.map { ifw -> ControlState(pm, ifw) } }
        val entries = (states + states.reversed()).mapIndexed { index, state ->
            val componentIndex = index % states.size
            entry(
                userId = if (index < states.size) 0 else 10,
                component = "Service" + componentIndex.toString().padStart(2, '0'),
                before = state,
                after = states[(states.indexOf(state) + 1) % states.size],
            )
        }
        val operation = Operation(id = 0, timestamp = Instant.fromEpochMilliseconds(42), source = OperationSource.APP_DETAIL, ref = "com.example")
        val id = dao.insertOperation(operation.asEntity(), entries.reversed().map { it.asEntity() })

        assertThat(id).isGreaterThan(0)
        assertThat(dao.getEntries(id).map { it.asExternalModel() })
            .containsExactlyElementsIn(entries.map { it.copy(operationId = id) }).inOrder()
        assertThat(operation.asEntity().asExternalModel()).isEqualTo(operation)

        val storedNames = db.openHelper.readableDatabase.query(
            "SELECT before_pm, after_pm, before_ifw, after_ifw FROM operation_entry ORDER BY user_id, package_name, component_name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(List(4) { cursor.getString(it) })
            }
        }
        assertThat(storedNames).containsExactlyElementsIn(
            entries.map { listOf(it.before.pm.name, it.after.pm.name, it.before.ifw.name, it.after.ifw.name) },
        ).inOrder()
    }

    @Test
    fun givenExistingEntry_whenAppended_thenFirstBeforeAndLatestAfterAreKept() = runTest {
        val first = entry()
        val otherUser = entry(userId = 10, before = ControlState.UNKNOWN)
        val id = dao.insertOperation(operation(100), listOf(first.asEntity(), otherUser.asEntity()))
        val latest = first.copy(
            operationId = id,
            before = first.after,
            after = ControlState(PmSetting.ENABLED, IfwBlock.CONDITIONAL),
        )

        dao.insertEntries(listOf(latest.asEntity()))

        assertThat(dao.getEntries(id).map { it.asExternalModel() }).containsExactly(
            latest.copy(before = first.before),
            otherUser.copy(operationId = id),
        ).inOrder()
    }

    @Test
    fun givenRepeatedNewEntryInOneBatch_whenAppended_thenFirstBeforeAndLastAfterAreKept() = runTest {
        val id = dao.insertOperation(operation(100), emptyList())
        val first = entry().copy(operationId = id)
        val middle = first.copy(before = first.after, after = ControlState(PmSetting.ENABLED, IfwBlock.CONDITIONAL))
        val last = first.copy(before = middle.after, after = ControlState.UNKNOWN)

        dao.insertEntries(listOf(first, middle, last).map { it.asEntity() })

        assertThat(dao.getEntries(id).map { it.asExternalModel() }).containsExactly(last.copy(before = first.before))
    }

    @Test
    fun givenEntryChangedBack_whenAppended_thenUnchangedEntryIsDeleted() = runTest {
        val reverted = entry(component = "A")
        val partiallyReverted = entry(component = "B")
        val id = dao.insertOperation(operation(100), listOf(reverted.asEntity(), partiallyReverted.asEntity()))
        val stillBlocked = partiallyReverted.after.copy(pm = partiallyReverted.before.pm)

        dao.insertEntries(
            listOf(
                reverted.copy(operationId = id, before = reverted.after, after = reverted.before).asEntity(),
                partiallyReverted.copy(operationId = id, before = partiallyReverted.after, after = stillBlocked).asEntity(),
            ),
        )

        assertThat(dao.getEntries(id).map { it.asExternalModel() })
            .containsExactly(partiallyReverted.copy(operationId = id, after = stillBlocked))
    }

    @Test
    fun givenOperations_whenObserved_thenNewestFirstWithEntryCount() = runTest {
        val latest = operation(200, ref = "com.example")
        val latestId = dao.insertOperation(latest, listOf(entry().asEntity(), entry(userId = 10).asEntity()))
        val oldest = operation(100)
        val oldestId = dao.insertOperation(oldest, listOf(entry().asEntity()))
        val expectedLatest = latest.copy(id = latestId).asExternalModel().copy(entryCount = 2)
        val expectedOldest = oldest.copy(id = oldestId).asExternalModel().copy(entryCount = 1)

        dao.observeOperations().test {
            assertThat(awaitItem().map { it.asExternalModel() }).containsExactly(expectedLatest, expectedOldest).inOrder()

            val atSameTime = operation(200, OperationSource.UNDO)
            val atSameTimeId = dao.insertOperation(atSameTime, emptyList())
            assertThat(awaitItem().map { it.asExternalModel() })
                .containsExactly(atSameTime.copy(id = atSameTimeId).asExternalModel(), expectedLatest, expectedOldest).inOrder()

            dao.insertEntries(listOf(entry().copy(operationId = atSameTimeId).asEntity()))
            assertThat(awaitItem().first().entryCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUndoneAndUndoOperations_whenEntriesSinceQueried_thenOnlyEligibleOldestFirst() = runTest {
        val change = entry()
        val newest = dao.insertOperation(operation(300), listOf(change.asEntity()))
        val atCutoff = dao.insertOperation(operation(200), listOf(change.asEntity()))
        val tooOld = dao.insertOperation(operation(199), listOf(change.asEntity()))
        val undone = dao.insertOperation(operation(210), listOf(change.asEntity()))
        val sameTime = dao.insertOperation(operation(200), listOf(change.asEntity()))
        val undo = dao.insertOperation(operation(220, OperationSource.UNDO), listOf(change.asEntity()))
        dao.markUndone(listOf(undone, tooOld), undo)
        dao.insertOperation(operation(198), listOf(change.asEntity()))

        assertThat(dao.getEntriesSince(Instant.fromEpochMilliseconds(200)).map { it.operationId })
            .containsExactly(atCutoff, sameTime, newest).inOrder()
        dao.observeOperations().test {
            val operations = awaitItem().associateBy { it.id }
            assertThat(operations.getValue(undone).undoneByOperationId).isEqualTo(undo)
            assertThat(operations.getValue(tooOld).undoneByOperationId).isEqualTo(undo)
            assertThat(operations.getValue(atCutoff).undoneByOperationId).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenOldOperation_whenDeletedOlderThan_thenEntriesCascade() = runTest {
        val expiredId = dao.insertOperation(operation(100), listOf(entry().asEntity()))
        val keptId = dao.insertOperation(operation(200), listOf(entry().asEntity()))

        dao.deleteOlderThan(Instant.fromEpochMilliseconds(200))

        assertThat(dao.getEntries(expiredId)).isEmpty()
        assertThat(dao.getEntries(keptId).map { it.asExternalModel() }).containsExactly(entry().copy(operationId = keptId))
        dao.observeOperations().test {
            assertThat(awaitItem().map { it.id }).containsExactly(keptId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenBatchWithMissingOperation_whenAppended_thenAllEntriesRollBack() = runTest {
        val id = dao.insertOperation(operation(100), emptyList())
        val change = entry().asEntity()

        val result = runCatching { dao.insertEntries(listOf(change.copy(operationId = id), change.copy(operationId = id + 1))) }

        assertThat(result.exceptionOrNull()).isInstanceOf(SQLiteConstraintException::class.java)
        assertThat(dao.getEntries(id)).isEmpty()
    }

    @Test
    fun givenIdsBeyondSqliteBindLimit_whenMarkedUndone_thenEveryMatchingOperationIsUpdated() = runTest {
        val first = dao.insertOperation(operation(100), emptyList())
        val last = dao.insertOperation(operation(200), emptyList())
        val undo = dao.insertOperation(operation(300, OperationSource.UNDO), emptyList())
        val missingIds = (1_000L..2_000L).toList()

        dao.markUndone(listOf(first) + missingIds + last, undo)

        dao.observeOperations().test {
            val operations = awaitItem().associateBy { it.id }
            assertThat(operations.getValue(first).undoneByOperationId).isEqualTo(undo)
            assertThat(operations.getValue(last).undoneByOperationId).isEqualTo(undo)
            assertThat(operations.getValue(undo).undoneByOperationId).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun operation(timestamp: Long, source: OperationSource = OperationSource.APP_DETAIL, ref: String? = null) = OperationEntity(timestamp = Instant.fromEpochMilliseconds(timestamp), source = source, ref = ref)

    private fun entry(
        userId: Int = 0,
        component: String = "Service",
        before: ControlState = ControlState(PmSetting.DEFAULT, IfwBlock.NOT_BLOCKED),
        after: ControlState = ControlState(PmSetting.DISABLED, IfwBlock.BLOCKED),
    ) = OperationEntry(
        operationId = 0,
        key = ComponentKey(ComponentId("com.example", "com.example.$component"), UserId(userId)),
        type = ComponentType.SERVICE,
        before = before,
        after = after,
    )
}
