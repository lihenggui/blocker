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

package com.merxury.blocker.feature.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.preference.RuleServerProvider.GITLAB
import com.merxury.blocker.core.model.preference.ThemeBrand
import com.merxury.blocker.core.rule.work.ExportBlockerRulesWorker
import com.merxury.blocker.core.rule.work.ExportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportBlockerRuleWorker
import com.merxury.blocker.core.rule.work.ImportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportMatRulesWorker
import com.merxury.blocker.core.rule.work.ResetIfwWorker
import com.merxury.blocker.feature.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.settings.SettingsUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    appContext: Application,
    private val userDataRepository: UserDataRepository,
) : AndroidViewModel(appContext) {
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
                        showServiceInfo = userData.showServiceInfo,
                        themeBrand = userData.themeBrand,
                        darkThemeConfig = userData.darkThemeConfig,
                        useDynamicColor = userData.useDynamicColor,
                    ),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading,
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

    fun updateRuleBackupFolder(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                Timber.e("Backup folder is null, ignore")
                return@launch
            }
            val context: Context = getApplication()
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            userDataRepository.setRuleBackupFolder(uri.toString())
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

    fun importBlockerRules() = viewModelScope.launch {
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                "ImportBlockerRule",
                ExistingWorkPolicy.REPLACE,
                ImportBlockerRuleWorker.importWork(
                    backupPath = userData.ruleBackupFolder,
                    restoreSystemApps = userData.restoreSystemApp,
                    controllerType = userData.controllerType,
                ),
            )
        }
    }

    fun exportBlockerRules() = viewModelScope.launch {
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                "ExportBlockerRule",
                ExistingWorkPolicy.REPLACE,
                ExportBlockerRulesWorker.exportWork(
                    folderPath = userData.ruleBackupFolder,
                    backupSystemApps = userData.backupSystemApp,
                ),
            )
        }
    }

    fun exportIfwRules() = viewModelScope.launch {
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                "ExportIfwRule",
                ExistingWorkPolicy.KEEP,
                ExportIfwRulesWorker.exportWork(userData.ruleBackupFolder),
            )
        }
    }

    fun importIfwRules() = viewModelScope.launch {
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                "ImportIfwRule",
                ExistingWorkPolicy.KEEP,
                ImportIfwRulesWorker.importIfwWork(
                    backupPath = userData.ruleBackupFolder,
                    restoreSystemApps = userData.restoreSystemApp,
                ),
            )
        }
    }

    fun resetIfwRules() {
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork("ResetIfw", ExistingWorkPolicy.KEEP, ResetIfwWorker.clearIfwWork())
    }

    fun importMyAndroidToolsRules(fileUri: Uri?) = viewModelScope.launch {
        if (fileUri == null) {
            Timber.e("Can't get MAT backup file from URI.")
            return@launch
        }
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                "ImportMatRule",
                ExistingWorkPolicy.KEEP,
                ImportMatRulesWorker.importWork(
                    fileUri,
                    userData.controllerType,
                    userData.restoreSystemApp,
                ),
            )
        }
    }

    fun updateThemeBrand(themeBrand: ThemeBrand) {
        viewModelScope.launch {
            userDataRepository.setThemeBrand(themeBrand)
        }
    }

    fun updateDarkThemeConfig(darkThemeConfig: DarkThemeConfig) {
        viewModelScope.launch {
            userDataRepository.setDarkThemeConfig(darkThemeConfig)
        }
    }

    fun updateDynamicColorPreference(useDynamicColor: Boolean) {
        viewModelScope.launch {
            userDataRepository.setDynamicColorPreference(useDynamicColor)
        }
    }
}

data class UserEditableSettings(
    val controllerType: ControllerType = IFW,
    val ruleServerProvider: RuleServerProvider = GITLAB,
    val ruleBackupFolder: String = "",
    val backupSystemApp: Boolean = false,
    val restoreSystemApp: Boolean = false,
    val showSystemApps: Boolean = false,
    val showServiceInfo: Boolean = false,
    val themeBrand: ThemeBrand,
    val darkThemeConfig: DarkThemeConfig,
    val useDynamicColor: Boolean,
)

sealed interface SettingsUiState {
    object Loading : SettingsUiState

    data class Success(val settings: UserEditableSettings) : SettingsUiState
}
