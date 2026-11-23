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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PmBlockRecordDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BlockerRecordsDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.pmBlockRecordDao()

    @After
    fun close() = db.close()

    private fun record(packageName: String, className: String, userId: Int = 0, at: Long = 1) = PmBlockRecordEntity(packageName, className, userId, at)

    @Test
    fun givenRecordsOfTwoUsers_whenListedByUser_thenOnlyThatUsersAreReturnedInOrder() = runTest {
        dao.insertOrIgnore(listOf(record("b", "b.Z"), record("a", "a.Y"), record("a", "a.X"), record("a", "a.X", userId = 10)))

        assertThat(dao.getByUser(0)).containsExactly(record("a", "a.X"), record("a", "a.Y"), record("b", "b.Z")).inOrder()
        assertThat(dao.getByUser(10)).containsExactly(record("a", "a.X", userId = 10))
        assertThat(dao.getByPackage("a", 0)).containsExactly(record("a", "a.X"), record("a", "a.Y")).inOrder()
        assertThat(dao.count()).isEqualTo(4)
    }

    @Test
    fun givenStoredRecord_whenInsertedAgainLater_thenTheFirstTimeIsKept() = runTest {
        dao.insertOrIgnore(listOf(record("a", "a.X", at = 1)))

        dao.insertOrIgnore(listOf(record("a", "a.X", at = 2), record("a", "a.Y", at = 2)))

        assertThat(dao.getByUser(0)).containsExactly(record("a", "a.X", at = 1), record("a", "a.Y", at = 2)).inOrder()
    }

    @Test
    fun givenRecords_whenDeletedByKey_thenOnlyThoseKeysGoWhateverTheirTime() = runTest {
        dao.insertOrIgnore(listOf(record("a", "a.X"), record("a", "a.Y"), record("a", "a.X", userId = 10)))

        dao.delete(listOf(record("a", "a.X", at = 99), record("missing", "missing.M")))

        assertThat(dao.getByUser(0)).containsExactly(record("a", "a.Y"))
        assertThat(dao.getByUser(10)).containsExactly(record("a", "a.X", userId = 10))
    }

    @Test
    fun givenRecords_whenDeletedByPackageOrUser_thenOnlyThatPackageOfThatUserOrThatUserGoes() = runTest {
        dao.insertOrIgnore(listOf(record("a", "a.X"), record("b", "b.X"), record("a", "a.X", userId = 10), record("b", "b.X", userId = 10)))

        dao.deleteByPackage("a", 0)
        assertThat(dao.getByUser(0)).containsExactly(record("b", "b.X"))
        assertThat(dao.getByUser(10)).hasSize(2)

        dao.deleteByUser(10)
        assertThat(dao.getByUser(10)).isEmpty()
        assertThat(dao.count()).isEqualTo(1)
    }

    @Test
    fun givenObserver_whenRecordsChange_thenItSeesEachChange() = runTest {
        dao.observeByUser(0).test {
            assertThat(awaitItem()).isEmpty()
            dao.insertOrIgnore(listOf(record("a", "a.X")))
            assertThat(awaitItem()).containsExactly(record("a", "a.X"))
            dao.deleteByPackage("a", 0)
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
