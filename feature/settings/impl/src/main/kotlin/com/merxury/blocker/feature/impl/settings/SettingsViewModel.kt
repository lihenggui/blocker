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

package com.merxury.blocker.feature.impl.settings

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
import androidx.core.content.edit
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.analytics.StubAnalyticsHelper
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.SyncManager
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.UserEditableSettings
import com.merxury.blocker.core.model.preference.DarkThemeConfig
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.rule.backup.BackupSet
import com.merxury.blocker.core.rule.backup.BlockerBackupSets
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.rule.entity.RuleWorkResult
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.rule.entity.RuleWorkType.EXPORT_BLOCKER_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.EXPORT_IFW_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.IMPORT_BLOCKER_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.IMPORT_IFW_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.IMPORT_MAT_RULES
import com.merxury.blocker.core.rule.entity.RuleWorkType.RESET_IFW
import com.merxury.blocker.core.rule.entity.RuleWorkType.RESET_PM
import com.merxury.blocker.core.rule.entity.ruleWorkEventOf
import com.merxury.blocker.core.rule.work.ExportBlockerRulesWorker
import com.merxury.blocker.core.rule.work.ExportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportBlockerRuleWorker
import com.merxury.blocker.core.rule.work.ImportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportMatRulesWorker
import com.merxury.blocker.core.rule.work.ResetIfwWorker
import com.merxury.blocker.core.rule.work.ResetPmWorker
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.core.ui.profile.profileLabel
import com.merxury.blocker.feature.impl.settings.SettingsUiState.Loading
import com.merxury.blocker.feature.impl.settings.SettingsUiState.Success
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

