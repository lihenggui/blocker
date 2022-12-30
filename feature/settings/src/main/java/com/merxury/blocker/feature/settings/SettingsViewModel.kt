/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.feature.settings

import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.ui.data.ControllerType
import com.merxury.blocker.core.ui.data.ControllerType.IFW
import com.merxury.blocker.core.ui.data.OnlineRulesSource
import com.merxury.blocker.core.ui.data.OnlineRulesSource.GITLAB
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _settingsUiState: MutableStateFlow<SettingsUiState> =
        MutableStateFlow(SettingsUiState())
    val settingsUiState: StateFlow<SettingsUiState> = _settingsUiState
}

data class SettingsUiState(
    val controllerType: ControllerType = IFW,
    val onlineRulesSource: OnlineRulesSource = GITLAB,
    val folderToSave: File = File(""),
    val backupSystemApps: Boolean = false,
    val restoreSystemApps: Boolean = false,
)
