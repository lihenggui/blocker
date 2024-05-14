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

package com.merxury.blocker.feature.search

import android.content.pm.PackageManager
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.applist.SearchAppListUseCase
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.extension.getVersionCode
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.FilteredComponent
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.toAppItem
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.SearchScreenTabs
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.WarningDialogData
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.feature.search.LocalSearchUiState.Idle
import com.merxury.blocker.feature.search.LocalSearchUiState.Initializing
import com.merxury.blocker.feature.search.LocalSearchUiState.Loading
import com.merxury.blocker.feature.search.LocalSearchUiState.Success
import com.merxury.blocker.feature.search.navigation.PACKAGE_NAME_ARG
import com.merxury.blocker.feature.search.navigation.RULE_ID_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.merxury.blocker.core.ui.R.string as uiString

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pm: PackageManager,
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val initializeDatabase: InitializeDatabaseUseCase,
    private val searchAppList: SearchAppListUseCase,
    private val searchRule: SearchGeneralRuleUseCase,
    private val getAppController: GetAppControllerUseCase,
    private val userDataRepository: UserDataRepository,
    private val analyticsHelper: AnalyticsHelper,
    private val savedStateHandle: SavedStateHandle,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _searchUiState = MutableStateFlow(SearchUiState())
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()
    private val _localSearchUiState =
        MutableStateFlow<LocalSearchUiState>(Idle)
    val localSearchUiState: StateFlow<LocalSearchUiState> = _localSearchUiState.asStateFlow()
    private var filterComponentList: MutableList<FilteredComponent> = mutableListOf()
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val _warningState = MutableStateFlow<WarningDialogData?>(null)
    val warningState = _warningState.asStateFlow()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }
    private var searchJob: Job? = null
    private var loadAppJob: Job? = null
    private val selectedRuleId: StateFlow<String?> = savedStateHandle.getStateFlow(
        RULE_ID_ARG,
        null,
    )
    private val selectedPackageName: StateFlow<String?> = savedStateHandle.getStateFlow(
        PACKAGE_NAME_ARG,
        null,
    )

    private val _tabState = MutableStateFlow(
        TabState(
            items = listOf(
                SearchScreenTabs.App(),
                SearchScreenTabs.Component(),
                SearchScreenTabs.Rule(),
            ),
            selectedItem = SearchScreenTabs.App(),
        ),
    )
    val tabState: StateFlow<TabState<SearchScreenTabs>> = _tabState.asStateFlow()

    init {
        load()
    }

    @VisibleForTesting
    fun load() {
        loadAppJob?.cancel()
        loadAppJob = viewModelScope.launch {
            initializeDatabase().collect {
                if (it is InitializeState.Initializing) {
                    _localSearchUiState.emit(Initializing(it.processingName))
                } else {
                    _localSearchUiState.emit(Idle)
                }
            }
        }
    }

    fun onAppClick(packageName: String?) {
        savedStateHandle[PACKAGE_NAME_ARG] = packageName
        loadSelectedItem()
    }

    fun onComponentClick(packageName: String?) {
        savedStateHandle[PACKAGE_NAME_ARG] = packageName
        loadSelectedItem()
    }

    fun onRuleClick(ruleId: String?) {
        savedStateHandle[RULE_ID_ARG] = ruleId
        loadSelectedItem()
    }

    private fun loadSelectedItem() {
        _localSearchUiState.update {
            if (it is Success) {
                it.copy(
                    appTabUiState = it.appTabUiState.copy(selectedPackageName = selectedPackageName.value),
                    componentTabUiState = it.componentTabUiState.copy(selectedPackageName = selectedPackageName.value),
                    ruleTabUiState = it.ruleTabUiState.copy(selectedRuleId = selectedRuleId.value),
                )
            } else {
                it
            }
        }
    }

    fun switchTab(newTab: SearchScreenTabs) {
        if (newTab != tabState.value.selectedItem) {
            _tabState.update {
                it.copy(selectedItem = newTab)
            }
        }
    }

    fun search(keyword: String) {
        Timber.d("Search components: $keyword")
        if (keyword == _searchUiState.value.keyword) {
            return
        }
        _searchUiState.update { it.copy(keyword = keyword) }
        val searchAppFlow = searchAppList(keyword)
        val searchComponentFlow: Flow<List<FilteredComponent>> =
            componentRepository.searchComponent(keyword)
                .map { list ->
                    list.groupBy { it.packageName }
                        .toSortedMap()
                        .map MapToUiModel@{ (packageName, componentList) ->
                            // Map to UI model
                            val app = appRepository.getApplication(packageName).first()
                                ?: return@MapToUiModel null
                            if (!userDataRepository.userData.first().showSystemApps) {
                                if (app.isSystem) {
                                    // If user doesn't want to show system apps, skip it
                                    return@MapToUiModel null
                                }
                            }
                            Timber.v("Found ${componentList.size} components for $packageName")
                            FilteredComponent(
                                app = app.toAppItem(
                                    packageInfo = pm.getPackageInfoCompat(packageName, 0),
                                ),
                                activity = componentList
                                    .filter { it.type == ACTIVITY },
                                service = componentList
                                    .filter { it.type == SERVICE },
                                receiver = componentList
                                    .filter { it.type == RECEIVER },
                                provider = componentList
                                    .filter { it.type == PROVIDER },
                            )
                        }
                        .filterNotNull()
                }

        val searchGeneralRuleFlow = searchRule(keyword)
        val searchFlow = combine(
            searchAppFlow,
            searchComponentFlow,
            searchGeneralRuleFlow,
        ) { apps, components, rules ->
            filterComponentList.clear()
            filterComponentList.addAll(components)
            Timber.v("Find ${apps.size} apps, ${components.size} components, ${rules.size} rules")
            val matchedRules = rules.filter { it.matchedAppCount > 0 }
            val unmatchedRules = rules.filter { it.matchedAppCount == 0 }
            Success(
                searchKeyword = keyword.split(","),
                appTabUiState = AppTabUiState(
                    list = apps,
                    selectedPackageName = selectedPackageName.value,
                ),
                componentTabUiState = ComponentTabUiState(
                    list = components,
                    selectedPackageName = selectedPackageName.value,
                ),
                ruleTabUiState = RuleTabUiState(
                    matchedRules = matchedRules,
                    unmatchedRules = unmatchedRules,
                    selectedRuleId = selectedRuleId.value,
                ),
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchFlow.flowOn(ioDispatcher)
                .onStart {
                    _localSearchUiState.emit(Loading)
                }
                .collect { searchResult ->
                    _localSearchUiState.emit(searchResult)
                    val ruleCount = searchResult.ruleTabUiState.matchedRules.size +
                        searchResult.ruleTabUiState.unmatchedRules.size
                    _tabState.update {
                        it.copy(
                            items = listOf(
                                SearchScreenTabs.App(
                                    count = searchResult.appTabUiState.list.size,
                                ),
                                SearchScreenTabs.Component(
                                    count = searchResult.componentTabUiState.list.size,
                                ),
                                SearchScreenTabs.Rule(
                                    count = ruleCount,
                                ),
                            ),
                        )
                    }
                }
        }
    }

    fun controlAllSelectedComponents(enable: Boolean, action: (Int, Int) -> Unit) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            var current = 0
            val list = searchUiState.value.selectedComponentList
            componentRepository.batchControlComponent(
                components = list,
                newState = enable,
            )
                .catch { exception ->
                    _errorState.emit(exception.toErrorMessage())
                }
                .onCompletion {
                    switchSelectedMode(false)
                }
                .collect {
                    action(++current, list.size)
                }
        }
    }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun switchSelectedMode(value: Boolean) {
        // Clear list when exit from selectedMode
        if (!value) {
            _searchUiState.update {
                it.copy(selectedAppList = listOf(), selectedComponentList = listOf())
            }
        }
        _searchUiState.update {
            it.copy(isSelectedMode = value)
        }
    }

    fun selectItem(item: FilteredComponent) {
        val selectedList: MutableList<FilteredComponent> =
            _searchUiState.value.selectedAppList.toMutableList()
        selectedList.add(item)
        _searchUiState.update {
            it.copy(selectedAppList = selectedList)
        }
        transferToComponentInfoList()
    }

    fun deselectItem(item: FilteredComponent) {
        val selectedList: MutableList<FilteredComponent> =
            _searchUiState.value.selectedAppList.toMutableList()
        selectedList.remove(item)
        _searchUiState.update {
            it.copy(selectedAppList = selectedList)
        }
        transferToComponentInfoList()
    }

    fun selectAll() {
        val selectedAll = _searchUiState.value.selectedAppList.size == filterComponentList.size
        // if selectedAll == true, deselect all
        if (selectedAll) {
            _searchUiState.update {
                it.copy(selectedAppList = listOf())
            }
        } else {
            _searchUiState.update {
                it.copy(selectedAppList = filterComponentList)
            }
        }
        transferToComponentInfoList()
    }

    private fun transferToComponentInfoList(): List<ComponentInfo> {
        val list = mutableListOf<ComponentInfo>()
        if (_searchUiState.value.selectedAppList.isNotEmpty()) {
            _searchUiState.value.selectedAppList.forEach { filteredComponent ->
                list.addAll(filteredComponent.activity)
                list.addAll(filteredComponent.service)
                list.addAll(filteredComponent.receiver)
                list.addAll(filteredComponent.provider)
            }
        }
        _searchUiState.update {
            it.copy(selectedComponentList = list)
        }
        return list
    }

    fun clearCache(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        getAppController().first()
            .clearCache(packageName)
        analyticsHelper.logClearCacheClicked()
    }

    fun clearData(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        val action: () -> Unit = {
            viewModelScope.launch(ioDispatcher + exceptionHandler) {
                getAppController().first().clearData(packageName)
                analyticsHelper.logClearDataClicked()
            }
        }
        val label = appRepository.getApplication(packageName)
            .flowOn(ioDispatcher)
            .first()
            ?.label
            ?: packageName
        val data = WarningDialogData(
            title = label,
            message = uiString.core_ui_do_you_want_to_clear_data_of_this_app,
            onPositiveButtonClicked = action,
        )
        _warningState.emit(data)
    }

    fun forceStop(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        getAppController().first()
            .forceStop(packageName)
        analyticsHelper.logForceStopClicked()
    }

    fun uninstall(packageName: String) = viewModelScope.launch {
        val action: () -> Unit = {
            viewModelScope.launch(ioDispatcher + exceptionHandler) {
                val app = ApplicationUtil.getApplicationComponents(pm, packageName)
                val versionCode = app.getVersionCode()
                getAppController().first().uninstallApp(packageName, versionCode)
                notifyAppUpdated(packageName)
                analyticsHelper.logUninstallAppClicked()
            }
        }
        val label = appRepository.getApplication(packageName)
            .flowOn(ioDispatcher)
            .first()
            ?.label
            ?: packageName
        val data = WarningDialogData(
            title = label,
            message = uiString.core_ui_do_you_want_to_uninstall_this_app,
            onPositiveButtonClicked = action,
        )
        _warningState.emit(data)
    }

    fun enable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        getAppController().first()
            .enable(packageName)
        notifyAppUpdated(packageName)
        analyticsHelper.logEnableAppClicked()
    }

    fun disable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        getAppController().first()
            .disable(packageName)
        notifyAppUpdated(packageName)
        analyticsHelper.logDisableAppClicked()
    }

    private suspend fun notifyAppUpdated(packageName: String) {
        appRepository.updateApplication(packageName)
            .takeWhile { it !is Result.Success }
            .collect {
                if (it is Result.Error) {
                    _errorState.emit(it.exception.toErrorMessage())
                }
            }
        Timber.v("App updated: $packageName")
    }

    fun dismissWarningDialog() = viewModelScope.launch {
        _warningState.emit(null)
    }
}

sealed interface LocalSearchUiState {
    class Initializing(val processingName: String) : LocalSearchUiState
    data object Idle : LocalSearchUiState
    data object Loading : LocalSearchUiState
    data class Success(
        val searchKeyword: List<String> = listOf(),
        val appTabUiState: AppTabUiState = AppTabUiState(),
        val componentTabUiState: ComponentTabUiState = ComponentTabUiState(),
        val ruleTabUiState: RuleTabUiState = RuleTabUiState(),
    ) : LocalSearchUiState

    class Error(val uiMessage: UiMessage) : LocalSearchUiState
}

data class AppTabUiState(
    val list: List<AppItem> = listOf(),
    val selectedPackageName: String? = null,
)

data class ComponentTabUiState(
    val list: List<FilteredComponent> = listOf(),
    val selectedPackageName: String? = null,
)

data class RuleTabUiState(
    val matchedRules: List<GeneralRule> = listOf(),
    val unmatchedRules: List<GeneralRule> = listOf(),
    val selectedRuleId: String? = null,
)

data class SearchUiState(
    val keyword: String = "",
    val isSelectedMode: Boolean = false,
    val selectedAppList: List<FilteredComponent> = listOf(),
    val selectedComponentList: List<ComponentInfo> = listOf(),
)
