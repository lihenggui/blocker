package com.merxury.blocker.ui.detail.component

import com.merxury.libkit.entity.EComponentType

data class ComponentData(
    var name: String = "",
    var simpleName: String = "",
    var packageName: String = "",
    var type: EComponentType = EComponentType.RECEIVER,
    var ifwBlocked: Boolean = false,
    var pmBlocked: Boolean = false,
    var isRunning: Boolean = false
)