private const val MAT_TARGET_USER = "matTargetUser"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    appContext: Application,
    private val userDataRepository: UserDataRepository,
    private val blockerPreferences: BlockerPreferencesDataSource,
    private val syncManager: SyncManager,
    private val analyticsHelper: AnalyticsHelper,
    private val workManager: WorkManager,
    private val savedStateHandle: SavedStateHandle,
    private val selectedUsers: SelectedUserRepository,
    private val deviceUsers: DeviceUserRepository,
    private val backupSets: BlockerBackupSets,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : AndroidViewModel(appContext) {
    private val ruleDialog = MutableStateFlow<SettingsRuleDialog?>(null)
    private var backupLookup: Job? = null
    private val keepWorkObservers = mutableMapOf<String, Job>()

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
            .combine(ruleDialog) { state, dialog -> state.copy(ruleDialog = dialog) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = Loading,
            )

    private val ruleEvents = MutableSharedFlow<SettingsRuleEvent>()
    internal val eventFlow = ruleEvents.asSharedFlow()

    private suspend fun getPathFromUriString(path: String): String = withContext(ioDispatcher) {
        if (path.isEmpty()) return@withContext path
        return@withContext try {
            val context: Context = getApplication()
            val uri = path.toUri()
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
            val currentProvider = userDataRepository.userData.first().ruleServerProvider
            if (currentProvider == provider) return@launch
            userDataRepository.setRuleServerProvider(provider)
            withContext(ioDispatcher) {
                blockerPreferences.resetRuleCommitId()
                clearLastSyncTime()
            }
            syncManager.requestSyncImmediately()
        }
    }

    private fun clearLastSyncTime() {
        getApplication<Application>()
            .getSharedPreferences("sync_rule", Context.MODE_PRIVATE)
            .edit { remove("last_synced_time") }
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
        // Nothing is emitted while the work is running or blocked.
        ruleWorkEventOf(ruleWorkType, workInfo)?.let { ruleEvents.emit(SettingsRuleEvent(it)) }
    }

    internal fun startRuleAction(action: SettingsRuleAction, pickMatFile: () -> Unit = {}) {
        val selected = selectedUsers.selectedUser.value
        val users = selectedUsers.users.value
        val target = ruleTarget(selected, users)
        backupLookup?.cancel()
        ruleDialog.value = null
        if (action == SettingsRuleAction.IMPORT) {
            backupLookup = viewModelScope.launch {
                val prefs = userDataRepository.userData.first()
                val import = SettingsRuleImport(target, prefs.ruleBackupFolder, prefs.restoreSystemApp)
                val alternatives = backupSets.alternatives(import.path, target.user)
                if (alternatives.isNotEmpty()) {
                    val names = (users + cachedUsers()).distinctBy { it.id }
                    ruleDialog.value = SettingsRuleDialog.ChooseBackup(
                        import,
                        ruleUserLabel(target.user, names),
                        alternatives.associateWith { ruleUserLabel(it.user, names) },
                    )
                } else if (target.label != null) {
                    ruleDialog.value = SettingsRuleDialog.Confirm(action, target, import)
                } else {
                    importBlockerRules(import)
                }
            }
        } else if (target.label != null || action == SettingsRuleAction.RESET_PM) {
            ruleDialog.value = SettingsRuleDialog.Confirm(action, target)
        } else {
            runRuleAction(action, target, pickMatFile)
        }
    }

    private suspend fun cachedUsers(): List<DeviceUser> = try {
        deviceUsers.users.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Cannot read cached users for rule operations")
        emptyList()
    }

    internal fun confirmRuleAction(pickMatFile: () -> Unit) {
        val confirmation = ruleDialog.value as? SettingsRuleDialog.Confirm ?: return
        dismissRuleDialog()
        val import = confirmation.import
        if (import != null) {
            importBlockerRules(import)
        } else {
            runRuleAction(confirmation.action, confirmation.target, pickMatFile)
        }
    }

    internal fun chooseBackup(source: BackupSet) {
        val choice = ruleDialog.value as? SettingsRuleDialog.ChooseBackup ?: return
        if (source !in choice.sets) return
        dismissRuleDialog()
        importBlockerRules(choice.import, source)
    }

    internal fun dismissRuleDialog() {
        ruleDialog.value = null
    }

    private fun runRuleAction(action: SettingsRuleAction, target: SettingsRuleTarget, pickMatFile: () -> Unit) {
        when (action) {
            SettingsRuleAction.EXPORT -> exportBlockerRules(target)

            SettingsRuleAction.RESET_PM -> enqueueUserWork(RESET_PM, "ResetPm", ExistingWorkPolicy.KEEP, target) {
                ResetPmWorker.resetPmWork(target.user)
            }

            SettingsRuleAction.IMPORT_MAT -> {
                savedStateHandle[MAT_TARGET_USER] = target.user.value
                pickMatFile()
            }

            SettingsRuleAction.IMPORT -> error("Blocker rule imports require backup settings")
        }
    }

    private fun importBlockerRules(import: SettingsRuleImport, source: BackupSet? = null) = enqueueUserWork(
        IMPORT_BLOCKER_RULES,
        "ImportBlockerRule",
        ExistingWorkPolicy.REPLACE,
        import.target,
    ) {
        ImportBlockerRuleWorker.importWork(
            backupPath = import.path,
            restoreSystemApps = import.restoreSystemApps,
            targetUser = import.target.user,
            sourceSet = source,
        )
    }

    private fun exportBlockerRules(target: SettingsRuleTarget) = enqueueUserWork(
        EXPORT_BLOCKER_RULES,
        "ExportBlockerRule",
        ExistingWorkPolicy.REPLACE,
        target,
    ) {
        val prefs = userDataRepository.userData.first()
        ExportBlockerRulesWorker.exportWork(prefs.ruleBackupFolder, prefs.backupSystemApp, user = target.user)
    }

    private fun enqueueUserWork(
        type: RuleWorkType,
        name: String,
        policy: ExistingWorkPolicy,
        target: SettingsRuleTarget,
        buildRequest: suspend () -> OneTimeWorkRequest,
    ) {
        val taskName = if (target.user == selectedUsers.ownUser) name else "$name-user-${target.user.value}"
        val keep = policy == ExistingWorkPolicy.KEEP
        if (keep) keepWorkObservers.remove(taskName)?.cancel()
        val observer = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val request = buildRequest()
                workManager.enqueueUniqueWork(taskName, policy, request).await()
                val infos = if (keep) {
                    workManager.getWorkInfosForUniqueWorkFlow(taskName).map { work ->
                        work.firstOrNull { it.id == request.id } ?: work.firstOrNull { !it.state.isFinished } ?: work.firstOrNull()
                    }
                } else {
                    workManager.getWorkInfoByIdFlow(request.id)
                }
                infos.transformWhile { info ->
                    if (info == null) return@transformWhile keep
                    val event = if (keep && info.state == WorkInfo.State.RUNNING) {
                        RuleWorkEvent(type, RuleWorkResult.STARTED)
                    } else {
                        ruleWorkEventOf(type, info)
                    }
                    event?.let { emit(SettingsRuleEvent(it, target.label)) }
                    !info.state.isFinished
                }
                    .distinctUntilChanged()
                    .collect { ruleEvents.emit(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Cannot run $type for user ${target.user.value}")
                ruleEvents.emit(SettingsRuleEvent(RuleWorkEvent(type, RuleWorkResult.UNEXPECTED_EXCEPTION), target.label))
            } finally {
                if (keep) keepWorkObservers.remove(taskName, coroutineContext.job)
            }
        }
        if (keep) keepWorkObservers[taskName] = observer
        observer.start()
    }

    fun exportIfwRules() = viewModelScope.launch {
        val taskName = "ExportIfwRule"
        val userData = userDataRepository.userData.first()
        workManager.apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ExportIfwRulesWorker.exportWork(userData.ruleBackupFolder),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(EXPORT_IFW_RULES, workInfo)
                }
        }
    }

    fun importIfwRules() = viewModelScope.launch {
        val taskName = "ImportIfwRule"
        val userData = userDataRepository.userData.first()
        workManager.apply {
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
                    if (workInfoList.isEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(IMPORT_IFW_RULES, workInfo)
                }
        }
    }

    fun resetIfwRules() = viewModelScope.launch {
        val taskName = "ResetIfw"
        workManager.apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ResetIfwWorker.clearIfwWork(),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(RESET_IFW, workInfo)
                }
        }
    }

    fun importMyAndroidToolsRules(fileUri: Uri?) {
        val user = savedStateHandle.remove<Int>(MAT_TARGET_USER)?.let(::UserId) ?: return
        if (fileUri == null) return
        viewModelScope.launch {
            val target = ruleTarget(user, if (user == selectedUsers.ownUser) emptyList() else selectedUsers.users.value + cachedUsers())
            enqueueUserWork(IMPORT_MAT_RULES, "ImportMatRule", ExistingWorkPolicy.KEEP, target) {
                val prefs = userDataRepository.userData.first()
                ImportMatRulesWorker.importWork(fileUri, prefs.restoreSystemApp, targetUser = user)
            }
        }
    }

    private fun ruleTarget(user: UserId, users: List<DeviceUser>): SettingsRuleTarget = SettingsRuleTarget(user, if (user == selectedUsers.ownUser) null else ruleUserLabel(user, users))

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
        val ruleDialog: SettingsRuleDialog? = null,
    ) : SettingsUiState
}

private fun ruleUserLabel(user: UserId, users: List<DeviceUser>): ProfileLabel = users.find { it.id == user }?.profileLabel()
    ?: if (user == UserId.SYSTEM) ProfileLabel.Personal else ProfileLabel.Numbered(user.value)
