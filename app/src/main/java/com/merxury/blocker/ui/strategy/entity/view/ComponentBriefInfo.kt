package com.merxury.blocker.ui.strategy.entity.view

import com.merxury.blocker.ui.component.EComponentType

data class ComponentBriefInfo(
        var packageName: String = "",
        var name: String = "",
        var type: EComponentType = EComponentType.UNKNOWN
)