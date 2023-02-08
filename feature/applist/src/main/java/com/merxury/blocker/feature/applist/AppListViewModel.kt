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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.app.AppRepository
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.data.InstalledApp
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
import com.merxury.blocker.feature.applist.state.AppStateCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val appStateList = mutableStateListOf<AppItem>()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        _errorState.tryEmit(throwable.toErrorMessage())
    }
    private val channel = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        viewModelScope.launch {
            consumeEach { it.join() }
        }
    }

    init {
        loadData()
        updateInstalledAppList()
        listenSortingChanges()
        listenShowSystemAppsChanges()
    }

    fun loadData() = viewModelScope.launch {
        appRepository.getApplicationList()
            .onStart {
                _uiState.emit(AppListUiState.Loading)
            }
            .distinctUntilChanged()
            .collect { list ->
                val preference = userDataRepository.userData.first()
                val sortType = preference.appSorting
                val filteredList = if (!preference.showSystemApps) {
                    list.filterNot { it.isSystem }
                } else {
                    list
                }.toMutableList()
                mapToSnapshotStateList(filteredList)
                sortList(appStateList, sortType)
                _uiState.emit(AppListUiState.Success(appStateList))
            }
    }

    private fun updateInstalledAppList() = viewModelScope.launch {
        appRepository.updateApplicationList().collect {
            if (it is Result.Error) {
                _errorState.emit(it.exception?.toErrorMessage())
            }
        }
    }

    private fun listenSortingChanges() = viewModelScope.launch {
        userDataRepository.userData
            .map { it.appSorting }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                val uiState = _uiState.value
                if (uiState is AppListUiState.Success) {
                    sortList(uiState.appList, it)
                }
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

    fun updateServiceStatus(packageName: String) {
        channel.trySend(
            viewModelScope.launch(
                start = CoroutineStart.LAZY,
                context = ioDispatcher + exceptionHandler,
            ) {
                val userData = userDataRepository.userData.first()
                if (!userData.showServiceInfo) {
                    return@launch
                }
                Timber.d("Get service status for $packageName")
                val currentUiState = _uiState.value
                if (currentUiState !is AppListUiState.Success) {
                    Timber.e("Ui state is incorrect, don't update service status.")
                    return@launch
                }
                val currentList = currentUiState.appList
                val itemIndex = currentList.indexOfFirst { it.packageName == packageName }
                val oldItem = currentList.getOrNull(itemIndex) ?: return@launch
                if (oldItem.appServiceStatus != null) {
                    // Don't get service info again
                    return@launch
                }
                val status = AppStateCache.get(getApplication(), packageName)
                val serviceStatus = AppServiceStatus(
                    packageName = status.packageName,
                    running = status.running,
                    blocked = status.blocked,
                    total = status.total,
                )
                val newItem = oldItem.copy(appServiceStatus = serviceStatus)
                currentList[itemIndex] = newItem
            },
        )
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

    private suspend fun sortList(
        list: SnapshotStateList<AppItem>,
        sorting: AppSorting,
    ) = withContext(cpuDispatcher) {
        when (sorting) {
            NAME_ASCENDING -> list.sortBy { it.label.lowercase() }
            NAME_DESCENDING -> list.sortByDescending { it.label.lowercase() }
            FIRST_INSTALL_TIME_ASCENDING -> list.sortBy { it.firstInstallTime }
            FIRST_INSTALL_TIME_DESCENDING -> list.sortByDescending { it.firstInstallTime }
            LAST_UPDATE_TIME_ASCENDING -> list.sortBy { it.lastUpdateTime }
            LAST_UPDATE_TIME_DESCENDING -> list.sortByDescending { it.lastUpdateTime }
        }
    }

    private suspend fun mapToSnapshotStateList(
        list: MutableList<InstalledApp>,
    ) = withContext(cpuDispatcher) {
        appStateList.clear()
        list.forEach {
            val appItem = AppItem(
                label = it.label,
                packageName = it.packageName,
                versionName = it.versionName,
                versionCode = it.versionCode,
                isSystem = ApplicationUtil.isSystemApp(pm, it.packageName),
                // TODO detect if an app is running or not
                isRunning = false,
                enabled = it.isEnabled,
                firstInstallTime = it.firstInstallTime,
                lastUpdateTime = it.lastUpdateTime,
                appServiceStatus = null,
                packageInfo = pm.getPackageInfoCompat(it.packageName, 0),
            )
            appStateList.add(appItem)
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
    data class Success(
        val appList: SnapshotStateList<AppItem>,
    ) : AppListUiState
}
