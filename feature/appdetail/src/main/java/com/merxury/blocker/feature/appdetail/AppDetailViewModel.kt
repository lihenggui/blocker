package com.merxury.blocker.feature.appdetail

import android.content.pm.PackageInfo
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.merxury.blocker.core.decoder.StringDecoder
import com.merxury.blocker.feature.appdetail.navigation.AppDetailArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

private const val APP_INFO = "App info"
private const val SERVICE = "Service"
private const val RECEIVER = "Receiver"
private const val ACTIVITY = "Activity"
private const val CONTENT_PROVIDER = "Content Provider"
private const val TAG = "AppDetailViewModel"

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    stringDecoder: StringDecoder
) : ViewModel() {
    private val appPackageNameArgs: AppDetailArgs = AppDetailArgs(savedStateHandle, stringDecoder)
    private val _uiState: MutableStateFlow<AppDetailUiState> =
        MutableStateFlow(AppDetailUiState.Loading)
    val uiState: StateFlow<AppDetailUiState> = _uiState

    private val _tabState = MutableStateFlow(
        TabState(
            titles = listOf(APP_INFO, SERVICE, RECEIVER, ACTIVITY, CONTENT_PROVIDER),
            currentIndex = 0
        )
    )
    val tabState: StateFlow<TabState> = _tabState.asStateFlow()

    fun switchTab(newIndex: Int) {
        if (newIndex != tabState.value.currentIndex) {
            _tabState.update {
                it.copy(currentIndex = newIndex)
            }
        }
    }

    fun onSwitch(componentInfo: ComponentInfo) {
        val uiState = _uiState.value
        if (uiState !is AppDetailUiState.Success) {
            Timber.tag(TAG).e("Wrong type of UIState, return")
            return
        }
        val matchedDevIndex = uiState.activity
            .indexOfFirst { it.componentName == componentInfo.componentName }
        if (matchedDevIndex != -1) {
            uiState.activity[matchedDevIndex] =
                uiState.activity[matchedDevIndex].copy(value = componentInfo.value)
        }
        // TODO
    }
}

data class TabState(
    val titles: List<String>,
    val currentIndex: Int
)

data class AppDetailInfo(
    var appName: String = "",
    var packageName: String = "",
    var versionName: String? = "",
    var isEnabled: Boolean = false,
    var label: String = "",
    var firstInstallTime: Date? = null,
    var lastUpdateTime: Date? = null,
    var packageInfo: PackageInfo? = null,
)

data class ComponentInfo(
    val componentName: String,
    val componentDetail: String,
    val value: Boolean
)

sealed interface AppDetailUiState {
    object Loading : AppDetailUiState
    class Error(val errorMessage: String) : AppDetailUiState
    data class Success(
        val appInfo: AppDetailInfo,
        val service: SnapshotStateList<ComponentInfo>,
        val receiver: SnapshotStateList<ComponentInfo>,
        val activity: SnapshotStateList<ComponentInfo>,
        val contentProvider: SnapshotStateList<ComponentInfo>
    ) : AppDetailUiState
}
