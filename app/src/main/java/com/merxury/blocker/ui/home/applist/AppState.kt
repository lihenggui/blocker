package com.merxury.blocker.ui.home.applist

data class AppState(
    var running: Int = 0,
    var blocked: Int = 0,
    var total: Int = 0,
    var packageName: String
)
