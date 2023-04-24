package com.merxury.blocker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockerAppViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {
    private val _useBottomSheetStyleInDetailUiState = MutableStateFlow(UseBottomSheetStyleUiState())
    val useBottomSheetStyleInDetailUiState: StateFlow<UseBottomSheetStyleUiState> =
        _useBottomSheetStyleInDetailUiState.asStateFlow()

    init {
        listenUseBottomSheetStyleInDetailChanges()
    }

    private fun listenUseBottomSheetStyleInDetailChanges() = viewModelScope.launch {
        userDataRepository.userData
            .collect { useBottomSheetStyleInDetail ->
                if (useBottomSheetStyleInDetail.useBottomSheetStyleInDetail) {
                    _useBottomSheetStyleInDetailUiState.emit(UseBottomSheetStyleUiState(true))
                } else {
                    _useBottomSheetStyleInDetailUiState.emit(UseBottomSheetStyleUiState(false))
                }
            }
    }
}

data class UseBottomSheetStyleUiState(
    val useBottomSheetStyleInDetail: Boolean = false,
)
