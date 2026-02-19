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
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.analytics.AnalyticsHelper
import com.merxury.blocker.core.data.Synchronizer
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.datastore.BlockerPreferencesDataSource
import com.merxury.blocker.core.datastore.ChangeListVersions
import com.merxury.blocker.core.di.FilesDir
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.git.DefaultGitClient
import com.merxury.blocker.core.git.RepositoryInfo
import com.merxury.blocker.core.network.BlockerNetworkDataSource
import com.merxury.blocker.core.rule.work.CopyRulesToStorageWorker
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.sync.initializers.SyncConstraints
import com.merxury.blocker.sync.initializers.syncForegroundInfo
import com.merxury.blocker.sync.status.ISyncSubscriber
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.time.Instant

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
    @FilesDir private val filesDir: File,
    private val network: BlockerNetworkDataSource,
    private val blockerPreferences: BlockerPreferencesDataSource,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val analyticsHelper: AnalyticsHelper,
    private val syncSubscriber: ISyncSubscriber,
) : CoroutineWorker(appContext, workerParams),
    Synchronizer {

    override suspend fun getForegroundInfo(): ForegroundInfo = appContext.syncForegroundInfo()

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

    override suspend fun getChangeListVersions(): ChangeListVersions = blockerPreferences.getChangeListVersions()

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
        waitForCopyTaskFinish()
        val mainBranchName = "main"
        val repoInfo = RepositoryInfo(
            remoteName = provider.name,
            url = provider.url,
            repoName = provider.projectName,
            branch = mainBranchName,
        )
        val gitClient = DefaultGitClient(repoInfo, filesDir)
        // Detect the folder is a git repository or not
        val projectFolder = filesDir.resolve(repoInfo.repoName)
        val gitFolder = projectFolder.resolve(".git")
        try {
            if (projectFolder.exists()) {
                if (!gitFolder.exists()) {
                    // Repo not initialized, delete the folder and clone again
                    Timber.i("Local rule folder is not a git repository, delete and clone again")
                    projectFolder.deleteRecursively()
                    gitClient.setRemote(repoInfo.url, provider.name)
                    gitClient.cloneRepository()
                } else {
                    // Repo initialized, pull the latest changes
                    gitClient.setRemote(repoInfo.url, provider.name)
                    gitClient.pull()
                }
            } else {
                // Repo not exists, clone the repository
                gitClient.setRemote(repoInfo.url, provider.name)
                gitClient.cloneRepository()
            }
        } catch (e: Exception) {
            // If it is in the debug mode, throw the exception
            if (ApplicationUtil.isDebugMode(appContext)) {
                throw e
            }
            Timber.e(e, "Failed to sync rules from remote")
            return false
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
        return sharedPreferences.getLong(PREF_LAST_SYNCED_TIME, 0)
    }

    private fun markLastRunTime() {
        val sharedPreferences =
            appContext.getSharedPreferences(PREF_SYNC_RULE, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        sharedPreferences.edit { putLong(PREF_LAST_SYNCED_TIME, currentTime) }
        Timber.d("Mark rule sync time: ${Instant.fromEpochMilliseconds(currentTime)}")
    }

    private suspend fun waitForCopyTaskFinish() {
        val workManager = WorkManager.getInstance(appContext)
        workManager.getWorkInfosForUniqueWorkFlow(CopyRulesToStorageWorker.WORK_NAME)
            .takeWhile {
                val workInfo = it.firstOrNull()
                val isRunning = workInfo?.state == WorkInfo.State.RUNNING
                if (isRunning) {
                    Timber.v("Copy asset task is running, waiting...")
                } else {
                    Timber.v("Copy asset task is not running, continue syncing rules.")
                }
                isRunning
            }
            .collect()
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
