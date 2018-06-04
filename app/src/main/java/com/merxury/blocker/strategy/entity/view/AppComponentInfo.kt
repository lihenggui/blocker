package com.merxury.blocker.strategy.entity.view

import com.merxury.blocker.strategy.entity.Component

data class AppComponentInfo(
        var activity: List<Component>? = null,
        var receiver: List<Component>? = null,
        var service: List<Component>? = null,
        var provider: List<Component>? = null
)