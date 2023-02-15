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

package com.merxury.blocker.feature.applist

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_DESCENDING
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.core.ui.data.toErrorMessage
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.feature.applist.AppListUiState.Success
import com.merxury.blocker.feature.applist.state.AppStateCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AppListViewModel @Inject constructor(
    app: android.app.Application,
    private val pm: PackageManager,
    private val userDataRepository: UserDataRepository,
    private val appRepository: AppRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher,
) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow<AppListUiState>(AppListUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private val _errorState = MutableStateFlow<ErrorMessage?>(null)
    val errorState = _errorState.asStateFlow()
    private var _appList = mutableStateListOf<AppItem>()
    private val _appListFlow = MutableStateFlow(_appList)
    val appListFlow: StateFlow<List<AppItem>>
        get() = _appListFlow
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }

    init {
        loadData()
        updateInstalledAppList()
        listenSortingChanges()
        listenShowSystemAppsChanges()
    }

    fun loadData() = viewModelScope.launch(cpuDispatcher + exceptionHandler) {
        appRepository.getApplicationList()
            .onStart {
                _uiState.emit(AppListUiState.Loading)
            }
            .distinctUntilChanged()
            .collect { list ->
                Timber.v("App list changed, size ${list.size}")
                val preference = userDataRepository.userData.first()
                val sortType = preference.appSorting
                _appList = if (preference.showSystemApps) {
                    list
                } else {
                    list.filterNot { it.isSystem }
                }.map { installedApp ->
                    AppItem(
                        label = installedApp.label,
                        packageName = installedApp.packageName,
                        versionName = installedApp.versionName,
                        versionCode = installedApp.versionCode,
                        isSystem = ApplicationUtil.isSystemApp(pm, installedApp.packageName),
                        // TODO detect if an app is running or not
                        isRunning = false,
                        enabled = installedApp.isEnabled,
                        firstInstallTime = installedApp.firstInstallTime,
                        lastUpdateTime = installedApp.lastUpdateTime,
                        appServiceStatus = null,
                        packageInfo = pm.getPackageInfoCompat(installedApp.packageName, 0),
                    )
                }.sortedWith(
                    appComparator(sortType),
                ).toMutableStateList()
                _appListFlow.value = _appList
                _uiState.emit(Success)
            }
    }

    private fun appComparator(sortType: AppSorting): Comparator<AppItem> =
        when (sortType) {
            NAME_ASCENDING -> compareBy { it.label.lowercase() }
            NAME_DESCENDING -> compareByDescending { it.label.lowercase() }
            FIRST_INSTALL_TIME_ASCENDING -> compareBy { it.firstInstallTime }
            FIRST_INSTALL_TIME_DESCENDING -> compareByDescending { it.firstInstallTime }
            LAST_UPDATE_TIME_ASCENDING -> compareBy { it.lastUpdateTime }
            LAST_UPDATE_TIME_DESCENDING -> compareByDescending { it.lastUpdateTime }
        }

    private fun updateInstalledAppList() = viewModelScope.launch {
        appRepository.updateApplicationList().collect {
            if (it is Result.Error) {
                _errorState.emit(it.exception?.toErrorMessage())
            }
        }
    }

    private fun listenSortingChanges() = viewModelScope.launch(cpuDispatcher) {
        userDataRepository.userData
            .map { it.appSorting }
            .distinctUntilChanged()
            .drop(1)
            .collect { sorting ->
                val newList = _appList.toMutableList()
                newList.sortWith(appComparator(sorting))
                _appList = newList.toMutableStateList()
                _appListFlow.value = _appList
            }
    }

    private fun listenShowSystemAppsChanges() = viewModelScope.launch {
        userDataRepository.userData
            .map { it.showSystemApps }
            .distinctUntilChanged()
            .drop(1)
            .collect { loadData() }
    }

    fun updateSorting(sorting: AppSorting) = viewModelScope.launch {
        userDataRepository.setAppSorting(sorting)
    }

    fun updateServiceStatus(packageName: String, index: Int) = viewModelScope.launch(
        context = ioDispatcher + exceptionHandler,
    ) {
        val userData = userDataRepository.userData.first()
        if (!userData.showServiceInfo) {
            return@launch
        }
        val oldItem = _appList.getOrNull(index) ?: return@launch
        if (oldItem.appServiceStatus != null) {
            // Don't get service info again
            return@launch
        }
        Timber.d("Get service status for $packageName")
        val status = AppStateCache.get(getApplication(), packageName)
        val serviceStatus = AppServiceStatus(
            packageName = status.packageName,
            running = status.running,
            blocked = status.blocked,
            total = status.total,
        )
        val newItem = oldItem.copy(appServiceStatus = serviceStatus)
        _appList[index] = newItem
    }

    fun dismissDialog() = viewModelScope.launch {
        _errorState.emit(null)
    }

    fun clearData(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "pm clear $packageName".exec(ioDispatcher)
    }

    fun clearCache(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        val context: Context = getApplication()
        val cacheFolder = context.filesDir
            ?.parentFile
            ?.parentFile
            ?.resolve(packageName)
            ?.resolve("cache")
            ?: run {
                Timber.e("Can't resolve cache path for $packageName")
                return@launch
            }
        Timber.d("Delete cache folder: $cacheFolder")
        FileUtils.delete(cacheFolder.absolutePath, recursively = true, ioDispatcher)
    }

    fun uninstall(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "pm uninstall $packageName".exec(ioDispatcher)
        notifyAppUpdated(packageName)
    }

    fun forceStop(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "am force-stop $packageName".exec(ioDispatcher)
        notifyAppUpdated(packageName)
    }

    fun enable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "pm enable $packageName".exec(ioDispatcher)
        notifyAppUpdated(packageName)
    }

    fun disable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "pm disable $packageName".exec(ioDispatcher)
        notifyAppUpdated(packageName)
    }

    private suspend fun notifyAppUpdated(packageName: String) {
        appRepository.updateApplication(packageName).collect {
            if (it is Result.Error) {
                _errorState.emit(it.exception?.toErrorMessage())
            }
        }
    }
}

data class AppServiceStatus(
    val packageName: String,
    val running: Int = 0,
    val blocked: Int = 0,
    val total: Int = 0,
)

/**
 * Data representation for the installed application.
 * App icon will be loaded by PackageName.
 */
data class AppItem(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val isRunning: Boolean,
    val enabled: Boolean,
    val firstInstallTime: Instant?,
    val lastUpdateTime: Instant?,
    val appServiceStatus: AppServiceStatus?,
    val packageInfo: PackageInfo?,
)

sealed interface AppListUiState {
    object Loading : AppListUiState
    class Error(val error: ErrorMessage) : AppListUiState
    object Success : AppListUiState
}
