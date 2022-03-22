package com.merxury.blocker.ui.home.advsearch.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData
import com.elvishew.xlog.XLog
import com.merxury.blocker.data.Resource
import com.merxury.blocker.data.source.GeneralRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class GeneralRulesViewModel @Inject constructor(private val repo: GeneralRuleRepository) :
    ViewModel() {
    private val logger = XLog.tag("GeneralRulesViewModel")

    fun fetchRules() = liveData(Dispatchers.IO) {
        emit(Resource.loading(data = null))
        try {
            emit(Resource.success(data = repo.getRules()))
        } catch (e: Exception) {
            logger.e("Failed to get rules", e)
            emit(Resource.error(data = null, message = e.message ?: "Can't fetch rules"))
        }
    }

    class Factory(private val repo: GeneralRuleRepository) :
        ViewModelProvider.NewInstanceFactory() {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GeneralRulesViewModel(repo) as T
        }
    }
}