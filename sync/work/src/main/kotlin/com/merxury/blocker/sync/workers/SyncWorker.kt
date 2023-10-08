/*
 * Copyright 2023 Blocker
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
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.Synchronizer
import com.merxury.blocker.core.data.di.CacheDir
import com.merxury.blocker.core.data.di.FilesDir
import com.merxury.blocker.core.data.di.RuleBaseFolder
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.ChangeListVersions
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.network.io.BinaryFileWriter
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.FileUtils
import com.merxury.blocker.sync.initializers.SyncConstraints
import com.merxury.blocker.sync.initializers.syncForegroundInfo
import com.merxury.blocker.sync.status.ISyncSubscriber
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipException

private const val PREF_SYNC_RULE = "sync_rule"
private const val PREF_LAST_SYNCED_TIME = "last_synced_time"
private const val RULE_ZIP_FILENAME = "rules.zip"

/**
 * Syncs the data layer by delegating to the appropriate repository instances with
 * sync functionality.
 */

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userDataRepository: UserDataRepository,
    @CacheDir private val cacheDir: File,
    @FilesDir private val filesDir: File,
    @RuleBaseFolder private val ruleBaseFolder: String,
    private val network: BlockerNetworkDataSource,
    private val blockerPreferences: BlockerPreferencesDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val analyticsHelper: AnalyticsHelper,
    private val syncSubscriber: ISyncSubscriber,
) : CoroutineWorker(appContext, workerParams), Synchronizer {

    override suspend fun getForegroundInfo(): ForegroundInfo =
        appContext.syncForegroundInfo()

    override suspend fun doWork(): Result = withContext(ioDispatcher) {
        if (!shouldRunTask()) {
            Timber.v("No need to sync rule")
            return@withContext Result.success()
        }
        traceAsync("Sync", 0) {
            analyticsHelper.logSyncStarted()

            syncSubscriber.subscribe()

            val syncedSuccessfully = syncRule()

            analyticsHelper.logSyncFinished(syncedSuccessfully)

            if (syncedSuccessfully) {
                markLastRunTime()
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    override suspend fun getChangeListVersions(): ChangeListVersions =
        blockerPreferences.getChangeListVersions()

    override suspend fun updateChangeListVersions(
        update: ChangeListVersions.() -> ChangeListVersions,
    ) = blockerPreferences.updateChangeListVersion(update)

    private suspend fun syncRule(): Boolean {
        Timber.d("Syncing rule...")
        val provider = userDataRepository.userData.first().ruleServerProvider
        val localCommitId = getChangeListVersions().ruleCommitId
        val latestCommitId = network.getRuleLatestCommitId(provider)
            .ruleCommitId
        if (localCommitId.isNotBlank() && localCommitId == latestCommitId) {
            Timber.i("Local rule is up to date, skip syncing rules.")
            return true
        }
        Timber.i(
            "Last synced commit id: $localCommitId, latest commit id: $latestCommitId" +
                ", start pulling rule...",
        )
        val ruleFolder = cacheDir.resolve(ruleBaseFolder)
        if (!ruleFolder.exists()) {
            ruleFolder.mkdirs()
        }
        val file = File(ruleFolder, RULE_ZIP_FILENAME)
        try {
            file.outputStream().use { outputStream ->
                network.downloadRules(provider, BinaryFileWriter(outputStream))
            }
            // unzip the folder to rule folder
            FileUtils.unzip(file, ruleFolder.absolutePath)
            // Assume the name of the unzipped folder is 'blocker-general-rules-main
            // Rename to 'blocker-general-rules', and copy to filesDir
            val unzippedFolder = ruleFolder.resolve("blocker-general-rules-main")
            if (!unzippedFolder.exists()) {
                Timber.e("Unzipped folder $unzippedFolder does not exist")
                return false
            }
            val targetFolder = filesDir.resolve(ruleBaseFolder)
            if (targetFolder.exists()) {
                targetFolder.deleteRecursively()
            }
            unzippedFolder.copyRecursively(targetFolder, overwrite = true)
            unzippedFolder.deleteRecursively()
        } catch (e: IOException) {
            Timber.e(e, "Failed to sync rule")
            return false
        } catch (e: ZipException) {
            Timber.e(e, "Cannot unzip the file")
            return false
        } finally {
            file.deleteRecursively()
        }
        // write latest commit id to preference
        updateChangeListVersions {
            copy(ruleCommitId = latestCommitId)
        }
        return true
    }

    // Only run this worker in the first run in a day
    private fun shouldRunTask(): Boolean {
        if (ApplicationUtil.isDebugMode(appContext)) {
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
        return sharedPreferences.getLong(PREF_LAST_SYNCED_TIME, System.currentTimeMillis())
    }

    private fun markLastRunTime() {
        val sharedPreferences =
            appContext.getSharedPreferences(PREF_SYNC_RULE, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        sharedPreferences.edit().putLong(PREF_LAST_SYNCED_TIME, currentTime).apply()
        Timber.d("Mark rule sync time: ${Instant.fromEpochMilliseconds(currentTime)}")
    }

    companion object {
        /**
         * Expedited one time work to sync data on app startup
         */
        fun startUpSyncWork() = OneTimeWorkRequestBuilder<DelegatingWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(SyncConstraints)
            .setInputData(SyncWorker::class.delegatedData())
            .build()
    }
}
