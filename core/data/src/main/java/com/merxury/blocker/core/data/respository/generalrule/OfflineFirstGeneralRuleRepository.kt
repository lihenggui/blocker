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

import com.merxury.blocker.core.data.Synchronizer
import com.merxury.blocker.core.data.model.asEntity
import com.merxury.blocker.core.database.generalrule.GeneralRuleDao
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.database.generalrule.asExternalModel
import com.merxury.blocker.core.database.generalrule.fromExternalModel
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import com.merxury.blocker.core.result.asResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineFirstGeneralRuleRepository @Inject constructor(
    private val generalRuleDao: GeneralRuleDao,
    private val network: BlockerNetworkDataSource,
) : GeneralRuleRepository {

    override fun getCacheGeneralRules(): Flow<List<GeneralRule>> =
        generalRuleDao.getGeneralRuleEntities()
            .map { it.map(GeneralRuleEntity::asExternalModel) }

    override fun getNetworkGeneralRules() = flow {
        emit(network.getGeneralRules())
    }
        .map { it.map(NetworkGeneralRule::asEntity) }
        .map { it.map(GeneralRuleEntity::asExternalModel) }
        .asResult()

    override suspend fun updateGeneralRules(list: List<GeneralRule>) {
        generalRuleDao.upsertGeneralRule(
            list.map(GeneralRule::fromExternalModel),
        )
    }

    override suspend fun clearCacheData() {
        generalRuleDao.deleteAll()
    }

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = true
}
