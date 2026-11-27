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

package com.merxury.blocker.core.testing.repository.device

import com.merxury.blocker.core.data.device.PmBlockRecords
import com.merxury.blocker.core.database.record.PmBlockRecordDao
import com.merxury.blocker.core.database.record.PmBlockRecordEntity
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.component.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePmBlockRecords {
    private val dao = FakePmBlockRecordDao()
    val repository = PmBlockRecords(dao)

    suspend fun userBlocked(packageName: String, className: String, user: UserId = UserId.SYSTEM) {
        repository.record(listOf(ComponentKey(ComponentId(packageName, className), user)))
    }

    suspend fun blockedClasses(packageName: String): List<String> = dao.getByPackage(packageName, UserId.SYSTEM.value).map { it.className }
}

/** The record of what the user blocked through Blocker, kept in memory. */
private class FakePmBlockRecordDao : PmBlockRecordDao() {
    private val records = MutableStateFlow<List<PmBlockRecordEntity>>(emptyList())

    override fun observeByUser(userId: Int): Flow<List<PmBlockRecordEntity>> = records.map { all -> all.filter { it.userId == userId } }

    override suspend fun getByUser(userId: Int) = records.value.filter { it.userId == userId }

    override suspend fun getByPackage(packageName: String, userId: Int) = records.value.filter { it.packageName == packageName && it.userId == userId }

    override suspend fun insertOrIgnore(records: List<PmBlockRecordEntity>): List<Long> = records.map { record ->
        if (this.records.value.any { it.key == record.key }) {
            -1L
        } else {
            this.records.value += record
            this.records.value.size.toLong()
        }
    }

    override suspend fun deleteByPackage(packageName: String, userId: Int) {
        records.value = records.value.filterNot { it.packageName == packageName && it.userId == userId }
    }

    override suspend fun deleteByUser(userId: Int) {
        records.value = records.value.filterNot { it.userId == userId }
    }

    override suspend fun count(): Int = records.value.size

    override suspend fun deleteOne(packageName: String, className: String, userId: Int) {
        records.value = records.value.filterNot { it.key == Triple(packageName, className, userId) }
    }

    override suspend fun deleteMatching(packageName: String, className: String, userId: Int, recordedAt: Long) {
        records.value -= PmBlockRecordEntity(packageName, className, userId, recordedAt)
    }

    private val PmBlockRecordEntity.key get() = Triple(packageName, className, userId)
}
