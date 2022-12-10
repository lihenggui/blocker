package com.merxury.blocker.feature.appdetail

import android.content.pm.PackageInfo
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val APP_INFO = "App info"
private const val SERVICE = "Service"
private const val RECEIVER = "Receiver"
private const val ACTIVITY = "Activity"
private const val CONTENT_PROVIDER = "Content Provider"

@HiltViewModel
class AppDetailViewModel @Inject constructor() : ViewModel() {
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
