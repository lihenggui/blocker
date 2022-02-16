package com.merxury.blocker.ui.applist

data class AppState(
    var running: Int = 0,
    var blocked: Int = 0,
    var total: Int = 0,
    var packageName: String
)