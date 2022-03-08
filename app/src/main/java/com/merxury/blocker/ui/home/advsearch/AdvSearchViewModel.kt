package com.merxury.blocker.ui.home.advsearch

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.ui.detail.component.ComponentData
import com.merxury.libkit.entity.Application
import com.merxury.libkit.utils.ApplicationUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdvSearchViewModel : ViewModel() {
    private val _appList = MutableLiveData<List<Application>>()
    val appList: LiveData<List<Application>> = _appList
    private val _total = MutableLiveData<Int>()
    val total: LiveData<Int> = _total
    private val _current = MutableLiveData<Int>()
    val current: LiveData<Int> = _current
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    private val _currentProcessApplication = MutableLiveData<Application>()
    val currentProcessApplication: LiveData<Application> = _currentProcessApplication
    private val _finalData = MutableLiveData<Pair<Application, List<ComponentData>>>()
    val finalData: LiveData<Pair<Application, List<ComponentData>>> = _finalData

    fun load(context: Context) {
        viewModelScope.launch {
            val appList = ApplicationUtil.getThirdPartyApplicationList(context)
            processData(context, appList)
        }
    }

    private suspend fun processData(context: Context, appList: List<Application>) {
        notifyDataProcessing(appList)
        val result = mutableListOf<Pair<Application, List<ComponentData>>>()
        withContext(Dispatchers.Default) {
            appList.forEachIndexed { index, application ->
                _currentProcessApplication.postValue(application)
                _current.postValue(index)
                val components = mutableListOf<ComponentData>()
                val activities =
                    ApplicationUtil.getActivityList(context.packageManager, application.packageName)
                val services =
                    ApplicationUtil.getServiceList(context.packageManager, application.packageName)
                val providers =
                    ApplicationUtil.getProviderList(context.packageManager, application.packageName)
                val receivers =
                    ApplicationUtil.getReceiverList(context.packageManager, application.packageName)
                activities.map {

                }
            }
        }
    }

    private fun notifyDataProcessing(appList: List<Application>) {
        _appList.value = appList
        _total.value = appList.size
        _current.value = 0
        _isLoading.value = true
    }
}