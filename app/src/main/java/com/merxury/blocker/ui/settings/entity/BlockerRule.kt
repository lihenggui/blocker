package com.merxury.blocker.ui.settings.entity

data class BlockerRule(
        var packageName: String = "",
        var versionCode: Int = -1,
        var components: List<ComponentRule> = ArrayList()
)