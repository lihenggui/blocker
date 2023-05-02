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

package com.merxury.blocker.feature.appdetail

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkInfo.State
import androidx.work.WorkManager
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.controllers.shizuku.ShizukuInitializer
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.preference.ComponentShowPriority.DISABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.ENABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.NONE
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.ComponentSorting.PACKAGE_NAME
import com.merxury.blocker.core.model.preference.ComponentSortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.ComponentSortingOrder.DESCENDING
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
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.applist.model.toAppItem
import com.merxury.blocker.core.ui.component.ComponentItem
import com.merxury.blocker.core.ui.component.toComponentItem
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SHARE_RULE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.core.utils.ServiceHelper
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Loading
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val appContext: Application,
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder,
    private val analyticsHelper: AnalyticsHelper,
    private val pm: PackageManager,
    private val userDataRepository: UserDataRepository,
    private val appRepository: AppRepository,
    private val componentRepository: LocalComponentRepository,
    private val componentDetailRepository: ComponentDetailRepository,
    private val shizukuInitializer: ShizukuInitializer,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val appDetailArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _appInfoUiState: MutableStateFlow<AppInfoUiState> = MutableStateFlow(Loading)
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
            ),
            selectedItem = Info,
        ),
    )
    val tabState: StateFlow<TabState<AppDetailTabs>> = _tabState.asStateFlow()
    private var currentFilterKeyword = appDetailArgs.searchKeyword
        .map { it.trim() }
        .filterNot { it.isEmpty() }
    private var _unfilteredList = ComponentListUiState()
    private val _componentListUiState = MutableStateFlow(ComponentListUiState())
    val componentListUiState = _componentListUiState.asStateFlow()
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }

    // Int is the RuleWorkResult
    private val _eventFlow = MutableSharedFlow<Pair<RuleWorkType, Int>>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        loadTabInfo()
        updateSearchKeyword()
        loadAppInfo()
        loadComponentList()
        updateComponentList(appDetailArgs.packageName)
        listenSortStateChange()
    }

    override fun onCleared() {
        super.onCleared()
        deinitShizuku()
    }

    fun initShizuku() = viewModelScope.launch {
        val controllerType = userDataRepository.userData.first().controllerType
        if (controllerType == SHIZUKU) {
            shizukuInitializer.registerShizuku()
        }
    }

    private fun deinitShizuku() = viewModelScope.launch {
        val controllerType = userDataRepository.userData.first().controllerType
        if (controllerType == SHIZUKU) {
            shizukuInitializer.unregisterShizuku()
        }
    }

    fun search(newText: TextFieldValue) = viewModelScope.launch(cpuDispatcher + exceptionHandler) {
        val keyword = newText.text
        Timber.i("Filtering component list with keyword: $keyword")
        // Update search bar text first
        _appBarUiState.update { it.copy(keyword = newText) }
        filterAndUpdateComponentList(keyword)
        updateTabState(_componentListUiState.value)
    }

    private suspend fun updateTabState(listUiState: ComponentListUiState) {
        val itemCountMap = mapOf(
            Info to 1,
            Receiver to listUiState.receiver.size,
            Service to listUiState.service.size,
            Activity to listUiState.activity.size,
            Provider to listUiState.provider.size,
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

    private suspend fun filterAndUpdateComponentList(keyword: String) {
        // Start filtering in the component list
        currentFilterKeyword = keyword.split(",")
            .map { it.trim() }
            .filterNot { it.isEmpty() }
        if (currentFilterKeyword.isEmpty()) {
            _componentListUiState.emit(_unfilteredList)
            return
        }
        val receiver = mutableStateListOf<ComponentItem>()
        val service = mutableStateListOf<ComponentItem>()
        val activity = mutableStateListOf<ComponentItem>()
        val provider = mutableStateListOf<ComponentItem>()
        currentFilterKeyword.forEach { subKeyword ->
            val filteredReceiver = _unfilteredList.receiver
                .filter { it.name.contains(subKeyword, ignoreCase = true) }
            val filteredService = _unfilteredList.service
                .filter { it.name.contains(subKeyword, ignoreCase = true) }
            val filteredActivity = _unfilteredList.activity
                .filter { it.name.contains(subKeyword, ignoreCase = true) }
            val filteredProvider = _unfilteredList.provider
                .filter { it.name.contains(subKeyword, ignoreCase = true) }
            receiver.addAll(filteredReceiver)
            service.addAll(filteredService)
            activity.addAll(filteredActivity)
            provider.addAll(filteredProvider)
        }
        _componentListUiState.emit(
            ComponentListUiState(
                receiver = receiver,
                service = service,
                activity = activity,
                provider = provider,
            ),
        )
    }

    private fun loadComponentList() = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        val packageName = appDetailArgs.packageName
        componentRepository.getComponentList(packageName)
            .collect { origList ->
                // Show the cache data first
                updateTabContent(origList, packageName)
                // Load the data with description and update again
                val list = origList.map { component ->
                    val detail = componentDetailRepository.getComponentDetailCache(component.name)
                        .first()
                    if (detail != null) {
                        component.copy(description = detail.description)
                    } else {
                        component
                    }
                }
                updateTabContent(list, packageName)
            }
    }

    private suspend fun updateTabContent(
        list: List<ComponentInfo>,
        packageName: String,
    ) {
        // Store the unfiltered list
        val receiver = list.filter { it.type == RECEIVER }
        val service = list.filter { it.type == SERVICE }
        val activity = list.filter { it.type == ACTIVITY }
        val provider = list.filter { it.type == PROVIDER }
        _unfilteredList =
            getComponentListUiState(packageName, receiver, service, activity, provider)
        filterAndUpdateComponentList(currentFilterKeyword.joinToString(","))
        updateTabState(_componentListUiState.value)
    }

    private fun listenSortStateChange() = viewModelScope.launch {
        userDataRepository.userData
            .distinctUntilChanged()
            .collect {
                loadComponentList()
            }
    }

    private fun updateComponentList(packageName: String) = viewModelScope.launch {
        componentRepository.updateComponentList(packageName)
            .catch { _errorState.emit(it.toErrorMessage()) }
            .collect()
    }

    private suspend fun getComponentListUiState(
        packageName: String,
        receiver: List<ComponentInfo>,
        service: List<ComponentInfo>,
        activity: List<ComponentInfo>,
        provider: List<ComponentInfo>,
    ) = ComponentListUiState(
        receiver = sortAndConvertToComponentItem(
            list = receiver,
            packageName = packageName,
            type = RECEIVER,
        ),
        service = sortAndConvertToComponentItem(
            list = service,
            packageName = packageName,
            type = SERVICE,
        ),
        activity = sortAndConvertToComponentItem(
            list = activity,
            packageName = packageName,
            type = ACTIVITY,
        ),
        provider = sortAndConvertToComponentItem(
            list = provider,
            packageName = packageName,
            type = PROVIDER,
        ),
    )

    private suspend fun sortAndConvertToComponentItem(
        list: List<ComponentInfo>,
        packageName: String,
        type: ComponentType,
        filterKeyword: String = "",
    ): SnapshotStateList<ComponentItem> {
        val userData = userDataRepository.userData.first()
        val sorting = userData.componentSorting
        val order = userData.componentSortingOrder
        val serviceHelper = ServiceHelper(packageName)
        if (type == SERVICE) {
            serviceHelper.refresh()
        }
        return list.filter { it.name.contains(filterKeyword, ignoreCase = true) }
            .map {
                it.toComponentItem(
                    if (type == SERVICE) {
                        serviceHelper.isServiceRunning(it.name)
                    } else {
                        false
                    },
                )
            }
            .let { origList ->
                when (sorting) {
                    COMPONENT_NAME -> when (order) {
                        ASCENDING -> origList.sortedBy { it.simpleName.lowercase() }
                        DESCENDING -> origList.sortedByDescending { it.simpleName.lowercase() }
                    }

                    PACKAGE_NAME -> when (order) {
                        ASCENDING -> origList.sortedBy { it.name.lowercase() }
                        DESCENDING -> origList.sortedByDescending { it.name.lowercase() }
                    }
                }
            }
            .let { sortedList ->
                when (userData.componentShowPriority) {
                    NONE -> sortedList
                    DISABLED_COMPONENTS_FIRST -> sortedList.sortedBy { it.enabled() }
                    ENABLED_COMPONENTS_FIRST -> sortedList.sortedByDescending { it.enabled() }
                }
            }
            .toMutableStateList()
    }

    private fun updateSearchKeyword() {
        val keyword = appDetailArgs.searchKeyword
            .map { it.trim() }
            .filterNot { it.isEmpty() }
        if (keyword.isEmpty()) return
        val keywordString = keyword.joinToString(",")
        Timber.v("Search keyword: $keyword")
        _appBarUiState.update {
            it.copy(
                keyword = TextFieldValue(keywordString),
                isSearchMode = true,
                actions = getAppBarAction(),
            )
        }
    }

    private fun loadTabInfo() {
        val screen = appDetailArgs.tabs
        Timber.v("Jump to tab: $screen")
        _tabState.update { it.copy(selectedItem = screen) }
    }

    fun switchTab(newTab: AppDetailTabs) {
        if (newTab != tabState.value.selectedItem) {
            _tabState.update {
                it.copy(selectedItem = newTab)
            }
            _appBarUiState.update {
                it.copy(actions = getAppBarAction())
            }
        }
    }

    private fun getAppBarAction(): List<AppBarAction> = when (tabState.value.selectedItem) {
        Info -> listOf(SHARE_RULE)
        else -> listOf(SEARCH, MORE)
    }

    fun launchApp(context: Context, packageName: String) {
        Timber.i("Launch app $packageName")
        pm.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            context.startActivity(launchIntent)
        }
    }

    fun changeSearchMode(isSearchMode: Boolean) {
        Timber.v("Change search mode: $isSearchMode")
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

    fun controlAllComponents(enable: Boolean) =
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val list = when (tabState.value.selectedItem) {
                Receiver -> _componentListUiState.value.receiver
                Service -> _componentListUiState.value.service
                Activity -> _componentListUiState.value.activity
                Provider -> _componentListUiState.value.provider
                else -> return@launch
            }
            list.forEach {
                controlComponentInternal(it.packageName, it.name, enable)
            }
            analyticsHelper.logBatchOperationPerformed(enable)
        }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun launchActivity(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am start -n $packageName/$componentName".exec(ioDispatcher)
            analyticsHelper.logStartActivityClicked()
        }
    }

    fun stopService(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am stopservice $packageName/$componentName".exec(ioDispatcher)
            analyticsHelper.logStopServiceClicked()
            updateServiceStatus(packageName, componentName)
        }
    }

    private suspend fun updateServiceStatus(packageName: String, componentName: String) {
        val helper = ServiceHelper(packageName)
        helper.refresh()
        val isRunning = helper.isServiceRunning(componentName)
        val item = _componentListUiState.value.service.find { it.name == componentName }
        if (item == null) {
            Timber.w("Cannot find service $componentName to update")
            return
        }
        val newStatus = item.copy(isRunning = isRunning)
        Timber.d("Update service $componentName running status to $newStatus")
        _componentListUiState.update {
            it.copy(
                service = it.service.map { item ->
                    if (item.name == componentName) {
                        newStatus
                    } else {
                        item
                    }
                }.toMutableStateList(),
            )
        }
    }

    fun controlComponent(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        controlComponentInternal(packageName, componentName, enabled)
        analyticsHelper.logSwitchComponentClicked(newState = enabled)
    }

    private suspend fun controlComponentInternal(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) {
        componentRepository.controlComponent(packageName, componentName, enabled)
            .catch { exception ->
                _errorState.emit(exception.toErrorMessage())
            }
            .collect()
    }

    fun exportBlockerRule(packageName: String) = viewModelScope.launch {
        Timber.d("Export Blocker rule for $packageName")
        val taskName = "ExportBlockerRule:$packageName"
        val userData = userDataRepository.userData.first()
        WorkManager.getInstance(appContext).apply {
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
        WorkManager.getInstance(appContext).apply {
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
        WorkManager.getInstance(appContext).apply {
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
        WorkManager.getInstance(appContext).apply {
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
        WorkManager.getInstance(appContext).apply {
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

    private fun loadAppInfo() = viewModelScope.launch {
        val packageName = appDetailArgs.packageName
        val app = appRepository.getApplication(packageName).first()
        if (app == null) {
            val error = UiMessage("Can't find $packageName in this device.")
            Timber.e(error.title)
            _appInfoUiState.emit(AppInfoUiState.Error(error))
        } else {
            val packageInfo = pm.getPackageInfoCompat(packageName, 0)
            _appInfoUiState.emit(
                AppInfoUiState.Success(app.toAppItem(packageInfo = packageInfo)),
            )
        }
    }
}

sealed interface AppInfoUiState {
    object Loading : AppInfoUiState
    class Error(val error: UiMessage) : AppInfoUiState
    data class Success(val appInfo: AppItem) : AppInfoUiState
}

data class ComponentListUiState(
    val receiver: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val service: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val activity: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val provider: SnapshotStateList<ComponentItem> = mutableStateListOf(),
)
