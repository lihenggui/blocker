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

package com.merxury.blocker.feature.applist.impl

import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.data.util.PermissionMonitor
import com.merxury.blocker.core.data.util.PermissionStatus.NO_PERMISSION
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.MAIN
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.InitializeDatabaseUseCase
import com.merxury.blocker.core.domain.applist.SearchAppListUseCase
import com.merxury.blocker.core.domain.controller.GetAppControllerUseCase
import com.merxury.blocker.core.domain.model.InitializeState
import com.merxury.blocker.core.extension.getVersionCode
import com.merxury.blocker.core.model.data.AppItem
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME
import com.merxury.blocker.core.model.preference.AppSorting.NAME
import com.merxury.blocker.core.model.preference.SortingOrder
import com.merxury.blocker.core.model.preference.TopAppType
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.core.ui.data.WarningDialogData
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.merxury.blocker.core.ui.R.string as uiString

@HiltViewModel(assistedFactory = AppListViewModel.Factory::class)
class AppListViewModel @AssistedInject constructor(
    private val pm: PackageManager,
    private val userDataRepository: UserDataRepository,
    private val appRepository: AppRepository,
    private val initializeDatabase: InitializeDatabaseUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val searchAppList: SearchAppListUseCase,
    private val getAppController: GetAppControllerUseCase,
    private val permissionMonitor: PermissionMonitor,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
    @Dispatcher(MAIN) private val mainDispatcher: CoroutineDispatcher,
    @Assisted val initialPackageName: String?,
    private val analyticsHelper: AnalyticsHelper,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppListUiState>(AppListUiState.Initializing())
    val uiState = _uiState.asStateFlow()
    private val _errorState = MutableStateFlow<UiMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private val _warningState = MutableStateFlow<WarningDialogData?>(null)
    val warningState = _warningState.asStateFlow()

    private val selectedPackageKey = "selectedPackageKey"
    private val selectedPackageName: StateFlow<String?> = savedStateHandle.getStateFlow(
        key = selectedPackageKey,
        initialValue = initialPackageName,
    )
    private var showAppSortBottomSheetStatus = false

    // Internal list for storing the displayed app list (data storing)
    private var appList = listOf<AppItem>()

    // Internal list for storing the displayed app list with state (for UI display)
    private var appStateList = mutableStateListOf<AppItem>()

    // Flow to indicate the list is changed
    private val _appListFlow = MutableStateFlow(appStateList)
    val appListFlow: StateFlow<List<AppItem>>
        get() = _appListFlow

    private val refreshServiceJobs = SupervisorJob()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }

    init {
        listenPermissionChanges()
        loadData()
        updateInstalledAppList()
        listenSortingChanges()
        listenTopAppTypeChanges()
        listenShowSystemAppsChanges()
    }

    private var loadAppListJob: Job? = null

    fun loadData(query: String = "") {
        loadAppListJob?.cancel()
        loadAppListJob = viewModelScope.launch(exceptionHandler) {
            // Init DB first to get correct data
            initializeDatabase()
                .takeWhile { it is InitializeState.Initializing }
                .collect {
                    if (it is InitializeState.Initializing) {
                        _uiState.emit(AppListUiState.Initializing(it.processingName))
                    }
                }
            searchAppList(query)
                .onStart {
                    Timber.v("Start loading app list")
                    _uiState.update {
                        if (it is AppListUiState.Success) {
                            it.copy(isRefreshing = true)
                        } else {
                            AppListUiState.Initializing()
                        }
                    }
                }
                .distinctUntilChanged()
                .collect { list ->
                    Timber.v("App list changed, size ${list.size}")
                    refreshServiceJobs.cancelChildren()
                    appList = list
                    appStateList = list.toMutableStateList()
                    _appListFlow.value = appStateList
                    _uiState.emit(
                        AppListUiState.Success(
                            selectedPackageName = selectedPackageName.value,
                            isRefreshing = false,
                            showAppSortBottomSheet = showAppSortBottomSheetStatus,
                        ),
                    )
                }
        }
    }

    fun onAppClick(packageName: String?) {
        savedStateHandle[selectedPackageKey] = packageName
        loadSelectedApp()
    }

    fun showAppSortBottomSheet(showAppSortBottomSheet: Boolean) {
        showAppSortBottomSheetStatus = showAppSortBottomSheet
        _uiState.update {
            if (it is AppListUiState.Success) {
                it.copy(showAppSortBottomSheet = showAppSortBottomSheet)
            } else {
                it
            }
        }
    }

    private fun loadSelectedApp() {
        _uiState.update {
            if (it is AppListUiState.Success) {
                it.copy(selectedPackageName = selectedPackageName.value)
            } else {
                it
            }
        }
    }

    private fun listenPermissionChanges() = viewModelScope.launch {
        permissionMonitor.permissionStatus
            .collect { status ->
                if (status != NO_PERMISSION) {
                    Timber.d("Permission status changed: $status, reload data")
                    loadData()
                }
            }
    }

    private fun appComparator(sortType: AppSorting, sortOrder: SortingOrder): Comparator<AppItem> = if (sortOrder == SortingOrder.ASCENDING) {
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

    private var updateAppListJob: Job? = null
    fun updateInstalledAppList() {
        updateAppListJob?.cancel()
        updateAppListJob = viewModelScope.launch {
            appRepository.updateApplicationList().collect {
                if (it is Result.Error) {
                    _errorState.emit(it.exception.toErrorMessage())
                }
            }
        }
    }

    private var listenSortChangeJob: Job? = null
    private fun listenSortingChanges() {
        listenSortChangeJob?.cancel()
        listenSortChangeJob = viewModelScope.launch(cpuDispatcher) {
            userDataRepository.userData
                .distinctUntilChanged()
                .drop(1)
                .collect { userData ->
                    val newList = appList.toMutableList()
                    newList.sortWith(appComparator(userData.appSorting, userData.appSortingOrder))
                    when (userData.topAppType) {
                        TopAppType.NONE -> {}
                        TopAppType.RUNNING -> newList.sortByDescending { it.isRunning }
                        TopAppType.DISABLED -> newList.sortByDescending { !it.isEnabled }
                    }
                    withContext(mainDispatcher) {
                        refreshServiceJobs.cancelChildren()
                        appList = newList
                        appStateList = newList.toMutableStateList()
                        _appListFlow.value = appStateList
                    }
                }
        }
    }

    private var listenTopAppTypeChangesJob: Job? = null
    private fun listenTopAppTypeChanges() {
        listenTopAppTypeChangesJob?.cancel()
        listenTopAppTypeChangesJob = viewModelScope.launch {
            userDataRepository.userData
                .map { it.topAppType }
                .distinctUntilChanged()
                .drop(1)
                .collect { topAppType ->
                    val newList = appList.toMutableList()
                    val sorting = userDataRepository.userData.first().appSorting
                    val order = userDataRepository.userData.first().appSortingOrder
                    newList.sortWith(appComparator(sorting, order))
                    when (topAppType) {
                        TopAppType.NONE -> {}
                        TopAppType.RUNNING -> newList.sortByDescending { it.isRunning }
                        TopAppType.DISABLED -> newList.sortByDescending { !it.isEnabled }
                    }
                    withContext(mainDispatcher) {
                        refreshServiceJobs.cancelChildren()
                        appList = newList
                        appStateList = newList.toMutableStateList()
                        _appListFlow.value = appStateList
                    }
                }
        }
    }

    private var listenShowSystemAppsChangesJob: Job? = null

    private fun listenShowSystemAppsChanges() {
        listenShowSystemAppsChangesJob?.cancel()
        listenShowSystemAppsChangesJob = viewModelScope.launch {
            userDataRepository.userData
                .map { it.showSystemApps }
                .distinctUntilChanged()
                .drop(1)
                .collect { loadData() }
        }
    }

    fun dismissErrorDialog() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun clearData(packageName: String) = viewModelScope.launch {
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

    fun clearCache(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        getAppController().first().clearCache(packageName)
        analyticsHelper.logClearCacheClicked()
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

    fun forceStop(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        val appController = getAppController().first()
        appController.forceStop(packageName)
        appController.refreshRunningAppList()
        val isRunning = appController.isAppRunning(packageName)
        withContext(mainDispatcher) {
            val item = appList.find { it.packageName == packageName }
            if (item != null) {
                val index = appStateList.indexOf(item)
                if (index != -1) {
                    appStateList[index] = item.copy(isRunning = isRunning)
                }
            }
        }
        analyticsHelper.logForceStopClicked()
    }

    fun dismissWarningDialog() = viewModelScope.launch {
        _warningState.emit(null)
    }

    fun enable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        getAppController().first().enable(packageName)
        notifyAppUpdated(packageName)
        analyticsHelper.logEnableAppClicked()
    }

    fun disable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        getAppController().first().disable(packageName)
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

    @AssistedFactory
    interface Factory {
        fun create(initialPackageName: String?): AppListViewModel
    }
}

sealed interface AppListUiState {
    data class Initializing(val processingName: String = "") : AppListUiState
    data class Error(val error: UiMessage) : AppListUiState
    data class Success(
        val isRefreshing: Boolean = false,
        val selectedPackageName: String? = null,
        val showAppSortBottomSheet: Boolean = false,
    ) : AppListUiState
}
