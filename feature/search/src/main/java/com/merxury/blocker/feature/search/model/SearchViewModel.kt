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

package com.merxury.blocker.feature.search.model

import android.content.pm.PackageManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.component.ComponentRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.SearchGeneralRuleUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.data.InstalledApp
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME
import com.merxury.blocker.core.model.preference.AppSorting.NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.applist.model.AppItem
import com.merxury.blocker.core.ui.applist.model.toAppItem
import com.merxury.blocker.core.ui.component.ComponentItem
import com.merxury.blocker.core.ui.component.toComponentItem
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.feature.search.SearchScreenTabs
import com.merxury.blocker.feature.search.model.LocalSearchUiState.Loading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pm: PackageManager,
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val initializeDatabase: InitializeDatabaseUseCase,
    private val searchRule: SearchGeneralRuleUseCase,
    private val userDataRepository: UserDataRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _searchUiState = MutableStateFlow(SearchUiState())
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()
    private val _localSearchUiState =
        MutableStateFlow<LocalSearchUiState>(LocalSearchUiState.Idle)
    val localSearchUiState: StateFlow<LocalSearchUiState> = _localSearchUiState.asStateFlow()
    private var componentList: MutableList<FilteredComponent> = mutableListOf()
    private var selectedAllTag = false
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }
    private var searchJob: Job? = null

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

    private fun load() = viewModelScope.launch {
        initializeDatabase().collect {
            if (it is InitializeState.Initializing) {
                _localSearchUiState.emit(LocalSearchUiState.Initializing(it.processingName))
            } else {
                _localSearchUiState.emit(LocalSearchUiState.Idle)
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

    fun search(changedSearchText: TextFieldValue) {
        Timber.d("Search components: $changedSearchText")
        _searchUiState.update { it.copy(keyword = changedSearchText) }
        val keyword = changedSearchText.text
        val searchAppFlow = appRepository.searchInstalledApplications(keyword)
            .combineTransform(userDataRepository.userData) { list, userSetting ->
                val showSystemApps = userSetting.showSystemApps
                val sorting = userSetting.appSorting
                val order = userSetting.appSortingOrder
                val filteredList = list.filter { app ->
                    if (showSystemApps) {
                        true
                    } else {
                        !app.isSystem
                    }
                }.sortedWith(
                    appComparator(sorting, order),
                ).map { app ->
                    val packageInfo = pm.getPackageInfoCompat(app.packageName, 0)
                    app.toAppItem(packageInfo)
                }
                emit(filteredList)
            }

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
                                    .filter { it.type == ACTIVITY }
                                    .map { it.toComponentItem() },
                                service = componentList
                                    .filter { it.type == SERVICE }
                                    .map { it.toComponentItem() },
                                receiver = componentList
                                    .filter { it.type == RECEIVER }
                                    .map { it.toComponentItem() },
                                provider = componentList
                                    .filter { it.type == PROVIDER }
                                    .map { it.toComponentItem() },
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
            componentList.addAll(components)
            Timber.v("Find ${apps.size} apps, ${components.size} components, ${rules.size} rules")
            LocalSearchUiState.Success(
                searchKeyword = keyword.split(","),
                appTabUiState = AppTabUiState(list = apps),
                componentTabUiState = ComponentTabUiState(list = components),
                ruleTabUiState = RuleTabUiState(list = rules),
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
                                    count = searchResult.ruleTabUiState.list.size,
                                ),
                            ),
                        )
                    }
                }
        }
    }

    private fun appComparator(
        sortType: AppSorting,
        sortOrder: SortingOrder,
    ): Comparator<InstalledApp> =
        if (sortOrder == SortingOrder.ASCENDING) {
            when (sortType) {
                NAME -> compareBy { it.label.lowercase() }
                FIRST_INSTALL_TIME -> compareBy { it.firstInstallTime }
                LAST_UPDATE_TIME -> compareBy { it.lastUpdateTime }
            }
        } else {
            when (sortType) {
                NAME -> compareByDescending { it.label.lowercase() }
                FIRST_INSTALL_TIME -> compareByDescending { it.firstInstallTime }
                LAST_UPDATE_TIME -> compareByDescending { it.lastUpdateTime }
            }
        }

    fun resetSearchState() {
        _searchUiState.update { it.copy(keyword = TextFieldValue()) }
    }

    fun selectAll() {
        // if selectedAllTag == true, deselect all
        if (selectedAllTag) {
            _searchUiState.update {
                it.copy(selectedAppList = listOf())
            }
        } else {
            _searchUiState.update {
                it.copy(selectedAppList = componentList)
            }
        }
        selectedAllTag = !selectedAllTag
    }

    fun controlAllComponents(enable: Boolean) =
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            componentRepository.batchControlComponent(
                components = _searchUiState.value.selectedComponentList,
                newState = enable,
            )
                .catch { exception ->
                    _errorState.emit(exception.toErrorMessage())
                }
                .collect()
        }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun switchSelectedMode(value: Boolean) {
        // Clear list when exit from selectedMode
        if (!value) {
            _searchUiState.update {
                it.copy(selectedAppList = listOf())
            }
        }
        _searchUiState.update {
            it.copy(isSelectedMode = value)
        }
    }

    fun selectItem(item: FilteredComponent) {
        val selectedList: MutableList<FilteredComponent> = mutableListOf()
        selectedList.addAll(_searchUiState.value.selectedAppList)
        selectedList.add(item)
        _searchUiState.update {
            it.copy(selectedAppList = selectedList)
        }
        transferToComponentInoList()
    }

    fun deselectItem(item: FilteredComponent) {
        val selectedList: MutableList<FilteredComponent> = mutableListOf()
        selectedList.addAll(_searchUiState.value.selectedAppList)
        selectedList.remove(item)
        _searchUiState.update {
            it.copy(selectedAppList = selectedList)
        }
        transferToComponentInoList()
    }

    private fun transferToComponentInoList(): List<ComponentInfo> {
        val list = mutableListOf<ComponentInfo>()
        _searchUiState.value.selectedAppList.forEach { filteredComponent ->
            list.addAll(filteredComponent.activity.map { it.toComponentInfo() })
            list.addAll(filteredComponent.service.map { it.toComponentInfo() })
            list.addAll(filteredComponent.receiver.map { it.toComponentInfo() })
            list.addAll(filteredComponent.provider.map { it.toComponentInfo() })
        }
        _searchUiState.update {
            it.copy(selectedComponentList = list)
        }
        return list
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
)

data class ComponentTabUiState(
    val list: List<FilteredComponent> = listOf(),
)

data class RuleTabUiState(
    val list: List<GeneralRule> = listOf(),
)

data class FilteredComponent(
    val app: AppItem,
    val activity: List<ComponentItem> = listOf(),
    val service: List<ComponentItem> = listOf(),
    val receiver: List<ComponentItem> = listOf(),
    val provider: List<ComponentItem> = listOf(),
)

data class SearchUiState(
    val keyword: TextFieldValue = TextFieldValue(),
    val isSelectedMode: Boolean = false,
    val selectedAppList: List<FilteredComponent> = listOf(),
    val selectedComponentList: List<ComponentInfo> = listOf(),
)
