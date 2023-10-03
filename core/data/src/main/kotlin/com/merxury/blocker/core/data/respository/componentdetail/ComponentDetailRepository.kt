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

import com.merxury.blocker.core.data.respository.componentdetail.datasource.LocalComponentDetailDataSource
import com.merxury.blocker.core.data.respository.componentdetail.datasource.UserGeneratedComponentDetailDataSource
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ComponentDetailRepository @Inject constructor(
    private val localComponentDetailRepository: LocalComponentDetailDataSource,
    private val userGeneratedDataSource: UserGeneratedComponentDetailDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : IComponentDetailRepository {

    override fun hasUserGeneratedDetail(packageName: String): Flow<Boolean> =
        userGeneratedDataSource.getByPackageName(packageName)
            .map { it.isNotEmpty() }

    override fun getUserGeneratedDetail(name: String): Flow<ComponentDetail?> =
        userGeneratedDataSource.getByComponentName(name)

    override fun getLocalComponentDetail(name: String): Flow<ComponentDetail?> = flow {
        // Priority: user generated > db
        val userGeneratedData = userGeneratedDataSource.getByComponentName(name)
            .first()
        if (userGeneratedData != null) {
            emit(userGeneratedData)
            return@flow
        }
        val localData = localComponentDetailRepository.getByComponentName(name)
            .first()
        if (localData != null) {
            emit(localData)
            return@flow
        }
        emit(null)
    }
        .flowOn(ioDispatcher)

    override fun saveComponentDetail(componentDetail: ComponentDetail): Flow<Boolean> =
        userGeneratedDataSource.saveComponentData(componentDetail)

    override fun listenToComponentDetailChanges(): Flow<ComponentDetail> =
        userGeneratedDataSource.listenToComponentDetailChanges()
}
