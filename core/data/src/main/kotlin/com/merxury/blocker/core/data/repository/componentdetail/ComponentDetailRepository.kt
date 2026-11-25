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

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ComponentDetailRepository {

    val updatedComponent: Flow<ComponentDetail>

    fun hasUserGeneratedDetail(app: AppKey): Flow<Boolean>

    fun getLocalComponentDetail(name: String): Flow<ComponentDetail?> = getLocalComponentDetails(setOf(name)).map { it[name] }

    /** Details keyed by exact component name, with saved user descriptions taking precedence. */
    fun getLocalComponentDetails(names: Set<String>): Flow<Map<String, ComponentDetail>>

    fun saveComponentDetail(componentDetail: ComponentDetail): Flow<Boolean>
}
