package com.merxury.blocker.ui.strategy.entity.view

data class Result<T>(
        var code: Int,
        var msg: String,
        var data: T
)