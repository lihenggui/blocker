/*
 * Copyright 2022 Blocker
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

package com.merxury.blocker.ui.detail.component.info

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.OnlineComponentRepository
import com.merxury.blocker.core.network.model.NetworkComponentDetail
import com.merxury.blocker.core.result.Result
import com.merxury.blocker.ui.detail.component.ComponentData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ComponentDetailViewModel @Inject constructor(
    private val repository: OnlineComponentRepository
) : ViewModel() {
    private val _onlineData: MutableStateFlow<NetworkComponentDetail?> = MutableStateFlow(null)
    val onlineData = _onlineData.asStateFlow()
    private val isLoading = MutableStateFlow(false)
    val loading = isLoading.asStateFlow()

    fun getOnlineData(component: ComponentData) {
        viewModelScope.launch {
            isLoading.value = true
            _onlineData.value =
                repository.getUserGeneratedComponentDetail(component.name)
            val onlineData = repository.getNetworkComponentData(component.name,)
            onlineData.collect {
                Timber.d("Get online data $it")
                if (it is Result.Success) {
                    _onlineData.value = it.data
                    repository.saveComponentAsCache(it.data)
                }
                isLoading.value = false
            }
        }
    }

    fun saveUserRule(context: Context, data: NetworkComponentDetail) {
        viewModelScope.launch {
            repository.saveUserGeneratedComponentDetail(data)
        }
    }
}
