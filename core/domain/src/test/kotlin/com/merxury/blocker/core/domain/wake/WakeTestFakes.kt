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

package com.merxury.blocker.core.domain.wake

import android.content.pm.PackageManager
import com.merxury.blocker.core.database.record.PmBlockRecordDao
import com.merxury.blocker.core.database.record.PmBlockRecordEntity
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.privileged.wake.ManagedComponentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** PackageManager settings per component and user, changed by compare-and-set as the system does. */
internal class FakeComponentStates : ManagedComponentState {
    private val settings = mutableMapOf<Triple<String, String, UserId>, Int>()

    /** Each change that got past `proceed`, as `package/class user expected->new`. */
    val changes = mutableListOf<String>()

    fun setting(packageName: String, className: String, user: UserId = UserId.SYSTEM): Int = settings[Triple(packageName, className, user)] ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

    /** Sets a component as the user or the app would, outside the wake feature. */
    fun put(packageName: String, className: String, setting: Int, user: UserId = UserId.SYSTEM) {
        settings[Triple(packageName, className, user)] = setting
    }

    var beforeTakingLock: suspend () -> Unit = {}

    override suspend fun compareAndSet(
        packageName: String,
        className: String,
        user: UserId,
        expected: Int,
        newState: Int,
        afterConfirmed: suspend () -> Unit,
        proceed: suspend () -> Boolean,
    ): Boolean {
        beforeTakingLock()
        if (!proceed()) return false
        changes += "$packageName/$className ${user.value} $expected->$newState"
        val current = setting(packageName, className, user)
        if (current != expected && current != newState) return false
        if (current == expected) put(packageName, className, newState, user)
        afterConfirmed()
        return true
    }
}

/** The record of what the user blocked through Blocker, kept in memory. */
internal class FakePmBlockRecordDao : PmBlockRecordDao() {
    private val records = MutableStateFlow<List<PmBlockRecordEntity>>(emptyList())

    /** Records [className] of [packageName] as blocked by the user, as the app's own control does. */
    fun userBlocked(packageName: String, className: String, user: UserId = UserId.SYSTEM) {
        records.value += PmBlockRecordEntity(packageName, className, user.value, 0)
    }

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
