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

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.ComponentType.ACTIVITY
import com.merxury.blocker.core.model.ComponentType.PROVIDER
import com.merxury.blocker.core.model.ComponentType.RECEIVER
import com.merxury.blocker.core.model.ComponentType.SERVICE
import com.merxury.blocker.core.model.data.ComponentInfo
import com.merxury.blocker.core.model.preference.ComponentSorting
import com.merxury.blocker.core.model.preference.ComponentSorting.NAME_ASCENDING
import com.merxury.blocker.core.model.preference.ComponentSorting.NAME_DESCENDING
import com.merxury.blocker.core.ui.AppDetailTabs
import com.merxury.blocker.core.ui.AppDetailTabs.Activity
import com.merxury.blocker.core.ui.AppDetailTabs.Info
import com.merxury.blocker.core.ui.AppDetailTabs.Provider
import com.merxury.blocker.core.ui.AppDetailTabs.Receiver
import com.merxury.blocker.core.ui.AppDetailTabs.Service
import com.merxury.blocker.core.ui.TabState
import com.merxury.blocker.core.ui.component.ComponentItem
import com.merxury.blocker.core.ui.component.toComponentItem
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.ServiceHelper
import com.merxury.blocker.feature.appdetail.AppInfoUiState.Loading
import com.merxury.blocker.feature.appdetail.model.AppBarAction
import com.merxury.blocker.feature.appdetail.model.AppBarAction.MORE
import com.merxury.blocker.feature.appdetail.model.AppBarAction.SEARCH
import com.merxury.blocker.feature.appdetail.model.AppBarAction.SHARE_RULE
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    app: android.app.Application,
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder,
    private val userDataRepository: UserDataRepository,
    private val componentRepository: LocalComponentRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) : AndroidViewModel(app) {
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
    private var _unfilteredList = ComponentListUiState()
    private val _componentListUiState = MutableStateFlow(ComponentListUiState())
    val componentListUiState = _componentListUiState.asStateFlow()
    private val _errorState = MutableStateFlow<ErrorMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }

    init {
        updateSearchKeyword()
        loadTabInfo()
        loadAppInfo()
        loadComponentList()
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
            .collect { list ->
                // Store the unfiltered list
                val receiver = list.filter { it.type == RECEIVER }
                val service = list.filter { it.type == SERVICE }
                val activity = list.filter { it.type == ACTIVITY }
                val provider = list.filter { it.type == PROVIDER }
                _unfilteredList =
                    getComponentListUiState(packageName, receiver, service, activity, provider)
                filterAndUpdateComponentList(appDetailArgs.searchKeyword.joinToString(","))
                updateTabState(_componentListUiState.value)
            }
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
            .sortedWith(componentComparator(sorting))
            .sortedByDescending { it.isRunning }
            .toMutableStateList()
    }

    private fun componentComparator(sort: ComponentSorting): Comparator<ComponentItem> {
        return when (sort) {
            NAME_ASCENDING -> compareBy { it.simpleName }
            NAME_DESCENDING -> compareByDescending { it.simpleName }
        }
    }

    private fun updateSearchKeyword() {
        val keyword = appDetailArgs.searchKeyword
        if (keyword.isEmpty()) return
        val keywordString = appDetailArgs.searchKeyword.joinToString(",")
        Timber.v("Search keyword: $keyword")
        _appBarUiState.update {
            it.copy(keyword = TextFieldValue(keywordString), isSearchMode = true)
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

    fun launchApp(packageName: String) {
        Timber.i("Launch app $packageName")
        val context: Context = getApplication()
        context.packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
            context.startActivity(launchIntent)
        }
    }

    fun changeSearchMode(isSearchMode: Boolean) {
        Timber.v("Change search mode: $isSearchMode")
        _appBarUiState.update {
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
        }

    fun dismissAlert() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun launchActivity(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am start -n $packageName/$componentName".exec(ioDispatcher)
        }
    }

    fun stopService(packageName: String, componentName: String) {
        viewModelScope.launch(ioDispatcher + exceptionHandler) {
            "am stopservice $packageName/$componentName".exec(ioDispatcher)
        }
    }

    fun controlComponent(
        packageName: String,
        componentName: String,
        enabled: Boolean,
    ) = viewModelScope.launch {
        controlComponentInternal(packageName, componentName, enabled)
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

    fun exportRule(packageName: String) {
        Timber.d("Export Blocker rule for $packageName")
    }

    fun importRule(packageName: String) {
        Timber.d("Import Blocker rule for $packageName")
    }

    fun exportIfw(packageName: String) {
        Timber.d("Export IFW rule for $packageName")
    }

    fun importIfw(packageName: String) {
        Timber.d("Import IFW rule for $packageName")
    }

    fun resetIfw(packageName: String) {
        Timber.d("Reset IFW rule for $packageName")
    }

    private fun loadAppInfo() = viewModelScope.launch {
        val packageName = appDetailArgs.packageName
        val app = ApplicationUtil.getApplicationInfo(getApplication(), packageName)
        if (app == null) {
            val error = ErrorMessage("Can't find $packageName in this device.")
            Timber.e(error.message)
            _appInfoUiState.emit(AppInfoUiState.Error(error))
        } else {
            _appInfoUiState.emit(AppInfoUiState.Success(app))
        }
    }
}

sealed interface AppInfoUiState {
    object Loading : AppInfoUiState
    class Error(val error: ErrorMessage) : AppInfoUiState
    data class Success(
        val appInfo: Application,
    ) : AppInfoUiState
}

data class AppBarUiState(
    val keyword: TextFieldValue = TextFieldValue(),
    val isSearchMode: Boolean = false,
    val actions: List<AppBarAction> = listOf(),
)

data class ComponentListUiState(
    val receiver: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val service: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val activity: SnapshotStateList<ComponentItem> = mutableStateListOf(),
    val provider: SnapshotStateList<ComponentItem> = mutableStateListOf(),
)
