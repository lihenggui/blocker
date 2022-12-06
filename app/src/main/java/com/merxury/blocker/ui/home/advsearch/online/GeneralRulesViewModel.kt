package com.merxury.blocker.ui.home.advsearch.online

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.elvishew.xlog.XLog
import com.merxury.blocker.data.source.GeneralRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GeneralRulesViewModel @Inject constructor(private val repo: GeneralRuleRepository) :
    ViewModel() {
    private val logger = XLog.tag("GeneralRulesViewModel")
    private val reloadTrigger = MutableLiveData<Boolean>()
    val rules = Transformations.switchMap(reloadTrigger) { repo.getRules() }

    init {
        reloadTrigger.value = true
    }

    fun refresh() {
        logger.i("Refresh data")
        reloadTrigger.value = true
    }
}
