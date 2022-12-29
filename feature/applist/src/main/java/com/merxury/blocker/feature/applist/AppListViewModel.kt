/*
 * Copyright 2022 Blocker
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.UserDataRepository
import com.merxury.blocker.core.extension.exec
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_DESCENDING
import com.merxury.blocker.core.network.BlockerDispatchers.DEFAULT
import com.merxury.blocker.core.network.BlockerDispatchers.IO
import com.merxury.blocker.core.network.Dispatcher
import com.merxury.blocker.core.ui.data.ErrorMessage
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import timber.log.Timber

@HiltViewModel
class AppListViewModel @Inject constructor(
    app: android.app.Application,
    private val userDataRepository: UserDataRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    @Dispatcher(DEFAULT) private val cpuDispatcher: CoroutineDispatcher
) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow<AppListUiState>(AppListUiState.Loading)
    val uiState = _uiState.asStateFlow()
    var errorState = mutableStateOf<ErrorMessage?>(null)
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable)
        errorState.value = ErrorMessage(throwable.message.orEmpty(), throwable.stackTraceToString())
    }

    init {
        loadData()
    }

    fun loadData() = viewModelScope.launch {
        _uiState.emit(AppListUiState.Loading)
        val preference = userDataRepository.userData.first()
        val sortType = preference.appSorting
        val list = if (preference.showSystemApps) {
            ApplicationUtil.getApplicationList(getApplication())
        } else {
            ApplicationUtil.getThirdPartyApplicationList(getApplication())
        }
            .toMutableList()
        val stateAppList = mapToSnapshotStateList(list, getApplication())
        sortList(stateAppList, sortType)
        _uiState.emit(AppListUiState.Success(stateAppList))
    }

    fun dismissDialog() {
        errorState.value = null
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
    }

    fun forceStop(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "am force-stop $packageName".exec(ioDispatcher)
    }

    fun enable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "pm enable $packageName".exec(ioDispatcher)
    }

    fun disable(packageName: String) = viewModelScope.launch(ioDispatcher + exceptionHandler) {
        "pm disable $packageName".exec(ioDispatcher)
    }

    private suspend fun sortList(
        list: SnapshotStateList<AppItem>,
        sorting: AppSorting
    ) = withContext(cpuDispatcher) {
        when (sorting) {
            NAME_ASCENDING -> list.sortBy { it.label.lowercase() }
            NAME_DESCENDING -> list.sortByDescending { it.label.lowercase() }
            FIRST_INSTALL_TIME_ASCENDING -> list.sortBy { it.firstInstallTime }
            FIRST_INSTALL_TIME_DESCENDING -> list.sortByDescending { it.firstInstallTime }
            LAST_UPDATE_TIME_ASCENDING -> list.sortBy { it.lastUpdateTime }
            LAST_UPDATE_TIME_DESCENDING -> list.sortByDescending { it.lastUpdateTime }
        }
        list.sortBy { it.enabled }
    }

    private suspend fun mapToSnapshotStateList(
        list: MutableList<Application>,
        context: Context
    ): SnapshotStateList<AppItem> = withContext(cpuDispatcher) {
        val stateAppList = mutableStateListOf<AppItem>()
        list.forEach {
            val appItem = AppItem(
                label = it.label,
                packageName = it.packageName,
                versionName = it.versionName.orEmpty(),
                isSystem = ApplicationUtil.isSystemApp(context.packageManager, it.packageName),
                // TODO detect if an app is running or not
                isRunning = false,
                enabled = it.isEnabled,
                firstInstallTime = it.firstInstallTime,
                lastUpdateTime = it.lastUpdateTime,
                // TODO get service status
                appServiceStatus = null,
                packageInfo = it.packageInfo
            )
            stateAppList.add(appItem)
        }
        return@withContext stateAppList
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
