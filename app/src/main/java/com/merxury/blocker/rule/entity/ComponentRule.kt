package com.merxury.blocker.rule.entity

import com.merxury.blocker.core.entity.EComponentType
import com.merxury.blocker.core.root.EControllerMethod

data class ComponentRule(
    var packageName: String = "",
    var name: String = "",
    var state: Boolean = true,
    var type: EComponentType = EComponentType.RECEIVER,
    var method: EControllerMethod = EControllerMethod.PM
)