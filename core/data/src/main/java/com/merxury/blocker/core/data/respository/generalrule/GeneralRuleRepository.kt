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

package com.merxury.blocker.core.data.respository.generalrule

import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.result.Result
import kotlinx.coroutines.flow.Flow

interface GeneralRuleRepository {
    /**
     * Gets the general rule as a stream
     */
    fun getGeneralRules(): Flow<List<GeneralRule>>

    /**
     * Update the general rule from the backend API
     * And emit results in a flow for application to listen
     */
    fun updateGeneralRule(): Flow<Result<Unit>>
}
