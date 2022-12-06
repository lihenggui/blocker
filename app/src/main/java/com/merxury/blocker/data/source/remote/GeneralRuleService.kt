package com.merxury.blocker.data.source.remote

import com.merxury.blocker.data.source.GeneralRule
import retrofit2.Response
import retrofit2.http.GET

interface GeneralRuleService {
    @GET("zh-cn/general.json")
    suspend fun getOnlineRules(): Response<List<GeneralRule>>
}
