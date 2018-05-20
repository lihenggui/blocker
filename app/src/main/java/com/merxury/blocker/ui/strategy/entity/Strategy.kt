package com.merxury.blocker.ui.strategy.entity

data class Strategy(
        var id: Long = -1,
        var packageName: String = "",
        var versionName: String = "",
        var versionCode: Int = 0,
        var author: String? = "",
        var deviceId: String? = "",
        var disabledComponents: List<String> = ArrayList(),
        var upVoteCount: Long = 0,
        var downVoteCount: Long = 0
)