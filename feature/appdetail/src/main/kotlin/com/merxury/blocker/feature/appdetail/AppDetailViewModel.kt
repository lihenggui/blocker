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

package com.merxury.blocker.feature.appdetail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.controllers.IServiceController
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.ZipAllRuleUseCase
import com.merxury.blocker.core.domain.ZipAppRuleUseCase
import com.merxury.blocker.core.domain.components.SearchComponentsUseCase
import com.merxury.blocker.core.domain.controller.GetServiceControllerUseCase
import com.merxury.blocker.core.domain.detail.SearchMatchedRuleInAppUseCase
import com.merxury.blocker.core.domain.model.ComponentSearchResult
import com.merxury.blocker.core.domain.model.MatchedItem
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.result.asResult
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
import com.merxury.blocker.core.rule.work.ResetIfwWorker
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Sdk
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.ui.extension.updateComponentDetailUiState
import com.merxury.blocker.core.ui.extension.updateComponentInfoSwitchState
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SHARE_RULE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import timber.log.Timber
import javax.inject.Inject

private const val LIBCHECKER_PACKAGE_NAME = "com.absinthe.libchecker"

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analyticsHelper: AnalyticsHelper,
    private val pm: PackageManager,
    private val workerManager: WorkManager,
    private val userDataRepository: UserDataRepository,
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val componentDetailRepository: ComponentDetailRepository,
    private val searchComponents: SearchComponentsUseCase,
    private val getServiceController: GetServiceControllerUseCase,
    private val zipAllRuleUseCase: ZipAllRuleUseCase,
    private val zipAppRuleUseCase: ZipAppRuleUseCase,
    private val searchMatchedRuleInAppUseCase: SearchMatchedRuleInAppUseCase,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val appDetailArgs: AppDetailArgs = AppDetailArgs(savedStateHandle)
    private val _appInfoUiState: MutableStateFlow<AppInfoUiState> =
        MutableStateFlow(AppInfoUiState(AppItem("")))
    val appInfoUiState = _appInfoUiState.asStateFlow()
    private val _appBarUiState = MutableStateFlow(AppBarUiState())
    val appBarUiState: StateFlow<AppBarUiState> = _appBarUiState.asStateFlow()
    private val _tabState = MutableStateFlow(
        TabState(
            items = listOf(
                Info,
                Receiver,
                Service,
                Activity,
                Provider,
                Sdk,
            ),
            selectedItem = Info,
        ),
    )
    val tabState: StateFlow<TabState<AppDetailTabs>> = _tabState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _appInfoUiState.update {
            it.copy(error = throwable.toErrorMessage())
        }
    }

    // Int is the RuleWorkResult
    private val _eventFlow = MutableSharedFlow<Pair<RuleWorkType, Int>>()
    val eventFlow = _eventFlow.asSharedFlow()
    private var searchJob: Job? = null
    private var controlComponentJob: Job? = null

    init {
        loadTabInfo()
        updateSearchKeyword()
        loadAppInfo()
        loadComponentList()
        updateComponentList()
        listenSortStateChange()
        listenComponentDetailChanges()
        loadMatchedRule()
    }

    fun search(keyword: String) {
        if (keyword == _appBarUiState.value.keyword) return
        _appBarUiState.update { it.copy(keyword = keyword) }
        searchJob?.cancel()
        loadComponentListJob?.cancel()
        searchJob = viewModelScope.launch(cpuDispatcher + exceptionHandler) {
            Timber.v("Start filtering component list with keyword: $keyword")
            val packageName = appDetailArgs.packageName
            searchComponents(packageName, keyword).collect { result ->
                updateTabState(result)
                _appInfoUiState.update {
                    it.copy(
                        componentSearchUiState = Result.Success(result),
                    )
                }
            }
        }
    }

    private suspend fun updateTabState(result: ComponentSearchResult) {
        val itemCountMap = mapOf(
            Info to 1,
            Receiver to result.receiver.size,
            Service to result.service.size,
            Activity to result.activity.size,
            Provider to result.provider.size,
            Sdk to 1,
        ).filter { it.value > 0 }
        val nonEmptyItems = itemCountMap.filter { it.value > 0 }.keys.toList()
        if (_tabState.value.selectedItem !in nonEmptyItems) {
            Timber.d(
                "Selected tab ${_tabState.value.selectedItem}" +
                    "is not in non-empty items, return to first item",
            )
            _tabState.emit(
                TabState(
                    items = nonEmptyItems,
                    selectedItem = nonEmptyItems.first(),
                    itemCount = itemCountMap,
                ),
            )
        } else {
            _tabState.emit(
                TabState(
                    items = nonEmptyItems,
                    selectedItem = _tabState.value.selectedItem,
                    itemCount = itemCountMap,
                ),
            )
        }
    }

    private var loadComponentListJob: Job? = null

    fun loadComponentList() {
        if (_appBarUiState.value.isSearchMode) {
            val keyword = _appBarUiState.value.keyword
            search(keyword)
        } else {
            searchJob?.cancel()
            loadComponentListJob?.cancel()
            loadComponentListJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
                val packageName = appDetailArgs.packageName
                Timber.v("Start loading component: $packageName")
                searchComponents(packageName).collect { result ->
                    updateTabState(result)
                    _appInfoUiState.update {
                        it.copy(
                            componentSearchUiState = Result.Success(result),
                            isRefreshing = false,
                        )
                    }
                }
            }
        }
    }

    private var searchMatchedRuleJob: Job? = null
    fun loadMatchedRule() {
        searchMatchedRuleJob?.cancel()
        searchMatchedRuleJob = viewModelScope.launch(exceptionHandler) {
            val packageName = appDetailArgs.packageName
            searchMatchedRuleInAppUseCase(packageName)
                .asResult()
                .collect { result ->
                    when (result) {
                        is Result.Loading ->
                            Timber.v("Loading matched rule for $packageName")
                        is Result.Error ->
                            Timber.e(result.exception, "Fail to search matched rule")
                        is Result.Success ->
                            Timber.v("Matched rule for $packageName is loaded, size = ${result.data.size}")
                    }
                    _appInfoUiState.update {
                        it.copy(
                            matchedRuleUiState = result,
                        )
                    }
                }
        }
    }

    private fun listenSortStateChange() = viewModelScope.launch {
        userDataRepository.userData
            .distinctUntilChanged()
            .collect {
                loadComponentList()
            }
    }

    fun updateComponentList() = viewModelScope.launch {
        val packageName = appDetailArgs.packageName
        componentRepository.updateComponentList(packageName)
            .catch { error ->
                _appInfoUiState.update {
                    it.copy(error = error.toErrorMessage())
                }
            }
            .collect()
    }

    private fun updateSearchKeyword() = viewModelScope.launch(mainDispatcher) {
        val keyword = appDetailArgs.searchKeyword
            .map { it.trim() }
            .filterNot { it.isEmpty() }
        if (keyword.isEmpty()) return@launch
        val keywordString = keyword.joinToString(",")
        Timber.v("Search keyword: $keyword")
        _appBarUiState.update {
            it.copy(
                keyword = keywordString,
                isSearchMode = true,
                actions = getAppBarAction(),
            )
        }
    }

    @VisibleForTesting
    fun loadTabInfo() = viewModelScope.launch(mainDispatcher) {
        val screen = appDetailArgs.tabs
        Timber.v("Jump to tab: $screen")
        _tabState.update { it.copy(selectedItem = screen) }
        _appBarUiState.update {
            it.copy(actions = getAppBarAction())
        }
    }

    fun switchTab(newTab: AppDetailTabs) = viewModelScope.launch(mainDispatcher) {
        if (newTab != tabState.value.selectedItem) {
            Timber.d("Switch tab to ${newTab.name}, screen = ${appDetailArgs.packageName}")
            _tabState.update {
                it.copy(selectedItem = newTab)
            }
            _appBarUiState.update {
                it.copy(actions = getAppBarAction())
            }
        }
    }

    private suspend fun getAppBarAction(): List<AppBarAction> = when (tabState.value.selectedItem) {
        Info -> if (hasCustomizedRule()) {
            listOf(SHARE_RULE)
        } else {
            emptyList()
        }

        else -> if (hasCustomizedRule()) {
            listOf(SEARCH, MORE, SHARE_RULE)
        } else {
            listOf(SEARCH, MORE)
        }
    }

    private suspend fun hasCustomizedRule(): Boolean {
        val packageName = appDetailArgs.packageName
        return withContext(ioDispatcher) {
            componentDetailRepository.hasUserGeneratedDetail(packageName)
                .first()
        }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        Timber.i("Launch app $packageName")
        return pm.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            context.startActivity(launchIntent)
            true
        } ?: false
    }

    fun changeSearchMode(isSearchMode: Boolean) {
        Timber.v("Change search mode: $isSearchMode")
        if (!isSearchMode) {
            search("")
        }
        _appBarUiState.update {
            val originalSearchState = it.isSearchMode
            if (!originalSearchState && isSearchMode) {
                analyticsHelper.logSearchButtonClicked()
            }
            it.copy(
                isSearchMode = isSearchMode,
            )
        }
    }

    fun controlAllComponentsInPage(enable: Boolean, block: suspend (Int, Int) -> Unit) {
        controlComponentJob?.cancel()
        val currentComponentListUiState = _appInfoUiState.value.componentSearchUiState
        if (currentComponentListUiState !is Result.Success) {
            Timber.w("Cannot find component list to control")
            return
        }
        val componentList = currentComponentListUiState.data
        val sdkUiState = _appInfoUiState.value.matchedRuleUiState
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val list = when (tabState.value.selectedItem) {
                Receiver -> componentList.receiver
                Service -> componentList.service
                Activity -> componentList.activity
                Provider -> componentList.provider
                Sdk -> (sdkUiState as? Result.Success)
                    ?.data
                    ?.flatMap { it.componentList }
                    ?: listOf()

                else -> return@launch
            }
            controlAllComponentsInternal(list, enable, block)
            analyticsHelper.logBatchOperationPerformed(enable)
        }
    }

    fun controlAllComponents(
        list: List<ComponentInfo>,
        enable: Boolean,
        action: suspend (Int, Int) -> Unit,
    ) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            controlAllComponentsInternal(list, enable, action)
            analyticsHelper.logControlAllComponentsInSdkClicked(enable)
        }
    }

    private suspend fun controlAllComponentsInternal(
        list: List<ComponentInfo>,
        enable: Boolean,
        action: suspend (Int, Int) -> Unit,
    ) {
        val controllerType = userDataRepository.userData.first().controllerType
        var successCount = 0
        componentRepository.batchControlComponent(
            components = list,
            newState = enable,
        )
            .onStart {
                changeComponentsUiStatus(
                    changed = list,
                    controllerType = controllerType,
                    enabled = enable,
                )
            }
            .catch { exception ->
                Timber.e(exception, "Fail to change components to $enable")
                changeComponentsUiStatus(
                    changed = list,
                    controllerType = controllerType,
                    enabled = !enable,
                )
                _appInfoUiState.update {
                    it.copy(error = exception.toErrorMessage())
                }
            }
            .collect { _ ->
                successCount++
                action(successCount, list.size)
            }
    }

    fun dismissAlert() = _appInfoUiState.update {
        it.copy(error = null)
    }

    fun launchActivity(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am start -n $packageName/$componentName".exec(ioDispatcher)
            analyticsHelper.logStartActivityClicked()
        }
    }

    fun stopService(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val serviceController = getServiceController().first()
            serviceController.stopService(packageName, componentName)
            analyticsHelper.logStopServiceClicked()
            updateServiceStatus(serviceController, packageName, componentName)
        }
    }

    private suspend fun updateServiceStatus(
        serviceController: IServiceController,
        packageName: String,
        componentName: String,
    ) {
        val componentsUiState = _appInfoUiState.value.componentSearchUiState
        if (componentsUiState !is Result.Success) {
            Timber.w("Cannot find component list to update service info")
            return
        }
        val componentList = componentsUiState.data
        serviceController.load()
        val isRunning = serviceController.isServiceRunning(packageName, componentName)
        val item = componentList.service.find { it.name == componentName }
        if (item == null) {
            Timber.w("Cannot find service $componentName to update")
            return
        }
        val newStatus = item.copy(isRunning = isRunning)
        Timber.d("Update service $componentName running status to $newStatus")
        val newServiceState = componentList.copy(
            service = componentList.service.map { service ->
                if (service.name == componentName) {
                    newStatus
                } else {
                    service
                }
            },
        )
        _appInfoUiState.update {
            it.copy(componentSearchUiState = Result.Success(newServiceState))
        }
    }

    fun controlComponent(
        component: ComponentInfo,
        enabled: Boolean,
    ) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            controlComponentInternal(component, enabled)
            analyticsHelper.logSwitchComponentClicked(newState = enabled)
        }
    }

    fun controlAllSelectedComponents(enable: Boolean) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            controlAllComponentsInternal(
                list = _appBarUiState.value.selectedComponentList,
                enable = enable,
            ) { successCount, totalCount ->
                if (successCount == totalCount) {
                    Timber.i("All components are switch to $enable, updating ui")
                    switchSelectedMode(false)
                }
            }
        }
    }

    fun switchSelectedMode(value: Boolean) {
        // Clear list when exit from selectedMode
        if (!value) {
            _appBarUiState.update {
                it.copy(selectedComponentList = listOf())
            }
        }
        _appBarUiState.update {
            it.copy(isSelectedMode = value)
        }
    }

    fun selectItem(item: ComponentInfo) {
        val selectedList: MutableList<ComponentInfo> =
            _appBarUiState.value.selectedComponentList.toMutableList()
        selectedList.add(item)
        _appBarUiState.update {
            it.copy(selectedComponentList = selectedList)
        }
    }

    fun deselectItem(item: ComponentInfo) {
        val selectedList: MutableList<ComponentInfo> =
            _appBarUiState.value.selectedComponentList.toMutableList()
        selectedList.remove(item)
        _appBarUiState.update {
            it.copy(selectedComponentList = selectedList)
        }
    }

    fun selectAll() {
        val selectedAll = _appBarUiState.value.selectedComponentList
            .filter { it.type == AppDetailTabs.toComponentType(_tabState.value.selectedItem.name) }
            .size == getCurrentTabFilterComponentList().size
        // if selectedAll == true, deselect all
        if (selectedAll) {
            // un-select all components in the current tab
            val selectedList: MutableList<ComponentInfo> =
                _appBarUiState.value.selectedComponentList.toMutableList()
            selectedList.removeAll(getCurrentTabFilterComponentList())
            _appBarUiState.update {
                it.copy(selectedComponentList = selectedList)
            }
        } else {
            // select all components in the current tab
            val selectedList: MutableList<ComponentInfo> =
                _appBarUiState.value.selectedComponentList.toMutableList()
            selectedList.addAll(getCurrentTabFilterComponentList())
            _appBarUiState.update {
                it.copy(selectedComponentList = selectedList)
            }
        }
    }

    private fun getCurrentTabFilterComponentList(): MutableList<ComponentInfo> {
        val componentUiState = _appInfoUiState.value.componentSearchUiState
        if (componentUiState !is Result.Success) {
            Timber.w("Cannot find component list to update service info")
            return mutableListOf()
        }
        val componentList = componentUiState.data
        return when (tabState.value.selectedItem) {
            Receiver -> componentList.receiver
            Service -> componentList.service
            Activity -> componentList.activity
            Provider -> componentList.provider
            else -> listOf()
        }.toMutableList()
    }

    private suspend fun controlComponentInternal(
        component: ComponentInfo,
        enabled: Boolean,
    ) {
        val controllerType = userDataRepository.userData.first().controllerType
        componentRepository.controlComponent(component, enabled)
            .onStart {
                changeComponentsUiStatus(
                    changed = listOf(component),
                    controllerType = controllerType,
                    enabled = enabled,
                )
            }
            .catch { error ->
                Timber.e(error, "Cannot change component ${component.name} to $enabled")
                changeComponentsUiStatus(
                    changed = listOf(component),
                    controllerType = controllerType,
                    enabled = !enabled,
                )
                _appInfoUiState.update {
                    it.copy(error = error.toErrorMessage())
                }
            }
            .collect { result ->
                if (!result) {
                    Timber.w("Fail to change component ${component.name} to $enabled")
                    changeComponentsUiStatus(
                        changed = listOf(component),
                        controllerType = controllerType,
                        enabled = !enabled,
                    )
                }
            }
    }

    private fun changeComponentsUiStatus(
        changed: List<ComponentInfo>,
        controllerType: ControllerType,
        enabled: Boolean,
    ) {
        _appInfoUiState.update {
            val listUiState = it.componentSearchUiState
            val sdkUiState = it.matchedRuleUiState
            it.copy(
                componentSearchUiState = listUiState.updateComponentInfoSwitchState(
                    changed = changed,
                    controllerType = controllerType,
                    enabled = enabled,
                ),
                matchedRuleUiState = sdkUiState.updateComponentInfoSwitchState(
                    changed = changed,
                    controllerType = controllerType,
                    enabled = enabled,
                ),
            )
        }
    }

    fun exportBlockerRule(packageName: String) = viewModelScope.launch {
        Timber.d("Export Blocker rule for $packageName")
        val taskName = "ExportBlockerRule:$packageName"
        val userData = userDataRepository.userData.first()
        workerManager.apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.REPLACE,
                ExportBlockerRulesWorker.exportWork(
                    folderPath = userData.ruleBackupFolder,
                    backupSystemApps = userData.backupSystemApp,
                    backupPackageName = packageName,
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
        analyticsHelper.logExportBlockerRuleClicked()
    }

    fun importBlockerRule(packageName: String) = viewModelScope.launch {
        Timber.d("Import Blocker rule for $packageName")
        val taskName = "ImportBlockerRule:$packageName"
        val userData = userDataRepository.userData.first()
        workerManager.apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.REPLACE,
                ImportBlockerRuleWorker.importWork(
                    backupPath = userData.ruleBackupFolder,
                    restoreSystemApps = userData.restoreSystemApp,
                    controllerType = userData.controllerType,
                    backupPackageName = packageName,
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
        analyticsHelper.logImportBlockerRuleClicked()
    }

    fun exportIfwRule(packageName: String) = viewModelScope.launch {
        Timber.d("Export IFW rule for $packageName")
        val taskName = "ExportIfwRule:$packageName"
        val userData = userDataRepository.userData.first()
        workerManager.apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ExportIfwRulesWorker.exportWork(
                    folderPath = userData.ruleBackupFolder,
                    backupPackageName = packageName,
                ),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isNullOrEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(EXPORT_IFW_RULES, workInfo)
                }
        }
        analyticsHelper.logExportIfwRuleClicked()
    }

    fun importIfwRule(packageName: String) = viewModelScope.launch {
        Timber.d("Import IFW rule for $packageName")
        val taskName = "ImportIfwRule:$packageName"
        val userData = userDataRepository.userData.first()
        workerManager.apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ImportIfwRulesWorker.importIfwWork(
                    backupPath = userData.ruleBackupFolder,
                    restoreSystemApps = userData.restoreSystemApp,
                    packageName = packageName,
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
        analyticsHelper.logImportIfwRuleClicked()
    }

    fun resetIfw(packageName: String) = viewModelScope.launch {
        Timber.d("Reset IFW rule for $packageName")
        val taskName = "ResetIfw:$packageName"
        workerManager.apply {
            enqueueUniqueWork(
                taskName,
                ExistingWorkPolicy.KEEP,
                ResetIfwWorker.clearIfwWork(
                    packageName = packageName,
                ),
            )
            getWorkInfosForUniqueWorkLiveData(taskName)
                .asFlow()
                .collect { workInfoList ->
                    if (workInfoList.isNullOrEmpty()) return@collect
                    val workInfo = workInfoList.first()
                    listenWorkInfo(RESET_IFW, workInfo)
                }
        }
        analyticsHelper.logResetIfwRuleClicked()
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

    fun loadAppInfo() = viewModelScope.launch {
        val packageName = appDetailArgs.packageName
        val app = appRepository.getApplication(packageName).first()
        val isLibCheckerInstalled = ApplicationUtil.isAppInstalled(
            pm = pm,
            packageName = LIBCHECKER_PACKAGE_NAME,
        )
        if (app == null) {
            Timber.e("Wrong package name: $packageName, cannot show detail.")
            val error = IllegalStateException("Can't find $packageName in this device.")
            _appInfoUiState.update {
                it.copy(error = error.toErrorMessage())
            }
        } else {
            val packageInfo = pm.getPackageInfoCompat(packageName, 0)
            val userData = userDataRepository.userData.first()
            _appInfoUiState.update {
                it.copy(
                    appInfo = app.toAppItem(packageInfo = packageInfo),
                    iconBasedTheming = if (userData.useDynamicColor) {
                        getAppIcon(packageInfo)
                    } else {
                        null
                    },
                    showOpenInLibChecker = isLibCheckerInstalled,
                )
            }
        }
    }

    private fun listenComponentDetailChanges() = viewModelScope.launch {
        componentDetailRepository.updatedComponent.collect { detail ->
            _appInfoUiState.update {
                val listsState = it.componentSearchUiState
                val sdkUiState = it.matchedRuleUiState
                it.copy(
                    componentSearchUiState = listsState.updateComponentDetailUiState(detail),
                    matchedRuleUiState = sdkUiState.updateComponentDetailUiState(detail),
                )
            }
        }
    }

    private suspend fun getAppIcon(packageInfo: PackageInfo?) = withContext(ioDispatcher) {
        val icon: Drawable? = packageInfo?.applicationInfo?.loadIcon(pm)
        return@withContext icon?.toBitmap()
    }

    fun zipAllRule() = zipAllRuleUseCase()

    fun zipAppRule() = zipAppRuleUseCase(appDetailArgs.packageName)

    fun showAppInfo(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Timber.w("Show app info is only supported on Android N+")
            return
        }
        val destinationPackage = LIBCHECKER_PACKAGE_NAME
        val packageName = appDetailArgs.packageName
        val intent = Intent(Intent.ACTION_SHOW_APP_INFO).apply {
            putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            setPackage(destinationPackage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "LibChecker is not installed")
        }
    }
}

data class AppInfoUiState(
    val appInfo: AppItem,
    val isRefreshing: Boolean = false,
    val error: UiMessage? = null,
    val componentSearchUiState: Result<ComponentSearchResult> = Result.Loading,
    val matchedRuleUiState: Result<List<MatchedItem>> = Result.Loading,
    val iconBasedTheming: Bitmap? = null,
    val showOpenInLibChecker: Boolean = false,
)
