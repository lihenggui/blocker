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

package com.merxury.blocker.core.data.respository.fake

import com.merxury.blocker.core.data.Synchronizer
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.componentdetail.datasource.DbComponentDetailDataSource
import com.merxury.blocker.core.data.respository.componentdetail.datasource.LocalComponentDetailDataSource
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class FakeComponentDetailRepository @Inject constructor(
    private val userGeneratedDataSource: LocalComponentDetailDataSource,
    private val dbDataSource: DbComponentDetailDataSource,
) : ComponentDetailRepository {
    override fun getUserGeneratedDetail(name: String): Flow<ComponentDetail?> =
        userGeneratedDataSource.getComponentDetail(name)

    override fun getDbComponentDetail(name: String): Flow<ComponentDetail?> =
        dbDataSource.getComponentDetail(name)

    override fun getComponentDetailCache(name: String): Flow<ComponentDetail?> = flowOf(null)

    override suspend fun saveComponentDetail(
        componentDetail: ComponentDetail,
        userGenerated: Boolean,
    ): Boolean = true

    override suspend fun syncWith(synchronizer: Synchronizer): Boolean = true
}
