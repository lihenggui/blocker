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

package com.merxury.blocker.feature.appdetail.componentdetail

import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.model.data.ComponentDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ComponentDetailViewModel @Inject constructor(
    private val componentDetailRepository: ComponentDetailRepository,
) : ViewModel()

sealed class ComponentDetailUiState {
    object Loading : ComponentDetailUiState()
    data class Success(val componentDetail: ComponentDetail) : ComponentDetailUiState()
    data class Error(val message: String) : ComponentDetailUiState()
}
