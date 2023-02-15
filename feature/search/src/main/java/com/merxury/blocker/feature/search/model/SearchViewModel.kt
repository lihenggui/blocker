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
import com.merxury.blocker.core.data.respository.generalrule.GeneralRuleRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.data.GeneralRule
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_DESCENDING
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.feature.search.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val pm: PackageManager,
    private val appRepository: AppRepository,
    private val componentRepository: ComponentRepository,
    private val generalRuleRepository: GeneralRuleRepository,
    private val initializeDatabaseUseCase: InitializeDatabaseUseCase,
    private val userDataRepository: UserDataRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _searchBoxUiState = MutableStateFlow(SearchBoxUiState())
    val searchBoxUiState: StateFlow<SearchBoxUiState> = _searchBoxUiState.asStateFlow()
    private val _localSearchUiState =
        MutableStateFlow<LocalSearchUiState>(LocalSearchUiState.Idle)
    val localSearchUiState: StateFlow<LocalSearchUiState> = _localSearchUiState.asStateFlow()
    private var searchJob: Job? = null

    private val _tabState = MutableStateFlow(
        SearchTabState(
            titles = listOf(
                R.string.application,
                R.string.component,
                R.string.online_rule,
            ),
            currentIndex = 0,
        ),
    )
    val tabState: StateFlow<SearchTabState> = _tabState.asStateFlow()

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        initializeDatabaseUseCase.invoke()
            .collect {
                if (it is InitializeState.Initializing) {
                    _localSearchUiState.emit(LocalSearchUiState.Initializing(it.processingName))
                } else {
                    _localSearchUiState.emit(LocalSearchUiState.Idle)
                }
            }
    }

    fun switchTab(newIndex: Int) {
        if (newIndex != tabState.value.currentIndex) {
            _tabState.update {
                it.copy(currentIndex = newIndex)
            }
        }
    }

    fun search(changedSearchText: TextFieldValue) {
        _searchBoxUiState.update { it.copy(keyword = changedSearchText) }
        val keyword = changedSearchText.text
        val searchAppFlow = appRepository.searchInstalledApplications(keyword)
            .combineTransform(userDataRepository.userData) { list, userSetting ->
                val showSystemApps = userSetting.showSystemApps
                val sorting = userSetting.appSorting
                val filteredList = list.filter { app ->
                    if (showSystemApps) {
                        true
                    } else {
                        !app.isSystem
                    }
                }.sortedWith(
                    when (sorting) {
                        NAME_ASCENDING -> compareBy { it.label }
                        NAME_DESCENDING -> compareByDescending { it.label }
                        FIRST_INSTALL_TIME_ASCENDING -> compareBy { it.firstInstallTime }
                        FIRST_INSTALL_TIME_DESCENDING -> compareByDescending { it.firstInstallTime }
                        LAST_UPDATE_TIME_ASCENDING -> compareBy { it.lastUpdateTime }
                        LAST_UPDATE_TIME_DESCENDING -> compareByDescending { it.lastUpdateTime }
                    },
                ).map { app ->
                    val packageInfo = pm.getPackageInfoCompat(app.packageName, 0)
                    app.toInstalledAppItem(packageInfo)
                }
                emit(filteredList)
            }
        // Organized by <PackageName, List<Component>>
        val searchComponentFlow: Flow<Map<String, List<ComponentInfo>>> =
            componentRepository.searchComponent(keyword)
                .map { list ->
                    list.groupBy { it.packageName }
                }

        val searchGeneralRuleFlow = generalRuleRepository.searchGeneralRule(keyword)
        val searchFlow = combine(
            searchAppFlow,
            searchComponentFlow,
            searchGeneralRuleFlow,
        ) { apps, components, rules ->
            Timber.v("Fild ${apps.size} apps, ${components.size} components, ${rules.size} rules")
            // Group component list by packages
            val filteredComponents = components.map { (packageName, componentList) ->
                val app = appRepository.getApplication(packageName).first()
                if (app != null) {
                    FilteredComponentItem(
                        app = app.toInstalledAppItem(pm.getPackageInfoCompat(packageName, 0)),
                        activity = componentList.filter { it.type == ACTIVITY },
                        service = componentList.filter { it.type == SERVICE },
                        receiver = componentList.filter { it.type == RECEIVER },
                        provider = componentList.filter { it.type == PROVIDER },
                    )
                } else {
                    null
                }
            }
                .filterNotNull()
            LocalSearchUiState.Success(
                apps = apps,
                components = filteredComponents,
                rules = rules,
            )
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchFlow.flowOn(ioDispatcher)
                .collect { searchResult ->
                    _localSearchUiState.emit(searchResult)
                    _tabState.update {
                        it.copy(
                            appCount = searchResult.apps.size,
                            componentCount = searchResult.components.size,
                            rulesCount = searchResult.rules.size,
                        )
                    }
                }
        }
    }

    fun search(keywords: List<String>) {
    }

    fun resetSearchState() {
        _searchBoxUiState.update { SearchBoxUiState() }
    }

    fun selectAll() {
        // TODO
    }

    fun blockAll() {
        // TODO
    }

    fun checkAll() {
        // TODO
    }

    fun switchSelectedMode(value: Boolean) {
        // TODO, isSelectedMode = true
    }

    fun selectItem(select: Boolean) {
        // TODO
    }
}

sealed interface LocalSearchUiState {
    class Initializing(val processingName: String) : LocalSearchUiState
    object Idle : LocalSearchUiState
    object Loading : LocalSearchUiState
    class Success(
        val apps: List<InstalledAppItem> = listOf(),
        val components: List<FilteredComponentItem> = listOf(),
        val rules: List<GeneralRule> = listOf(),
        val isSelectedMode: Boolean = false,
        val selectedAppCount: Int = 0,
    ) : LocalSearchUiState

    class Error(val message: ErrorMessage) : LocalSearchUiState
}

data class SearchBoxUiState(
    val keyword: TextFieldValue = TextFieldValue(),
)

data class FilteredComponentItem(
    val app: InstalledAppItem,
    val activity: List<ComponentInfo> = listOf(),
    val service: List<ComponentInfo> = listOf(),
    val receiver: List<ComponentInfo> = listOf(),
    val provider: List<ComponentInfo> = listOf(),
    val isSelected: Boolean = false,
)

data class SearchTabState(
    val titles: List<Int>,
    val currentIndex: Int,
    val appCount: Int = 0,
    val componentCount: Int = 0,
    val rulesCount: Int = 0,
)
