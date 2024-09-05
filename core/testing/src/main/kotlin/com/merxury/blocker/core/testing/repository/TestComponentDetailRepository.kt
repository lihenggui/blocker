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

package com.merxury.blocker.core.testing.repository

import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TestComponentDetailRepository : ComponentDetailRepository {
    override val updatedComponent: Flow<ComponentDetail>
        get() = flowOf(ComponentDetail(""))

    private val componentDetail: MutableSharedFlow<List<ComponentDetail>> =
        MutableSharedFlow(replay = 1, onBufferOverflow = DROP_OLDEST)

    override fun hasUserGeneratedDetail(packageName: String): Flow<Boolean> = flowOf(false)

    override fun getUserGeneratedDetail(name: String): Flow<ComponentDetail?> = componentDetail.map {
        it.find { componentDetail -> componentDetail.name == name }
    }

    override fun getLocalComponentDetail(name: String): Flow<ComponentDetail?> = componentDetail.map {
        it.find { componentDetail -> componentDetail.name == name }
    }

    override fun saveComponentDetail(componentDetail: ComponentDetail): Flow<Boolean> = flowOf(true)

    fun sendComponentDetail(componentDetail: List<ComponentDetail>) {
        this.componentDetail.tryEmit(componentDetail)
    }
}
