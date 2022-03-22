package com.merxury.blocker.data.source

import androidx.lifecycle.MutableLiveData
import com.merxury.blocker.data.source.local.GeneralRuleDao
import com.merxury.blocker.data.source.remote.GeneralRuleService
import javax.inject.Inject

class GeneralRuleRepository @Inject constructor(
    private val removeDataSource: GeneralRuleService,
    private val localDataSource: GeneralRuleDao
) {
    val rules = MutableLiveData<List<GeneralRule>>()

    suspend fun getRules() = removeDataSource.getOnlineRules()
}