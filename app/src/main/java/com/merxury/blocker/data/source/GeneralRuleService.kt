package com.merxury.blocker.data.source

import com.merxury.blocker.ui.home.advsearch.online.GeneralRule
import retrofit2.http.GET

interface GeneralRuleService {
    @GET("general.json")
    suspend fun getOnlineRules(): List<GeneralRule>
}