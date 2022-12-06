package com.merxury.blocker.data.source.remote

import com.merxury.blocker.data.source.BaseDataSource
import javax.inject.Inject

class RuleRemoteDataSource @Inject constructor(private val service: GeneralRuleService) :
    BaseDataSource() {
    suspend fun getRules() = getResult { service.getOnlineRules() }
}
