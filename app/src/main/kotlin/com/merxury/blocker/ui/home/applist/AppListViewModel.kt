/*
 * Copyright 2025 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.merxury.blocker.ui.home.applist

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.elvishew.xlog.XLog
import com.merxury.blocker.R
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.util.ManagerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AppListViewModel : ViewModel() {
    private val _appList = MutableLiveData<List<Application>>()
    val appList: LiveData<List<Application>> = _appList
    private var originalList = listOf<Application>()
    private val _sortType = MutableLiveData<SortType?>()
    val sortType: LiveData<SortType?> = _sortType
    private val _error = MutableSharedFlow<Exception>()
    val error = _error.asSharedFlow()
    private var pm: PackageManager? = null
    private val logger = XLog.tag("AppListViewModel")

    override fun onCleared() {
        pm = null
    }

    fun loadData(context: Context, loadSystemApp: Boolean) {
        logger.i("loadData, isLoadSystemApp: $loadSystemApp")
        if (pm == null) {
            pm = context.packageManager
        }
        viewModelScope.launch {
            val list = if (loadSystemApp) {
                ApplicationUtil.getApplicationList(context)
            } else {
                ApplicationUtil.getThirdPartyApplicationList(context)
            }
            logger.i("loadData done, list size: ${list.size}")
            val sortType = getSortType(context)
            val sortedList = sortList(list, sortType)
            originalList = sortedList
            _appList.value = sortedList
        }
    }

    fun updateSorting(sortType: SortType?) {
        _sortType.value = sortType
        val list = _appList.value ?: mutableListOf()
        _appList.value = sortList(list, sortType)
    }

    fun filter(keyword: String?) {
        if (keyword.isNullOrEmpty()) {
            _appList.value = originalList
            return
        }
        // Ignore spaces
        val clearedKeyword = keyword.trim().replace(" ", "")
        _appList.value = originalList.filter {
            it.label.replace(" ", "").contains(clearedKeyword, true) || it.packageName.contains(
                keyword,
                true,
            )
        }
    }

    fun clearData(app: Application) {
        logger.d("clearData, app: ${app.packageName}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ManagerUtils.clearData(app.packageName)
            } catch (e: Exception) {
                logger.e("Failed to clear data", e)
                _error.emit(e)
            }
        }
    }

    fun clearCache(app: Application) {
        logger.d("clearCache, app: ${app.packageName}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ManagerUtils.clearCache(app.packageName)
            } catch (e: Exception) {
                logger.e("Failed to clear cache", e)
                _error.emit(e)
            }
        }
    }

    fun uninstallApp(app: Application) {
        logger.d("uninstallApp, app: ${app.packageName}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ManagerUtils.uninstallApplication(app.packageName)
            } catch (e: Exception) {
                logger.e("Failed to uninstall app", e)
                _error.emit(e)
            }
        }
    }

    fun enableApp(app: Application) {
        logger.d("enableApp, app: ${app.packageName}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ManagerUtils.enableApplication(app.packageName)
            } catch (e: Exception) {
                logger.e("Failed to enable app", e)
                _error.emit(e)
            }
        }
    }

    fun disableApp(app: Application) {
        logger.d("disableApp, app: ${app.packageName}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ManagerUtils.disableApplication(app.packageName)
            } catch (e: Exception) {
                logger.e("Failed to disable app", e)
                _error.emit(e)
            }
        }
    }

    fun forceStop(app: Application) {
        logger.d("forceStop, app: ${app.packageName}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ManagerUtils.forceStop(app.packageName)
            } catch (e: Exception) {
                logger.e("Failed to force stop app", e)
                _error.emit(e)
            }
        }
    }

    private fun sortList(list: List<Application>, sortType: SortType?): List<Application> {
        return when (sortType) {
            SortType.NAME_ASC -> list.sortedBy { it.label }
            SortType.NAME_DESC -> list.sortedByDescending { it.label }
            SortType.INSTALL_TIME -> list.sortedByDescending { it.firstInstallTime }
            SortType.LAST_UPDATE_TIME -> list.sortedByDescending { it.lastUpdateTime }
            else -> list.sortedBy { it.label }
        }
    }

    private fun getSortType(context: Context): SortType {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val value = pref.getString(context.getString(R.string.key_pref_sort_type), null).orEmpty()
        return try {
            SortType.valueOf(value)
        } catch (e: Exception) {
            SortType.NAME_ASC
        }
    }
}
