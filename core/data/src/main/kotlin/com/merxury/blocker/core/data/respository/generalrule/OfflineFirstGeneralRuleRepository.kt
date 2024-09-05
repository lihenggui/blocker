/*
 * Copyright 2024 Blocker
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

package com.merxury.blocker.core.data.respository.generalrule

import com.merxury.blocker.core.database.generalrule.GeneralRuleDao
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.database.generalrule.asExternalModel
import com.merxury.blocker.core.database.generalrule.fromExternalModel
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject

internal class OfflineFirstGeneralRuleRepository @Inject constructor(
    private val generalRuleDao: GeneralRuleDao,
    private val dataSource: GeneralRuleDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : GeneralRuleRepository {

    override fun getGeneralRules(): Flow<List<GeneralRule>> = generalRuleDao.getGeneralRuleEntities()
        .mapNotNull { it.map(GeneralRuleEntity::asExternalModel) }
        .map {
            // Fallback to rules in the data folder if the database is empty
            it.ifEmpty {
                dataSource.getGeneralRules().first()
            }
        }

    override fun getGeneralRule(id: Int): Flow<GeneralRule> = generalRuleDao.getGeneralRuleEntity(id)
        .mapNotNull { it?.asExternalModel() }

    override fun getRuleHash(): Flow<String> = dataSource.getGeneralRules()
        .map { it.hashCode().toString() }

    override fun updateGeneralRule(): Flow<Result<Unit>> = dataSource.getGeneralRules()
        .map { list ->
            list.map { it.fromExternalModel() }
        }
        .flatMapConcat { list ->
            compareAndUpdateCache(list)
        }
        .catch { e ->
            Timber.e(e, "Failed to get the general rules.")
            emit(Result.Error(e))
        }
        .onStart {
            Timber.v("Start fetching general online rules.")
            emit(Result.Loading)
        }
        .flowOn(ioDispatcher)

    override suspend fun saveGeneralRule(rule: GeneralRule) {
        generalRuleDao.upsertGeneralRule(rule.fromExternalModel())
    }

    override fun searchGeneralRule(keyword: String): Flow<List<GeneralRule>> = generalRuleDao.searchGeneralRule(keyword)
        .map { it.map(GeneralRuleEntity::asExternalModel) }

    private fun compareAndUpdateCache(
        latestRules: List<GeneralRuleEntity>,
    ): Flow<Result<Unit>> = flow {
        val currentCache = generalRuleDao.getGeneralRuleEntities().first()
        Timber.v(
            "Compare online rules with local rules.\n" +
                " Online rule size: ${latestRules.size}. Local DB size: ${currentCache.size}",
        )
        // Insert or update rules from the network
        latestRules.forEach { networkEntity ->
            val cachedEntity = currentCache.find { it.id == networkEntity.id }
            if (cachedEntity?.equalsInData(networkEntity) == true) {
                Timber.v("Skip saving entity id: ${cachedEntity.id}")
                return@forEach
            }
            Timber.v("Saving new rules ${networkEntity.name} to local db.")
            // Update the rule but keep the matched app count as a cache
            val cachedMatchedAppCount = cachedEntity?.matchedAppCount ?: 0
            generalRuleDao.upsertGeneralRule(
                networkEntity.copy(matchedAppCount = cachedMatchedAppCount),
            )
        }
        // Delete outdated rules in the local cache
        // Find the rules that's not existed
        currentCache.filter { localEntity ->
            latestRules.find { it.id == localEntity.id } == null
        }.forEach { localEntity ->
            Timber.v("Rule ${localEntity.name} outdated, delete it. ")
            generalRuleDao.delete(localEntity)
        }
        emit(Result.Success(Unit))
    }
        .flowOn(ioDispatcher)
}
private fun GeneralRuleEntity.equalsInData(other: GeneralRuleEntity): Boolean = this.name == other.name &&
    this.iconUrl == other.iconUrl &&
    this.company == other.company &&
    this.searchKeyword == other.searchKeyword &&
    this.useRegexSearch == other.useRegexSearch &&
    this.description == other.description &&
    this.safeToBlock == other.safeToBlock &&
    this.sideEffect == other.sideEffect &&
    this.contributors == other.contributors
