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
import com.merxury.blocker.core.datastore.BlockerAppPropertiesDataStore
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.model.preference.RuleServerProvider
import com.merxury.blocker.core.utils.AppDebugChecker
import com.merxury.blocker.sync.initializers.SyncConstraints
import com.merxury.blocker.sync.initializers.syncForegroundInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private const val FORCE_CHECK = "force_catalog_check"
private val SYNC_INTERVAL = 1.days

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
    private val appProperties: BlockerAppPropertiesDataStore,
    private val clock: Clock,
    private val appDebugChecker: AppDebugChecker,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo = appContext.syncForegroundInfo()

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) return@withContext Result.failure()
        try {
            val provider = userDataRepository.userData.first().ruleServerProvider
            if (!inputData.getBoolean(FORCE_CHECK, false) && !shouldRunTask(provider)) {
                Timber.v("No need to sync rule")
                return@withContext Result.success()
            }
            syncCatalog(provider)
        } catch (e: IOException) {
            Timber.w(e, "Cannot read or persist catalog sync state")
            retryOrFail()
        }
    }

    private suspend fun syncCatalog(provider: RuleServerProvider): Result = traceAsync("Sync", 0) {
        analyticsHelper.logSyncStarted()
        val result = catalogRepository.update(provider)
        currentCoroutineContext().ensureActive()
        analyticsHelper.logSyncFinished(result !is CatalogUpdateResult.Failed)

        when (result) {
            is CatalogUpdateResult.Updated, CatalogUpdateResult.UpToDate -> {
                appProperties.setLastCatalogSynced(clock.now().toEpochMilliseconds(), provider.name)
                Result.success()
            }

            is CatalogUpdateResult.Failed -> when (result.reason) {
                Reason.NETWORK -> retryOrFail()
                Reason.INVALID_DATA, Reason.LOCAL_STORAGE -> Result.failure()
            }
        }
    }

    private suspend fun shouldRunTask(provider: RuleServerProvider): Boolean {
        if (appDebugChecker.isDebugMode()) return true
        val (lastSyncedEpochMs, lastSyncedProvider) = appProperties.lastCatalogSync.first()
        val currentEpochMs = clock.now().toEpochMilliseconds()
        // Check again after a clock rollback so a future timestamp cannot suppress new checks.
        return lastSyncedEpochMs == 0L || lastSyncedProvider != provider.name || currentEpochMs < lastSyncedEpochMs ||
            currentEpochMs - lastSyncedEpochMs >= SYNC_INTERVAL.inWholeMilliseconds
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
