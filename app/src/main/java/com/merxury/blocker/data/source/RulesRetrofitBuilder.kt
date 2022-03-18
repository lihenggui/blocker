package com.merxury.blocker.data.source

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RulesRetrofitBuilder {
    private const val URL_GITHUB =
        "https://raw.githubusercontent.com/lihenggui/blocker-general-rules/main/zh-cn/"
    private const val URL_GITEE = "https://gitee.com/Merxury/blocker-general-rules/raw/main/zh-cn/"
    val apiService = Retrofit.Builder()
        .baseUrl(URL_GITEE)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GeneralRuleService::class.java)
}