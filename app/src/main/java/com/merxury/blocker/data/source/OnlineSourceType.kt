package com.merxury.blocker.data.source

enum class OnlineSourceType(val baseUrl: String) {
    GITEE("https://gitee.com/Merxury/blocker-general-rules/raw/main/"),
    GITHUB("https://raw.githubusercontent.com/lihenggui/blocker-general-rules/main/")
}