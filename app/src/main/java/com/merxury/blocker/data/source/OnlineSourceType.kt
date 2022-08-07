package com.merxury.blocker.data.source

enum class OnlineSourceType(val baseUrl: String) {
    GITHUB("https://raw.githubusercontent.com/lihenggui/blocker-general-rules/main/"),
    GITLAB("https://gitlab.com/mercuryli/blocker-general-rules/-/raw/main/")
}