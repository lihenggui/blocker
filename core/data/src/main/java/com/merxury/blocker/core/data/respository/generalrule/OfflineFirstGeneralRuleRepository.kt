/*
 * Copyright 2023 Blocker
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

import com.merxury.blocker.core.data.model.asEntity
import com.merxury.blocker.core.database.generalrule.GeneralRuleDao
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.database.generalrule.asExternalModel
import com.merxury.blocker.core.database.generalrule.fromExternalModel
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class OfflineFirstGeneralRuleRepository @Inject constructor(
    private val generalRuleDao: GeneralRuleDao,
    private val network: BlockerNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : GeneralRuleRepository {

    override fun getGeneralRules(): Flow<List<GeneralRule>> {
        return generalRuleDao.getGeneralRuleEntities()
            .map { it.map(GeneralRuleEntity::asExternalModel) }
    }

    override fun getGeneralRule(id: Int): Flow<GeneralRule> {
        return generalRuleDao.getGeneralRuleEntity(id)
            .mapNotNull { it?.asExternalModel() }
    }

    override fun updateGeneralRule(): Flow<Result<Unit>> = flow {
        try {
            val networkRule = network.getGeneralRules()
                .map { it.asEntity() }
            compareAndUpdateCache(networkRule)
            emit(Result.Success(Unit))
        } catch (e: ClassCastException) {
            // Catch the error when the server returns a wrong format
            Timber.e(e, "Can't cast the response to GeneralRuleEntity.")
            emit(Result.Error(e))
        } catch (e: Exception) {
            // Catch general errors here
            Timber.w(e, "Failed to get the general rules from server.")
            emit(Result.Error(e))
        }
    }
        .onStart {
            Timber.v("Start fetching general online rules.")
            emit(Result.Loading)
        }
        .flowOn(ioDispatcher)

    override suspend fun saveGeneralRule(rule: GeneralRule) {
        generalRuleDao.upsertGeneralRule(rule.fromExternalModel())
    }

    override fun searchGeneralRule(keyword: String): Flow<List<GeneralRule>> {
        return generalRuleDao.searchGeneralRule(keyword)
            .map { it.map(GeneralRuleEntity::asExternalModel) }
    }

    private suspend fun compareAndUpdateCache(networkRules: List<GeneralRuleEntity>) {
        withContext(ioDispatcher) {
            val currentCache = generalRuleDao.getGeneralRuleEntities().first()
            Timber.v(
                "Compare online rules with local rules.\n" +
                    " Online rule size: ${networkRules.size}. Local DB size: ${currentCache.size}",
            )
            // Insert or update rules from the network
            networkRules.forEach { networkEntity ->
                val cachedEntity = currentCache.find { it.id == networkEntity.id }
                if (cachedEntity == networkEntity) {
                    Timber.v("Skip saving entity id: ${cachedEntity.id}")
                    return@forEach
                }
                Timber.v("Saving new rules $networkEntity to local db.")
                // Update the rule but keep the matched app count as a cache
                val cachedMatchedAppCount = cachedEntity?.matchedAppCount ?: 0
                generalRuleDao.upsertGeneralRule(
                    networkEntity.copy(matchedAppCount = cachedMatchedAppCount),
                )
            }
            // Delete outdated rules in the local cache
            // Find the rules that's not existed
            currentCache.filter { localEntity ->
                networkRules.find { it.id == localEntity.id } == null
            }.forEach { localEntity ->
                Timber.i("Rule outdated, delete it. $localEntity")
                generalRuleDao.delete(localEntity)
            }
        }
    }
}
