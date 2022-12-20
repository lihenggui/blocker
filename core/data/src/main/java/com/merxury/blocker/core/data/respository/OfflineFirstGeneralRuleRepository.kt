/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.core.data.respository

import com.merxury.blocker.core.data.Synchronizer
import com.merxury.blocker.core.data.changeListSync
import com.merxury.blocker.core.data.model.asEntity
import com.merxury.blocker.core.database.generalrule.GeneralRuleDao
import com.merxury.blocker.core.database.generalrule.GeneralRuleEntity
import com.merxury.blocker.core.database.generalrule.asExternalModel
import com.merxury.blocker.core.datastore.ChangeListVersions
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.model.NetworkGeneralRule
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineFirstGeneralRuleRepository @Inject constructor(
    private val generalRuleDao: GeneralRuleDao,
    private val network: BlockerNetworkDataSource
) : GeneralRuleRepository {

    override fun getGeneralRules(): Flow<List<GeneralRule>> =
        generalRuleDao.getGeneralRuleEntities()
            .map { it.map(GeneralRuleEntity::asExternalModel) }

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean =
        synchronizer.changeListSync(
            versionReader = ChangeListVersions::generalRuleVersion,
            changeListFetcher = { currentVersion ->
                network.getGeneralRuleChangeList(after = currentVersion)
            },
            versionUpdater = { latestVersion ->
                copy(generalRuleVersion = latestVersion)
            },
            modelDeleter = generalRuleDao::deleteGeneralRules,
            modelUpdater = {
                val networkGeneralRules = network.getGeneralRules()
                generalRuleDao.upsertGeneralRule(
                    entities = networkGeneralRules.map(NetworkGeneralRule::asEntity)
                )
            }
        )
}
