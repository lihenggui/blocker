package com.merxury.blocker.ui.home

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.libkit.entity.Application
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.coroutines.launch

class AppListViewModel : ViewModel() {
    private val _appList = MutableLiveData<List<Application>>()
    val appList: LiveData<List<Application>> = _appList

    private val _sortType = MutableLiveData<SortType?>()
    val sortType: LiveData<SortType?> = _sortType

    fun loadData(context: Context, loadSystemApp: Boolean) {
        viewModelScope.launch {
            val list = if (loadSystemApp) {
                ApplicationUtil.getSystemApplicationList(context)
            } else {
                ApplicationUtil.getThirdPartyApplicationList(context)
            }
            _appList.value = sortList(list, _sortType.value)
        }
    }

    fun updateSorting(sortType: SortType?) {
        _sortType.value = sortType
        val list = _appList.value ?: mutableListOf()
        _appList.value = sortList(list, sortType)
    }

    private fun sortList(list: List<Application>, sortType: SortType?): List<Application> {
        return when (sortType) {
            SortType.NAME -> list.sortedBy { it.label }
            SortType.INSTALL_TIME -> list.sortedBy { it.lastUpdateTime }
            else -> list.sortedBy { it.label }
        }
    }
}