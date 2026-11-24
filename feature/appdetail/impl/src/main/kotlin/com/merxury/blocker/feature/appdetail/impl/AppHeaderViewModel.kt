/*
 * Copyright 2026 Blocker
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

package com.merxury.blocker.feature.appdetail.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.domain.ZipAllRuleUseCase
import com.merxury.blocker.core.domain.ZipAppRuleUseCase
import com.merxury.blocker.core.domain.model.ZippedRule
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.preference.UserPreferenceData
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.rule.entity.RuleWorkEvent
import com.merxury.blocker.core.rule.entity.RuleWorkType
import com.merxury.blocker.core.rule.entity.ruleWorkEventOf
import com.merxury.blocker.core.rule.work.ExportBlockerRulesWorker
import com.merxury.blocker.core.rule.work.ExportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ImportBlockerRuleWorker
import com.merxury.blocker.core.rule.work.ImportIfwRulesWorker
import com.merxury.blocker.core.rule.work.ResetIfwWorker
import com.merxury.blocker.core.ui.coil.ApkIcon
import com.merxury.blocker.core.ui.profile.ProfileLabel
import com.merxury.blocker.core.ui.profile.profileLabel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** The package name LibChecker is installed under. */
internal const val LIBCHECKER_PACKAGE_NAME = "com.absinthe.libchecker"

/**
 * The header of the detail screen for one app and user, and the actions on the whole app: the
 * rule works for this app, which act on the screen's user, and sharing its rules.
 */
