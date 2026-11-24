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

import com.merxury.blocker.core.database.app.AppComponentDao
import com.merxury.blocker.core.database.app.AppComponentEntity
import com.merxury.blocker.core.model.ComponentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The old component list in memory, keyed by package and component name as its table is. */
internal class FakeAppComponentDao : AppComponentDao {
    private val rows = MutableStateFlow<Map<Pair<String, String>, AppComponentEntity>>(emptyMap())

    /** How often [getPmBlockedComponents] was read. */
    var pmBlockedReads = 0

    private fun AppComponentEntity.key() = packageName to componentName

    private fun put(entities: Collection<AppComponentEntity>) = rows.update { current -> current + entities.associateBy { it.key() } }

    override suspend fun insert(vararg appComponentEntities: AppComponentEntity) = put(appComponentEntities.toList())

    override suspend fun delete(appComponentEntity: AppComponentEntity): Int = delete(listOf(appComponentEntity))

    override suspend fun delete(entities: List<AppComponentEntity>): Int {
        val keys = entities.map { it.key() }.toSet()
        val count = rows.value.keys.count { it in keys }
        rows.update { current -> current - keys }
        return count
    }

    override suspend fun deleteByPackageName(packageName: String): Int = delete(rows.value.values.filter { it.packageName == packageName })

    override suspend fun deleteAll(): Int = delete(rows.value.values.toList())

    override fun getByPackageName(packageName: String): Flow<List<AppComponentEntity>> = rows.map { all -> all.values.filter { it.packageName == packageName } }

    override suspend fun getPmBlockedComponents(): List<AppComponentEntity> {
        pmBlockedReads++
        return rows.value.values.filter { it.pmBlocked }
    }

    override fun getByPackageNameAndComponentName(packageName: String, componentName: String): Flow<AppComponentEntity?> = rows.map { it[packageName to componentName] }

    override fun getByPackageNameAndType(packageName: String, type: ComponentType): Flow<List<AppComponentEntity>> = rows.map { all -> all.values.filter { it.packageName == packageName && it.type == type } }

    override fun getByName(name: String): Flow<AppComponentEntity?> = rows.map { all -> all.values.firstOrNull { it.componentName == name } }

    override suspend fun upsertComponentList(componentList: List<AppComponentEntity>) = put(componentList)
}
