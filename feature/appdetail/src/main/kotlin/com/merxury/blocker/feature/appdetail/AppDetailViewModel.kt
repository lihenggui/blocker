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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
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
import com.merxury.blocker.core.controllers.di.RootApiServiceControl
import com.merxury.blocker.core.controllers.di.ShizukuServiceControl
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.IComponentDetailRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.ZipAllRuleUseCase
import com.merxury.blocker.core.domain.ZipAppRuleUseCase
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.ComponentItem
import com.merxury.blocker.core.model.data.ControllerType.IFW
import com.merxury.blocker.core.model.data.ControllerType.SHIZUKU
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.model.data.toComponentItem
import com.merxury.blocker.core.model.preference.ComponentShowPriority.DISABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.ENABLED_COMPONENTS_FIRST
import com.merxury.blocker.core.model.preference.ComponentShowPriority.NONE
import com.merxury.blocker.core.model.preference.ComponentSorting.COMPONENT_NAME
import com.merxury.blocker.core.model.preference.ComponentSorting.PACKAGE_NAME
import com.merxury.blocker.core.model.preference.SortingOrder.ASCENDING
import com.merxury.blocker.core.model.preference.SortingOrder.DESCENDING
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
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.MORE
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SEARCH
import com.merxury.blocker.core.ui.state.toolbar.AppBarAction.SHARE_RULE
import com.merxury.blocker.core.ui.state.toolbar.AppBarUiState
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Loading
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val componentDetailRepository: IComponentDetailRepository,
    @RootApiServiceControl private val rootApiServiceController: IServiceController,
    @ShizukuServiceControl private val shizukuServiceController: IServiceController,
    private val zipAllRuleUseCase: ZipAllRuleUseCase,
    private val zipAppRuleUseCase: ZipAppRuleUseCase,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val appDetailArgs: AppDetailArgs = AppDetailArgs(savedStateHandle)
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
    private var unfilteredList = ComponentListUiState()
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
    private var loadComponentListJob: Job? = null
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
    }

    fun search(keyword: String) {
        if (keyword == _appBarUiState.value.keyword) return
        _appBarUiState.update { it.copy(keyword = keyword) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(cpuDispatcher + exceptionHandler) {
            Timber.i("Filtering component list with keyword: $keyword")
            filterAndUpdateComponentList(keyword)
            updateTabState(_componentListUiState.value)
        }
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
            _componentListUiState.emit(unfilteredList)
            return
        }
        val receiver = mutableStateListOf<ComponentItem>()
        val service = mutableStateListOf<ComponentItem>()
        val activity = mutableStateListOf<ComponentItem>()
        val provider = mutableStateListOf<ComponentItem>()
        currentFilterKeyword.forEach { subKeyword ->
            val filteredReceiver = unfilteredList.receiver
                .filter { it.name.contains(subKeyword, ignoreCase = true) }
            val filteredService = unfilteredList.service
                .filter { it.name.contains(subKeyword, ignoreCase = true) }
            val filteredActivity = unfilteredList.activity
                .filter { it.name.contains(subKeyword, ignoreCase = true) }
            val filteredProvider = unfilteredList.provider
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

    fun loadComponentList() {
        loadComponentListJob?.cancel()
        loadComponentListJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val packageName = appDetailArgs.packageName
            Timber.v("Start loading component: $packageName")
            componentRepository.getComponentList(packageName)
                // Take 2 events only
                // The first one is the initial loading
                // The second one is the updated data from `updateComponentList()`
                // It will be updated separately for single component event
                .take(2)
                .collect { componentList ->
                    // Load the data with description and then update the ui
                    val listWithDescription = componentList.map { component ->
                        val detail =
                            componentDetailRepository.getLocalComponentDetail(component.name)
                                .first()
                        if (detail != null) {
                            component.copy(description = detail.description)
                        } else {
                            component
                        }
                    }
                    updateTabContent(listWithDescription)
                    withContext(mainDispatcher) {
                        _componentListUiState.update {
                            it.copy(isRefreshing = false)
                        }
                    }
                }
        }
    }

    private suspend fun updateTabContent(list: List<ComponentInfo>) {
        // Store the unfiltered list
        val receiver = list.filter { it.type == RECEIVER }
        val service = list.filter { it.type == SERVICE }
        val activity = list.filter { it.type == ACTIVITY }
        val provider = list.filter { it.type == PROVIDER }
        unfilteredList = getComponentListUiState(receiver, service, activity, provider)
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

    fun updateComponentList() = viewModelScope.launch {
        val packageName = appDetailArgs.packageName
        componentRepository.updateComponentList(packageName)
            .catch { _errorState.emit(it.toErrorMessage()) }
            .collect()
    }

    private suspend fun getComponentListUiState(
        receiver: List<ComponentInfo>,
        service: List<ComponentInfo>,
        activity: List<ComponentInfo>,
        provider: List<ComponentInfo>,
    ) = ComponentListUiState(
        receiver = sortAndConvertToComponentItem(
            list = receiver,
            type = RECEIVER,
        ),
        service = sortAndConvertToComponentItem(
            list = service,
            type = SERVICE,
        ),
        activity = sortAndConvertToComponentItem(
            list = activity,
            type = ACTIVITY,
        ),
        provider = sortAndConvertToComponentItem(
            list = provider,
            type = PROVIDER,
        ),
    )

    private suspend fun sortAndConvertToComponentItem(
        list: List<ComponentInfo>,
        type: ComponentType,
        filterKeyword: String = "",
    ): SnapshotStateList<ComponentItem> {
        val userData = userDataRepository.userData.first()
        val sorting = userData.componentSorting
        val order = userData.componentSortingOrder
        val serviceController = if (userData.controllerType == SHIZUKU) {
            shizukuServiceController
        } else {
            rootApiServiceController
        }
        serviceController.load()
        return list.filter { it.name.contains(filterKeyword, ignoreCase = true) }
            .map {
                it.toComponentItem(
                    if (type == SERVICE) {
                        serviceController.isServiceRunning(it.packageName, it.name)
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
            .let { sortedList ->
                sortedList.sortedByDescending { it.isRunning }
            }
            .toMutableStateList()
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

    private fun loadTabInfo() = viewModelScope.launch(mainDispatcher) {
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

        else -> listOf(SEARCH, MORE)
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
            loadComponentList()
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

    fun controlAllComponents(enable: Boolean, block: suspend (Int, Int) -> Unit) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            val list = when (tabState.value.selectedItem) {
                Receiver -> _componentListUiState.value.receiver
                Service -> _componentListUiState.value.service
                Activity -> _componentListUiState.value.activity
                Provider -> _componentListUiState.value.provider
                else -> return@launch
            }.map {
                it.toComponentInfo()
            }
            var successCount = 0
            componentRepository.batchControlComponent(
                components = list,
                newState = enable,
            )
                .catch { exception ->
                    _errorState.emit(exception.toErrorMessage())
                }
                .collect { component ->
                    val type = findComponentType(component.name)
                    changeComponentUiStatus(component.name, type, enable)
                    successCount++
                    block(successCount, list.size)
                }
            analyticsHelper.logBatchOperationPerformed(enable)
        }
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
            val controllerType = userDataRepository.userData.first().controllerType
            val serviceController = if (controllerType == SHIZUKU) {
                shizukuServiceController
            } else {
                rootApiServiceController
            }
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
        serviceController.load()
        val isRunning = serviceController.isServiceRunning(packageName, componentName)
        val item = _componentListUiState.value.service.find { it.name == componentName }
        if (item == null) {
            Timber.w("Cannot find service $componentName to update")
            return
        }
        val newStatus = item.copy(isRunning = isRunning)
        Timber.d("Update service $componentName running status to $newStatus")
        withContext(mainDispatcher) {
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
    }

    fun controlComponent(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            controlComponentInternal(packageName, componentName, enabled)
            analyticsHelper.logSwitchComponentClicked(newState = enabled)
        }
    }

    fun controlAllSelectedComponents(enable: Boolean) {
        controlComponentJob?.cancel()
        controlComponentJob = viewModelScope.launch(ioDispatcher + exceptionHandler) {
            componentRepository.batchControlComponent(
                components = _appBarUiState.value.selectedComponentList,
                newState = enable,
            )
                .catch { exception ->
                    _errorState.emit(exception.toErrorMessage())
                }
                .collect { component ->
                    val type = findComponentType(component.name)
                    changeComponentUiStatus(component.name, type, enable)
                }
            withContext(mainDispatcher) {
                _appBarUiState.update {
                    it.copy(selectedComponentList = listOf())
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
        return when (tabState.value.selectedItem) {
            Receiver -> _componentListUiState.value.receiver.map { it.toComponentInfo() }
            Service -> _componentListUiState.value.service.map { it.toComponentInfo() }
            Activity -> _componentListUiState.value.activity.map { it.toComponentInfo() }
            Provider -> _componentListUiState.value.provider.map { it.toComponentInfo() }
            else -> listOf()
        }.toMutableList()
    }

    private suspend fun findComponentType(componentName: String): ComponentType {
        return withContext(cpuDispatcher) {
            val currentList = _componentListUiState.value
            val receiver = currentList.receiver.find { it.name == componentName }
            if (receiver != null) {
                return@withContext RECEIVER
            }
            val service = currentList.service.find { it.name == componentName }
            if (service != null) {
                return@withContext SERVICE
            }
            val activity = currentList.activity.find { it.name == componentName }
            if (activity != null) {
                return@withContext ACTIVITY
            }
            val provider = currentList.provider.find { it.name == componentName }
            if (provider != null) {
                return@withContext PROVIDER
            }
            // Should be unreachable code
            throw IllegalStateException("Cannot find component type for $componentName")
        }
    }

    private suspend fun changeComponentUiStatus(
        componentName: String,
        type: ComponentType,
        enable: Boolean,
    ) {
        withContext(cpuDispatcher) {
            val currentController = userDataRepository.userData.first().controllerType
            val list = when (type) {
                RECEIVER -> _componentListUiState.value.receiver
                SERVICE -> _componentListUiState.value.service
                ACTIVITY -> _componentListUiState.value.activity
                PROVIDER -> _componentListUiState.value.provider
            }
            val position = list.indexOfFirst { it.name == componentName }
            if (position == -1) {
                Timber.w("Cannot find component $componentName in the list")
                return@withContext
            }
            withContext(mainDispatcher) {
                list[position] = if (currentController == IFW && type != PROVIDER) {
                    list[position].copy(ifwBlocked = !enable)
                } else {
                    list[position].copy(pmBlocked = !enable)
                }
            }
        }
    }

    private suspend fun controlComponentInternal(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) {
        val type = findComponentType(componentName)
        val result = componentRepository.controlComponent(packageName, componentName, enabled)
            .onStart {
                changeComponentUiStatus(componentName, type, enabled)
            }
            .catch { exception ->
                changeComponentUiStatus(componentName, type, !enabled)
                _errorState.emit(exception.toErrorMessage())
            }
            .first()
        if (!result) {
            changeComponentUiStatus(componentName, type, !enabled)
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
            val error = UiMessage("Can't find $packageName in this device.")
            Timber.e(error.title)
            _appInfoUiState.emit(AppInfoUiState.Error(error))
        } else {
            val packageInfo = pm.getPackageInfoCompat(packageName, 0)
            val userData = userDataRepository.userData.first()
            _appInfoUiState.emit(
                AppInfoUiState.Success(
                    appInfo = app.toAppItem(packageInfo = packageInfo),
                    iconBasedTheming = if (userData.useDynamicColor) {
                        getAppIcon(packageInfo)
                    } else {
                        null
                    },
                    isLibCheckerInstalled = isLibCheckerInstalled,
                ),
            )
        }
    }

    private fun listenComponentDetailChanges() = viewModelScope.launch {
        componentDetailRepository.listenToComponentDetailChanges().collect {
            updateComponentDetail(it)
        }
    }

    // TODO Refactor this function to remove duplications
    private suspend fun updateComponentDetail(componentDetail: ComponentDetail) {
        Timber.v("Update component detail: $componentDetail")
        val currentState = _componentListUiState.value.copy()
        currentState.receiver.find { it.name == componentDetail.name }
            ?.let { item ->
                val index = currentState.receiver.indexOf(item)
                if (index == -1) {
                    Timber.w("Cannot find receiver ${componentDetail.name} to update")
                    return
                }
                withContext(mainDispatcher) {
                    _componentListUiState.update {
                        it.copy(
                            receiver = it.receiver.toMutableStateList().apply {
                                set(index, item.copy(description = componentDetail.description))
                            },
                        )
                    }
                }
            }
        currentState.service.find { it.name == componentDetail.name }
            ?.let { item ->
                val index = currentState.service.indexOf(item)
                if (index == -1) {
                    Timber.w("Cannot find service ${componentDetail.name} to update")
                    return
                }
                withContext(mainDispatcher) {
                    _componentListUiState.update {
                        it.copy(
                            service = it.service.toMutableStateList().apply {
                                set(index, item.copy(description = componentDetail.description))
                            },
                        )
                    }
                }
            }
        currentState.activity.find { it.name == componentDetail.name }
            ?.let { item ->
                val index = currentState.activity.indexOf(item)
                if (index == -1) {
                    Timber.w("Cannot find activity ${componentDetail.name} to update")
                    return
                }
                withContext(mainDispatcher) {
                    _componentListUiState.update {
                        it.copy(
                            activity = it.activity.toMutableStateList().apply {
                                set(index, item.copy(description = componentDetail.description))
                            },
                        )
                    }
                }
            }
        currentState.provider.find { it.name == componentDetail.name }
            ?.let { item ->
                val index = currentState.provider.indexOf(item)
                if (index == -1) {
                    Timber.w("Cannot find provider ${componentDetail.name} to update")
                    return
                }
                withContext(mainDispatcher) {
                    _componentListUiState.update {
                        it.copy(
                            provider = it.provider.toMutableStateList().apply {
                                set(index, item.copy(description = componentDetail.description))
                            },
                        )
                    }
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

sealed interface AppInfoUiState {
    data object Loading : AppInfoUiState
    data class Error(val error: UiMessage) : AppInfoUiState
    data class Success(
        val appInfo: AppItem,
        val iconBasedTheming: Bitmap?,
        val isLibCheckerInstalled: Boolean = false,
    ) : AppInfoUiState
}

data class ComponentListUiState(
    val receiver: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val service: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val activity: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val provider: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val isRefreshing: Boolean = false,
)
