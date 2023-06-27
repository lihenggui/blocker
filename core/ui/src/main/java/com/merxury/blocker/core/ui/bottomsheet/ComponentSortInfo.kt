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

package com.merxury.blocker.core.ui.bottomsheet

import com.merxury.blocker.core.model.preference.ComponentShowPriority
import com.merxury.blocker.core.model.preference.ComponentShowPriority.DISABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING

sealed interface ComponentSortInfoUiState {
    object Loading : ComponentSortInfoUiState
    data class Success(val componentSortInfo: ComponentSortInfo) : ComponentSortInfoUiState
}

data class ComponentSortInfo(
    val sorting: ComponentSorting = COMPONENT_NAME,
    val order: SortingOrder = ASCENDING,
    val priority: ComponentShowPriority = DISABLED_COMPONENTS_FIRST,
)
