package com.merxury.blocker.rule.entity

data class BlockerRule(
        var packageName: String = "",
        var versionName: String = "",
        var versionCode: Int = -1,
        var author: String = "Blocker",
        var components: MutableList<ComponentRule> = ArrayList()
)