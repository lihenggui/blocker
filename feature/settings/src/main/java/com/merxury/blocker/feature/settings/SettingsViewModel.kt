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
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.feature.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.settings.SettingsUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository
) : ViewModel() {
    val settingsUiState: StateFlow<SettingsUiState> =
        userDataRepository.userData
            .map { userData ->
                Success(
                    settings = UserEditableSettings(
                        controllerType = userData.controllerType,
                        ruleServerProvider = userData.ruleServerProvider,
                        ruleBackupFolder = userData.ruleBackupFolder,
                        backupSystemApp = userData.backupSystemApp,
                        restoreSystemApp = userData.restoreSystemApp,
                        showSystemApps = userData.showSystemApps,
                        showServiceInfo = userData.showServiceInfo
                    )
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading
            )

    fun updateControllerType(type: ControllerType) {
        viewModelScope.launch {
            userDataRepository.setControllerType(type)
        }
    }

    fun updateShowSystemApp(shouldShow: Boolean) {
        viewModelScope.launch {
            userDataRepository.setShowSystemApps(shouldShow)
        }
    }

    fun updateShowServiceInfo(shouldShow: Boolean) {
        viewModelScope.launch {
            userDataRepository.setShowServiceInfo(shouldShow)
        }
    }

    fun updateRuleServerProvider(provider: RuleServerProvider) {
        viewModelScope.launch {
            userDataRepository.setRuleServerProvider(provider)
        }
    }

    fun updateRuleBackupFolder(path: String) {
        viewModelScope.launch {
            userDataRepository.setRuleBackupFolder(path)
        }
    }

    fun updateBackupSystemApp(shouldBackup: Boolean) {
        viewModelScope.launch {
            userDataRepository.setBackupSystemApp(shouldBackup)
        }
    }

    fun updateRestoreSystemApp(shouldRestore: Boolean) {
        viewModelScope.launch {
            userDataRepository.setRestoreSystemApp(shouldRestore)
        }
    }

    fun importBlockerRules() {
        //TODO
    }

    fun exportBlockerRules() {
        //TODO
    }

    fun exportIfwRules() {
        //TODO
    }

    fun importIfwRules() {
        //TODO
    }

    fun resetIfwRules() {
        //TODO
    }

    fun importMyAndroidToolsRules() {
        //TODO
    }
}

data class UserEditableSettings(
    val controllerType: ControllerType = IFW,
    val ruleServerProvider: RuleServerProvider = GITLAB,
    val ruleBackupFolder: String? = null,
    val backupSystemApp: Boolean = false,
    val restoreSystemApp: Boolean = false,
    val showSystemApps: Boolean = false,
    val showServiceInfo: Boolean = false
)

sealed interface SettingsUiState {
    object Loading : SettingsUiState

    data class Success(val settings: UserEditableSettings) : SettingsUiState
}
