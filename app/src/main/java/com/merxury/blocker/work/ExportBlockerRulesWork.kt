package com.merxury.blocker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.merxury.blocker.rule.Rule
import com.merxury.blocker.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportBlockerRulesWork(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val shouldBackupSystemApp = PreferenceUtil.shouldBackupSystemApps(applicationContext)
        withContext(Dispatchers.IO) {
            Rule.export(applicationContext)
        }
        return Result.success()
    }
}