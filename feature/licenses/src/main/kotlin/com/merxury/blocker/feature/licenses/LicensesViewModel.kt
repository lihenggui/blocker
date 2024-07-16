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

package com.merxury.blocker.feature.licenses

import android.app.Application
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.licenses.LicenseItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class LicensesViewModel @Inject constructor(
    appContext: Application,
    private val analyticsHelper: AnalyticsHelper,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _licensesUiState = MutableStateFlow(LicensesUiState.Loading)
    val licensesUiState: StateFlow<LicensesUiState> = _licensesUiState.asStateFlow()
}

sealed interface LicensesUiState {
    data object Loading : LicensesUiState

    data class Success(
        val licenses: List<LicenseGroup> = emptyList(),
        val eventSink: String,
    ) : LicensesUiState
}

data class LicenseGroup(
    val id: String,
    val artifacts: List<LicenseItem>,
)
