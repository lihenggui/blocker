package com.merxury.blocker.ui.component

data class ComponentItemViewModel(
        var state: Boolean = true,
        var ifwState: Boolean = true,
        var upVoted: Boolean = false,
        var downVoted: Boolean = false,
        var upVoteCount: Int = 0,
        var downVoteCount: Int = 0,
        var name: String = "",
        var simpleName: String = "",
        var packageName: String = "",
        var bestComment: String = ""
)