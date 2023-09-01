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

import com.merxury.blocker.core.data.Syncable
import com.merxury.blocker.core.model.data.ComponentDetail
import kotlinx.coroutines.flow.Flow

interface ComponentDetailRepository : Syncable {

    fun getUserGeneratedDetail(name: String): Flow<ComponentDetail?>

    fun getDbComponentDetail(name: String): Flow<ComponentDetail?>

    fun getComponentDetailCache(name: String): Flow<ComponentDetail?>

    suspend fun saveComponentDetail(
        componentDetail: ComponentDetail,
        userGenerated: Boolean,
    ): Boolean
}
