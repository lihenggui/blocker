package com.merxury.blocker.strategy.entity.view

data class Result<T>(
        var code: Int,
        var msg: String,
        var data: T
)