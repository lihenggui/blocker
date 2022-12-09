package com.merxury.blocker.feature.applist

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class AppListViewModel @Inject constructor() : ViewModel() {
    private val _uiState: MutableStateFlow<AppListUiState> =
        MutableStateFlow(AppListUiState.Loading)
    val uiState: StateFlow<AppListUiState> = _uiState
}

data class AppStatus(
    var running: Int = 0,
    var blocked: Int = 0,
    var total: Int = 0,
    var packageName: String
)

data class AppInfo(
    var packageName: String = "",
    var versionName: String? = "",
    var appIconUrl: String = "",
    var appStatus: AppStatus
)

sealed interface AppListUiState {
    object Loading : AppListUiState
    class Error(val errorMessage: String) : AppListUiState
    data class Success(
        val appList: SnapshotStateList<AppInfo>
    ) : AppListUiState
}
