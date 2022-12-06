package com.merxury.blocker.data.source

import androidx.lifecycle.MutableLiveData
import com.merxury.blocker.data.source.local.GeneralRuleDao
import com.merxury.blocker.data.source.remote.RuleRemoteDataSource
import javax.inject.Inject

class GeneralRuleRepository @Inject constructor(
    private val remoteDataSource: RuleRemoteDataSource,
    private val localDataSource: GeneralRuleDao
) {
    val rules = MutableLiveData<List<GeneralRule>>()

    fun getRules() = performGetOperation(
        databaseQuery = { localDataSource.getAll() },
        networkCall = { remoteDataSource.getRules() },
        saveCallResult = { localDataSource.insertAll(it) }
    )
}
