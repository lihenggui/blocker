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

package com.merxury.blocker.feature.appdetail.componentdetail

import android.os.Parcelable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.component.LocalComponentRepository
import com.merxury.blocker.core.data.respository.componentdetail.ComponentDetailRepository
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.data.ComponentDetail
import com.merxury.blocker.core.ui.data.UiMessage
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Error
import com.merxury.blocker.feature.appdetail.componentdetail.ComponentDetailUiState.Success
import com.merxury.blocker.feature.appdetail.navigation.ComponentDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ComponentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder,
    private val componentRepository: LocalComponentRepository,
    private val componentDetailRepository: ComponentDetailRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val componentDetailArg = ComponentDetailArgs(savedStateHandle, stringDecoder)

    private val _uiState = MutableStateFlow<ComponentDetailUiState>(ComponentDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() = viewModelScope.launch(ioDispatcher) {
        val userGeneratedDetail = componentDetailRepository
            .getUserGeneratedDetail(componentDetailArg.name)
            .first()
        if (userGeneratedDetail != null) {
            _uiState.value = Success(
                isFetchingData = true,
                detail = userGeneratedDetail.toUserEditableComponentDetail(),
            )
            return@launch
        }
        val dbDetail = componentDetailRepository
            .getDbComponentDetail(componentDetailArg.name)
            .first()
        if (dbDetail != null) {
            _uiState.value = Success(
                isFetchingData = true,
                detail = dbDetail.toUserEditableComponentDetail(),
            )
            return@launch
        }
        // No match found in the cache, emit the default value
        val component = componentRepository.getComponent(componentDetailArg.name).first()
        if (component == null) {
            Timber.e("Component ${componentDetailArg.name} not found")
            _uiState.value = Error(UiMessage("Component not found"))
            return@launch
        }
        _uiState.value = Success(
            isFetchingData = true,
            detail = UserEditableComponentDetail(
                name = component.name,
            ),
        )
        // Fetch the data from network
        val networkDetail = componentDetailRepository
            .getNetworkComponentDetail(componentDetailArg.name)
            .first()
        if (networkDetail != null) {
            _uiState.value = Success(
                isFetchingData = false,
                detail = networkDetail.toUserEditableComponentDetail(),
            )
            return@launch
        }
        // No matching found in the network, emit the default value
        // Dismiss the loading progress bar
        _uiState.value = Success(
            isFetchingData = false,
            detail = UserEditableComponentDetail(
                name = component.name,
            ),
        )
    }

    fun save(editableDetail: UserEditableComponentDetail) = viewModelScope.launch(ioDispatcher) {
        val detail = ComponentDetail(
            name = editableDetail.name,
            sdkName = if (editableDetail.belongToSdk) editableDetail.sdkName else null,
            description = editableDetail.description,
            disableEffect = editableDetail.disableEffect,
            contributor = editableDetail.contributor,
            addedVersion = editableDetail.addedVersion,
            recommendToBlock = editableDetail.recommendToBlock,
        )
        componentDetailRepository.saveComponentDetail(detail, userGenerated = true)
    }
}

@Parcelize
data class UserEditableComponentDetail(
    val name: String,
    val belongToSdk: Boolean = false,
    val sdkName: String? = null,
    val description: String? = null,
    val disableEffect: String? = null,
    val contributor: String? = null,
    val addedVersion: String? = null,
    val recommendToBlock: Boolean = false,
) : Parcelable

private fun ComponentDetail.toUserEditableComponentDetail() = UserEditableComponentDetail(
    name = name,
    belongToSdk = sdkName != null,
    sdkName = sdkName,
    description = description,
    disableEffect = disableEffect,
    contributor = contributor,
    addedVersion = addedVersion,
    recommendToBlock = recommendToBlock,
)

sealed class ComponentDetailUiState {
    object Loading : ComponentDetailUiState()
    data class Success(
        val isFetchingData: Boolean,
        val detail: UserEditableComponentDetail,
    ) : ComponentDetailUiState()

    data class Error(val message: UiMessage) : ComponentDetailUiState()
}
