package com.merxury.blocker.ui.detail.component

data class ComponentData(
    var name: String = "",
    var simpleName: String = "",
    var packageName: String = "",
    var ifwBlocked: Boolean = false,
    var pmBlocked: Boolean = false,
    var isRunning: Boolean = false
)