/*
 * Copyright 2025 Blocker
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.merxury.blocker.sync.workers

import android.content.Context
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.tracing.traceAsync
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import com.merxury.blocker.core.data.repository.catalog.CatalogUpdateResult
import com.merxury.blocker.core.data.repository.catalog.CatalogUpdateResult.Reason
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.utils.AppDebugChecker
import com.merxury.blocker.sync.initializers.SyncConstraints
import com.merxury.blocker.sync.initializers.syncForegroundInfo
import com.merxury.blocker.sync.status.ISyncSubscriber
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.time.Instant

private const val FORCE_CHECK = "force_catalog_check"
private const val PREF_SYNC_RULE = "sync_rule"
private const val PREF_LAST_SYNCED_TIME = "last_synced_time"

/**
 * Syncs the data layer by delegating to the appropriate repository instances with
 * sync functionality.
 */

@HiltWorker
internal class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userDataRepository: UserDataRepository,
    private val catalogRepository: CatalogRepository,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val analyticsHelper: AnalyticsHelper,
    private val syncSubscriber: ISyncSubscriber,
    private val appDebugChecker: AppDebugChecker,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo = appContext.syncForegroundInfo()

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        if (!inputData.getBoolean(FORCE_CHECK, false) && !shouldRunTask()) {
            Timber.v("No need to sync rule")
            return@withContext Result.success()
        }
        traceAsync("Sync", 0) {
            analyticsHelper.logSyncStarted()

            syncSubscriber.subscribe()

            val provider = userDataRepository.userData.first().ruleServerProvider
            val result = catalogRepository.update(provider)
            ensureActive()
            analyticsHelper.logSyncFinished(result !is CatalogUpdateResult.Failed)

            when (result) {
                is CatalogUpdateResult.Updated, CatalogUpdateResult.UpToDate -> {
                    markLastRunTime()
                    Result.success()
                }

                is CatalogUpdateResult.Failed -> when (result.reason) {
                    Reason.NETWORK -> Result.retry()
                    Reason.INVALID_DATA, Reason.LOCAL_STORAGE -> Result.failure()
                }
            }
        }
    }

    // Only run this worker in the first run in a day
    private fun shouldRunTask(): Boolean {
        if (appDebugChecker.isDebugMode()) {
            Timber.d("Should run sync task in debug mode each time when app launches")
            return true
        }
        val lastRunTime = getLastRunTime()
        val currentTime = System.currentTimeMillis()
        val shouldRunTask = (currentTime - lastRunTime) > 24 * 60 * 60 * 1000
        Timber.d(
            "Last rule sync time: ${Instant.fromEpochMilliseconds(lastRunTime)}, " +
                "Should run sync task: $shouldRunTask",
        )
        return shouldRunTask
    }

    private fun getLastRunTime(): Long {
        val sharedPreferences =
            appContext.getSharedPreferences(PREF_SYNC_RULE, Context.MODE_PRIVATE)
        return sharedPreferences.getLong(PREF_LAST_SYNCED_TIME, 0)
    }

    private fun markLastRunTime() {
        val sharedPreferences =
            appContext.getSharedPreferences(PREF_SYNC_RULE, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        sharedPreferences.edit { putLong(PREF_LAST_SYNCED_TIME, currentTime) }
        Timber.d("Mark rule sync time: ${Instant.fromEpochMilliseconds(currentTime)}")
    }

    companion object {
        /**
         * Expedited one time work to sync data on app startup
         */
        fun startUpSyncWork(forceCheck: Boolean = false) = OneTimeWorkRequestBuilder<DelegatingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(SyncConstraints)
            .setInputData(
                Data.Builder()
                    .putAll(SyncWorker::class.delegatedData())
                    .putBoolean(FORCE_CHECK, forceCheck)
                    .build(),
            )
            .build()
    }
}
