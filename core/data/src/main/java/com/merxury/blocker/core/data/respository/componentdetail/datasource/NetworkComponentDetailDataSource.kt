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

package com.merxury.blocker.core.data.respository.componentdetail.datasource

import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.model.asExternalModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import javax.inject.Inject

class NetworkComponentDetailDataSource @Inject constructor(
    private val blockerNetworkDataSource: BlockerNetworkDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailDataSource {
    override fun getComponentDetail(name: String): Flow<ComponentDetail?> = flow {
        try {
            val path = name.replace(".", "/")
                .plus(".json")
            val componentDetail = blockerNetworkDataSource
                .getComponentData(path)
                ?.asExternalModel()
            emit(componentDetail)
        } catch (e: Exception) {
            Timber.d("Can't find detail for $name in the remote source.")
            emit(null)
        }
    }
        .flowOn(ioDispatcher)

    override suspend fun saveComponentData(component: ComponentDetail): Boolean {
        // Not implemented
        return false
    }
}
