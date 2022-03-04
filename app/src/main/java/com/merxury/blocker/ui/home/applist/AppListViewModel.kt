package com.merxury.blocker.ui.home.applist

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elvishew.xlog.XLog
import com.merxury.blocker.util.PreferenceUtil
import com.merxury.libkit.entity.Application
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.coroutines.launch

class AppListViewModel : ViewModel() {
    private val _appList = MutableLiveData<List<Application>>()
    val appList: LiveData<List<Application>> = _appList
    private var originalList = listOf<Application>()
    private val _sortType = MutableLiveData<SortType?>()
    val sortType: LiveData<SortType?> = _sortType
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
            val sortType = PreferenceUtil.getSortType(context)
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
            it.label.replace(" ", "").contains(clearedKeyword, true) ||
                    it.packageName.contains(keyword, true)
        }
    }

    private fun sortList(list: List<Application>, sortType: SortType?): List<Application> {
        return when (sortType) {
            SortType.NAME_ASC -> list.sortedBy { it.getLabel(pm!!) }
            SortType.NAME_DESC -> list.sortedByDescending { it.getLabel(pm!!) }
            SortType.INSTALL_TIME -> list.sortedByDescending { it.firstInstallTime }
            SortType.LAST_UPDATE_TIME -> list.sortedByDescending { it.lastUpdateTime }
            else -> list.sortedBy { it.getLabel(pm!!) }
        }
    }
}