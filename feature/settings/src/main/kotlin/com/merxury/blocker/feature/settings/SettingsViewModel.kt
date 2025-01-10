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

package com.merxury.blocker.feature.settings

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.system.Os
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toFile
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.analytics.StubAnalyticsHelper
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.UserEditableSettings
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.rule.entity.RuleWorkType.EXPORT_BLOCKER_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.EXPORT_IFW_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.IMPORT_BLOCKER_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.IMPORT_IFW_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.RESET_IFW
import com.merxury.blocker.core.rule.work.ExportBlockerRulesWorker
import com.merxury.blocker.core.rule.work.ExportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportBlockerRuleWorker
import com.merxury.blocker.core.rule.work.ImportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportMatRulesWorker
import com.merxury.blocker.core.rule.work.ResetIfwWorker
import com.merxury.blocker.feature.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.settings.SettingsUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    appContext: Application,
    private val userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(appContext) {
    val settingsUiState: StateFlow<SettingsUiState> =
        userDataRepository.userData
            .map { userData ->
                Success(
                    settings = UserEditableSettings(
                        controllerType = userData.controllerType,
                        ruleServerProvider = userData.ruleServerProvider,
                        appDisplayLanguage = userData.appDisplayLanguage,
                        libDisplayLanguage = userData.libDisplayLanguage,
                        ruleBackupFolder = getPathFromUriString(userData.ruleBackupFolder),
                        backupSystemApp = userData.backupSystemApp,
                        restoreSystemApp = userData.restoreSystemApp,
                        showSystemApps = userData.showSystemApps,
                        showServiceInfo = userData.showServiceInfo,
                        darkThemeConfig = userData.darkThemeConfig,
                        useDynamicColor = userData.useDynamicColor,
                        enableStatistics = userData.enableStatistics,
                    ),
                    allowStatistics = isAllowStatistics(),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading,
            )

    // Int is the RuleWorkResult
    private val _eventFlow = MutableSharedFlow<Pair<RuleWorkType, Int>>()
    val eventFlow = _eventFlow.asSharedFlow()

    private suspend fun getPathFromUriString(path: String): String = withContext(ioDispatcher) {
        if (path.isEmpty()) return@withContext path
        return@withContext try {
            val context: Context = getApplication()
            val uri = Uri.parse(path)
            val cr = context.contentResolver
            if (uri.scheme == "file") {
                return@withContext uri.toFile().absolutePath
            }
            require(uri.scheme == "content") { "Uri lacks 'content' scheme: $uri" }
            val df = DocumentFile.fromTreeUri(context, uri)
                ?: throw IOException("Can't get DocumentFile from uri: $uri")
            cr.openFileDescriptor(df.uri, "r")?.use {
                Os.readlink("/proc/self/fd/${it.fd}")
            } ?: throw IOException("Can't open file descriptor for uri: $uri")
        } catch (e: Exception) {
            Timber.e(e, "Can't get path from uri string: $path")
            ""
        }
    }

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

    fun updateAppDisplayLanguage(language: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(language)
            } else {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language),
                )
            }
            userDataRepository.setAppDisplayLanguage(language)
        }
    }

    fun updateLibDisplayLanguage(language: String) {
        viewModelScope.launch {
            userDataRepository.setLibDisplayLanguage(language)
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

    fun updateCheckedStatistics(checked: Boolean) {
        viewModelScope.launch {
            userDataRepository.setEnableStatistics(checked)
        }
    }

    private suspend fun listenWorkInfo(ruleWorkType: RuleWorkType, workInfo: WorkInfo) {
        val state = workInfo.state
        val outputData = workInfo.outputData
        val workResult = when (state) {
            State.ENQUEUED -> RuleWorkResult.STARTED
            State.FAILED -> outputData.getInt(RuleWorkResult.PARAM_WORK_RESULT, -1)
            State.SUCCEEDED -> RuleWorkResult.FINISHED
            State.CANCELLED -> RuleWorkResult.CANCELLED
            else -> return // Do not emit anything when it is running or blocked
        }
        _eventFlow.emit(ruleWorkType to workResult)
    }

    fun importBlockerRules() = viewModelScope.launch {
        val taskName = "ImportBlockerRule"
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.REPLACE,
                ImportBlockerRuleWorker.importWork(
                    backupPath = userData.ruleBackupFolder,
                    restoreSystemApps = userData.restoreSystemApp,
                    controllerType = userData.controllerType,
                ),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isNullOrEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(IMPORT_BLOCKER_RULES, workInfo)
                }
        }
    }

    fun exportBlockerRules() = viewModelScope.launch {
        val taskName = "ExportBlockerRule"
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.REPLACE,
                ExportBlockerRulesWorker.exportWork(
                    folderPath = userData.ruleBackupFolder,
                    backupSystemApps = userData.backupSystemApp,
                ),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isNullOrEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(EXPORT_BLOCKER_RULES, workInfo)
                }
        }
    }

    fun exportIfwRules() = viewModelScope.launch {
        val taskName = "ExportIfwRule"
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ExportIfwRulesWorker.exportWork(userData.ruleBackupFolder),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isNullOrEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(EXPORT_IFW_RULES, workInfo)
                }
        }
    }

    fun importIfwRules() = viewModelScope.launch {
        val taskName = "ImportIfwRule"
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ImportIfwRulesWorker.importIfwWork(
                    backupPath = userData.ruleBackupFolder,
                    restoreSystemApps = userData.restoreSystemApp,
                ),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isNullOrEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(IMPORT_IFW_RULES, workInfo)
                }
        }
    }

    fun resetIfwRules() = viewModelScope.launch {
        val taskName = "ResetIfw"
        WorkManager.getInstance(getApplication()).apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ResetIfwWorker.clearIfwWork(),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isNullOrEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(RESET_IFW, workInfo)
                }
        }
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

    // Only FOSS version provides StubAnalyticsHelper
    @VisibleForTesting
    fun isAllowStatistics(): Boolean = analyticsHelper !is StubAnalyticsHelper
}

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Success(
        val settings: UserEditableSettings,
        val allowStatistics: Boolean = false,
    ) : SettingsUiState
}
