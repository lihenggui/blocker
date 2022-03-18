package com.merxury.blocker.data.source

import androidx.lifecycle.MutableLiveData
import com.merxury.blocker.ui.home.advsearch.online.GeneralRule

class GeneralRuleRepository(private val service: GeneralRuleService) {
    val rules = MutableLiveData<List<GeneralRule>>()

    suspend fun getRules() = service.getOnlineRules()
}