@HiltViewModel(assistedFactory = AppHeaderViewModel.Factory::class)
class AppHeaderViewModel @AssistedInject constructor(
    selectedUsers: SelectedUserRepository,
    deviceUsers: DeviceUserRepository,
    installedApps: InstalledAppRepository,
    componentDetails: ComponentDetailRepository,
    private val userDataRepository: UserDataRepository,
    private val workManager: WorkManager,
    private val zipAppRule: ZipAppRuleUseCase,
    private val zipAllRule: ZipAllRuleUseCase,
    private val analyticsHelper: AnalyticsHelper,
    @Assisted packageName: String,
    @Assisted userId: Int?,
) : ViewModel() {
    private val ownUser = selectedUsers.ownUser
    private val app = selectedUsers.appKeyOf(packageName, userId)
    private val ruleWork = MutableStateFlow<RuleWorkEvent?>(null)
    private val sharedRules = MutableStateFlow<ZippedRule?>(null)

    // LibChecker reads apps of its own user, which is Blocker's.
    private val libCheckerCanShow = combine(
        installedApps.app(AppKey(LIBCHECKER_PACKAGE_NAME, ownUser)),
        installedApps.app(AppKey(packageName, ownUser)),
    ) { libChecker, ownCopy -> libChecker != null && ownCopy != null }

    // Read again after each description the user saves, so sharing is offered after the first.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val hasCustomizedRules = componentDetails.updatedComponent
        .map { }
        .onStart { emit(Unit) }
        .flatMapLatest { componentDetails.hasUserGeneratedDetail(packageName) }
        .distinctUntilChanged()

    private val messages = combine(ruleWork, sharedRules, ::Pair)

    val uiState: StateFlow<AppHeaderUiState> = combine(
        installedApps.app(app),
        deviceUsers.users,
        libCheckerCanShow,
        combine(hasCustomizedRules, userDataRepository.userData.map { it.useDynamicColor }, ::Pair),
        messages,
    ) { installed, users, libChecker, (customized, dynamicColor), (ruleWork, sharedRules) ->
        AppHeaderUiState(
            app = installed?.toHeaderApp(),
            profile = profileOf(users),
            launch = launchModeOf(users),
            showLibChecker = libChecker,
            hasCustomizedRules = customized,
            useDynamicColor = dynamicColor,
            ruleWork = ruleWork,
            sharedRules = sharedRules,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppHeaderUiState(),
    )

    fun exportBlockerRules() = startRuleWork(RuleWorkType.EXPORT_BLOCKER_RULES, "ExportBlockerRule", ExistingWorkPolicy.REPLACE) {
        analyticsHelper.logExportBlockerRuleClicked()
        ExportBlockerRulesWorker.exportWork(it.ruleBackupFolder, it.backupSystemApp, backupPackageName = app.packageName, user = app.userId)
    }

    fun importBlockerRules() = startRuleWork(RuleWorkType.IMPORT_BLOCKER_RULES, "ImportBlockerRule", ExistingWorkPolicy.REPLACE) {
        analyticsHelper.logImportBlockerRuleClicked()
        ImportBlockerRuleWorker.importWork(it.ruleBackupFolder, it.restoreSystemApp, backupPackageName = app.packageName, targetUser = app.userId)
    }

    // Intent Firewall rules apply to every user, so these works name no user.
    fun exportIfwRules() = startRuleWork(RuleWorkType.EXPORT_IFW_RULES, "ExportIfwRule", ExistingWorkPolicy.KEEP) {
        analyticsHelper.logExportIfwRuleClicked()
        ExportIfwRulesWorker.exportWork(it.ruleBackupFolder, backupPackageName = app.packageName)
    }

    fun importIfwRules() = startRuleWork(RuleWorkType.IMPORT_IFW_RULES, "ImportIfwRule", ExistingWorkPolicy.KEEP) {
        analyticsHelper.logImportIfwRuleClicked()
        ImportIfwRulesWorker.importIfwWork(it.ruleBackupFolder, it.restoreSystemApp, packageName = app.packageName)
    }

    fun resetIfw() = startRuleWork(RuleWorkType.RESET_IFW, "ResetIfw", ExistingWorkPolicy.KEEP) {
        analyticsHelper.logResetIfwRuleClicked()
        ResetIfwWorker.clearIfwWork(packageName = app.packageName)
    }

    fun onRuleWorkShown() {
        ruleWork.value = null
    }

    fun shareAppRules() = viewModelScope.launch {
        sharedRules.value = zipAppRule(app).first()
    }

    fun shareAllRules() = viewModelScope.launch {
        sharedRules.value = zipAllRule().first()
    }

    fun onShareHandled() {
        sharedRules.value = null
    }

    /** Enqueues the work [request] makes and reports its events until the screen goes away. */
    private fun startRuleWork(
        type: RuleWorkType,
        name: String,
        policy: ExistingWorkPolicy,
        request: (UserPreferenceData) -> OneTimeWorkRequest,
    ) = viewModelScope.launch {
        val taskName = "$name:${app.packageName}:${app.userId.value}"
        Timber.d("Start $type for $app")
        workManager.enqueueUniqueWork(taskName, policy, request(userDataRepository.userData.first()))
        workManager.getWorkInfosForUniqueWorkLiveData(taskName).asFlow().collect { infos ->
            // Nothing is reported while the work runs or is blocked.
            infos.firstOrNull()?.let { ruleWorkEventOf(type, it) }?.let { ruleWork.value = it }
        }
    }

    /**
     * The profile of another user's app: its label once the users are cached, numbered when they
     * are and the app's user is not among them, and none while no user is cached yet.
     */
    private fun profileOf(users: List<DeviceUser>): ProfileLabel? {
        if (app.userId == ownUser || users.isEmpty()) return null
        return users.find { it.id == app.userId }?.profileLabel() ?: ProfileLabel.Numbered(app.userId.value)
    }

    private fun launchModeOf(users: List<DeviceUser>): LaunchMode {
        if (app.userId == ownUser) return LaunchMode.OWN_USER
        val own = users.find { it.id == ownUser }
        val other = users.find { it.id == app.userId }
        // The launcher service reaches the profiles of Blocker's profile group only.
        val sameGroup = other?.parent == ownUser || own?.parent == app.userId || (own?.parent != null && own.parent == other?.parent)
        return if (sameGroup) LaunchMode.PROFILE else LaunchMode.NONE
    }

    @AssistedFactory
    interface Factory {
        fun create(packageName: String, userId: Int?): AppHeaderViewModel
    }
}

private fun InstalledApp.toHeaderApp() = HeaderApp(
    packageName = key.packageName,
    label = label,
    versionName = versionName,
    versionCode = versionCode,
    icon = ApkIcon(key.packageName, apkPath, versionCode, uid),
    uid = uid,
    targetSdk = targetSdk,
    minSdk = minSdk,
    lastUpdateTime = lastUpdateTime,
    dataDir = dataDir,
)
