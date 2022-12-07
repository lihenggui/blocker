package com.merxury.blocker.feature.applist

import androidx.lifecycle.ViewModel

class AppListViewModel : ViewModel() {
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