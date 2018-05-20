package com.merxury.blocker.ui.strategy.entity

import com.merxury.blocker.ui.component.EComponentType


data class Component(
        var id: Long = -1,
        var name: String = "",
        var packageName: String = "",
        var type: EComponentType = EComponentType.UNKNOWN,
        var upVoteCount: Int = 0,
        var downVoteCount: Int = 0,
        var bestComment: ComponentDescription? = null
)