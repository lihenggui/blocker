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

package com.merxury.blocker.core.data.respository.componentdetail

import com.merxury.blocker.core.data.Synchronizer
import com.merxury.blocker.core.data.changeListSync
import com.merxury.blocker.core.data.respository.componentdetail.datasource.DbComponentDetailDataSource
import com.merxury.blocker.core.data.respository.componentdetail.datasource.LocalComponentDetailDataSource
import com.merxury.blocker.core.data.respository.componentdetail.datasource.NetworkComponentDetailDataSource
import com.merxury.blocker.core.datastore.ChangeListVersions
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.model.NetworkChangeList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class OfflineFirstComponentDetailRepository @Inject constructor(
    private val dbDataSource: DbComponentDetailDataSource,
    private val networkDataSource: NetworkComponentDetailDataSource,
    private val userGeneratedDataSource: LocalComponentDetailDataSource,
    private val network: BlockerNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailRepository {

    override fun getUserGeneratedDetail(name: String): Flow<ComponentDetail?> =
        userGeneratedDataSource.getComponentDetail(name)

    override fun getDbComponentDetail(name: String): Flow<ComponentDetail?> =
        dbDataSource.getComponentDetail(name)

    override fun getNetworkComponentDetail(name: String): Flow<ComponentDetail?> = flow {
        val networkData = networkDataSource.getComponentDetail(name).first()
        if (networkData != null) {
            saveComponentDetail(networkData, userGenerated = false)
            emit(networkData)
            return@flow
        }
        emit(null)
    }

    override fun getComponentDetailCache(name: String): Flow<ComponentDetail?> = flow {
        // Priority: user generated > db
        val userGeneratedData = userGeneratedDataSource.getComponentDetail(name)
            .first()
        if (userGeneratedData != null) {
            emit(userGeneratedData)
            return@flow
        }
        val dbData = dbDataSource.getComponentDetail(name).first()
        if (dbData != null) {
            emit(dbData)
            return@flow
        }
        emit(null)
    }
        .flowOn(ioDispatcher)

    override suspend fun saveComponentDetail(
        componentDetail: ComponentDetail,
        userGenerated: Boolean,
    ): Boolean {
        return if (userGenerated) {
            userGeneratedDataSource.saveComponentData(componentDetail)
        } else {
            dbDataSource.saveComponentData(componentDetail)
        }
    }

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean {
        return synchronizer.changeListSync(
            versionReader = ChangeListVersions::ruleCommitId,
            changeFetcher = {
                val commitId = network.getRuleLatestCommitId().ruleCommitId
                NetworkChangeList(commitId)
            },
            versionUpdater = { latestVersion ->
                copy(ruleCommitId = latestVersion)
            },
            modelUpdater = { commitId ->
            },
        )
    }
}
