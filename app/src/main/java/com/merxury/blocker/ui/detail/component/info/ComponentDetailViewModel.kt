package com.merxury.blocker.ui.detail.component.info

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.data.component.OnlineComponentData
import com.merxury.blocker.data.component.OnlineComponentDataRepository
import com.merxury.blocker.ui.detail.component.ComponentData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ComponentDetailViewModel @Inject constructor(private val repository: OnlineComponentDataRepository) :
    ViewModel() {
    private val _onlineData: MutableStateFlow<OnlineComponentData?> = MutableStateFlow(null)
    val onlineData = _onlineData.asStateFlow()
    private val _ifwState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val ifwState = _ifwState.asStateFlow()
    private val _pmState: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val pmState = _pmState.asStateFlow()

    fun getOnlineData(context: Context, component: ComponentData) {
        viewModelScope.launch {
            _onlineData.value =
                repository.getComponentData(context, component.name, loadFromCacheOnly = false)
        }
    }
}