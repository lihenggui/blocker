package com.merxury.blocker.ui.strategy.entity

import com.merxury.blocker.ui.component.EComponentType


data class ComponentDescription(
        var id: Long = -1,
        var name: String = "",
        var packageName: String = "",
        var type: EComponentType = EComponentType.UNKNOWN,
        var description: String = "",
        var author: String? = null,
        var authorDeviceId: String? = null,
        var upVoteCount: Int = 0,
        var downVoteCount: Int = 0
)