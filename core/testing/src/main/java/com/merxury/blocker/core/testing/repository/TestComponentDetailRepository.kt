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

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

class TestComponentDetailRepository : ComponentDetailRepository {
    private val componentDetail: MutableSharedFlow<ComponentDetail> =
        MutableSharedFlow(replay = 1, onBufferOverflow = DROP_OLDEST)

    override fun getUserGeneratedDetail(name: String): Flow<ComponentDetail?> {
        return componentDetail.map {
            it.takeIf { componentDetail -> componentDetail.name == name }
        }
    }

    override fun getDbComponentDetail(name: String): Flow<ComponentDetail?> {
        return componentDetail.map {
            it.takeIf { componentDetail -> componentDetail.name == name }
        }
    }

    override fun getNetworkComponentDetail(name: String): Flow<ComponentDetail?> {
        return componentDetail.map {
            it.takeIf { componentDetail -> componentDetail.name == name }
        }
    }

    override fun getComponentDetailCache(name: String): Flow<ComponentDetail?> {
        return componentDetail.map {
            it.takeIf { componentDetail -> componentDetail.name == name }
        }
    }

    override suspend fun saveComponentDetail(
        componentDetail: ComponentDetail,
        userGenerated: Boolean,
    ): Boolean = true

    fun sendComponentDetail(componentDetail: ComponentDetail) {
        this.componentDetail.tryEmit(componentDetail)
    }
}
