package com.merxury.blocker.ui.detail.component.info

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.data.component.OnlineComponentData
import com.merxury.blocker.data.component.OnlineComponentDataRepository
import com.merxury.blocker.ui.detail.component.ComponentData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ComponentDetailViewModel @Inject constructor(
    private val repository: OnlineComponentDataRepository
) : ViewModel() {
    private val _onlineData: MutableStateFlow<OnlineComponentData?> = MutableStateFlow(null)
    val onlineData = _onlineData.asStateFlow()
    private val isLoading = MutableStateFlow(false)
    val loading = isLoading.asStateFlow()

    fun getOnlineData(context: Context, component: ComponentData) {
        viewModelScope.launch {
            isLoading.value = true
            _onlineData.value =
                repository.getComponentData(context, component.name, loadFromCacheOnly = true)
            val onlineData = repository.getComponentData(
                context,
                component.name,
                loadFromCacheOnly = false
            )
            if (onlineData != null) {
                _onlineData.value = onlineData
            }
            isLoading.value = false
        }
    }

    fun saveUserRule(context: Context, data: OnlineComponentData) {
        viewModelScope.launch {
            repository.saveUserGeneratedComponentDetail(context, data)
        }
    }
}
