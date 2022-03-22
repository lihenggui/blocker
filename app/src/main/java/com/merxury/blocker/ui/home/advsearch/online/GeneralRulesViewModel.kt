package com.merxury.blocker.ui.home.advsearch.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.elvishew.xlog.XLog
import com.merxury.blocker.data.source.GeneralRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GeneralRulesViewModel @Inject constructor(private val repo: GeneralRuleRepository) :
    ViewModel() {
    private val logger = XLog.tag("GeneralRulesViewModel")

    val rules = repo.getRules()

    class Factory(private val repo: GeneralRuleRepository) :
        ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GeneralRulesViewModel(repo) as T
        }
    }
}