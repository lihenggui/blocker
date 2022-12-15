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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.UserDataRepository
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.preference.AppSorting
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.FIRST_INSTALL_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.LAST_UPDATE_TIME_DESCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_ASCENDING
import com.merxury.blocker.core.model.preference.AppSorting.NAME_DESCENDING
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class AppListViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppListUiState>(AppListUiState.Loading)
    val uiState = _uiState.asStateFlow()

    fun loadData(context: Context) = viewModelScope.launch {
        _uiState.emit(AppListUiState.Loading)
        val preference = userDataRepository.userData.first()
        val sortType = preference.appSorting
        val list = if (preference.showSystemApps) {
            ApplicationUtil.getApplicationList(context)
        } else {
            ApplicationUtil.getThirdPartyApplicationList(context)
        }
        sortList(list, sortType)
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
                appServiceStatus = null
            )
            stateAppList.add(appItem)
        }
        _uiState.emit(AppListUiState.Success(stateAppList))
    }

    private fun sortList(
        list: MutableList<Application>,
        sorting: AppSorting
    ) {
        when (sorting) {
            NAME_ASCENDING -> list.sortBy { it.label }
            NAME_DESCENDING -> list.sortByDescending { it.label }
            FIRST_INSTALL_TIME_ASCENDING -> list.sortBy { it.firstInstallTime }
            FIRST_INSTALL_TIME_DESCENDING -> list.sortByDescending { it.firstInstallTime }
            LAST_UPDATE_TIME_ASCENDING -> list.sortBy { it.lastUpdateTime }
            LAST_UPDATE_TIME_DESCENDING -> list.sortByDescending { it.lastUpdateTime }
        }
        list.sortBy { it.isEnabled }
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
    val appServiceStatus: AppServiceStatus?,
)

sealed interface AppListUiState {
    object Loading : AppListUiState
    class Error(val errorMessage: String) : AppListUiState
    data class Success(
        val appList: SnapshotStateList<AppItem>
    ) : AppListUiState
}
