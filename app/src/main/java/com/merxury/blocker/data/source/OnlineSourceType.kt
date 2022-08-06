package com.merxury.blocker.data.source

enum class OnlineSourceType(val baseUrl: String) {
    GITHUB("https://raw.githubusercontent.com/lihenggui/blocker-general-rules/online/"),
    CODING("https://blocker.coding.net/p/blockerrules/d/blocker-general-rules/git/raw/main/")
}