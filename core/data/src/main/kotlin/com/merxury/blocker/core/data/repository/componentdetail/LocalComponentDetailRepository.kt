/*
 * Copyright 2025 Blocker
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

package com.merxury.blocker.core.data.repository.componentdetail

import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.data.repository.componentdetail.datasource.CatalogComponentDetailDataSource
import com.merxury.blocker.core.data.repository.componentdetail.datasource.UserGeneratedComponentDetailDataSource
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.data.repository.userdata.observeLibDisplayLanguage
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

internal class LocalComponentDetailRepository @Inject constructor(
    private val catalogDetails: CatalogComponentDetailDataSource,
    private val userGeneratedDataSource: UserGeneratedComponentDetailDataSource,
    private val catalog: CatalogRepository,
    private val userData: UserDataRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ComponentDetailRepository {

    override val updatedComponent: Flow<ComponentDetail>
        get() = userGeneratedDataSource.eventFlow

    override fun hasUserGeneratedDetail(app: AppKey): Flow<Boolean> = userGeneratedDataSource.getByApp(app)
        .map { it.isNotEmpty() }

    override fun getLocalComponentDetails(names: Set<String>): Flow<Map<String, ComponentDetail>> {
        if (names.isEmpty()) return flowOf(emptyMap())
        val edits = updatedComponent.filter { it.name in names }.map { Unit }.onStart { emit(Unit) }
        return combine(catalog.catalogState, userData.observeLibDisplayLanguage(), edits) { _, language, _ ->
            val saved = userGeneratedDataSource.findByNames(names, language)
            val bundled = catalogDetails.findByNames(names - saved.keys, language)
            saved + bundled.associateBy(ComponentDetail::name)
        }.distinctUntilChanged().flowOn(ioDispatcher)
    }

    override fun saveComponentDetail(componentDetail: ComponentDetail): Flow<Boolean> = userGeneratedDataSource.saveComponentData(componentDetail)
}
