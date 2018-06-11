package com.merxury.blocker.ui.settings.entity

import com.merxury.blocker.core.root.EControllerMethod
import com.merxury.blocker.ui.component.EComponentType

data class ComponentRule(
        var packageName: String = "",
        var name: String = "",
        var type: EComponentType = EComponentType.UNKNOWN,
        var method: EControllerMethod = EControllerMethod.PM
)