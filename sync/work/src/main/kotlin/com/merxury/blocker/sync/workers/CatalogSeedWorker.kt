// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.merxury.blocker.core.data.repository.catalog.CatalogInstallResult
import com.merxury.blocker.core.data.repository.catalog.CatalogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import kotlin.time.TimeSource.Monotonic

@HiltWorker
class CatalogSeedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CatalogRepository,
    private val legacyRuleCacheCleanup: LegacyRuleCacheCleanup,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) return Result.failure()
        val start = Monotonic.markNow()
        val result = repository.installBundled()
        val duration = start.elapsedNow()
        val seedResult = when (result) {
            is CatalogInstallResult.Installed -> {
                Timber.i("Installed bundled catalog sequence %d in %s", result.state.sequence, duration)
                Result.success()
            }

            CatalogInstallResult.SkippedNotNewer -> {
                Timber.i("Skipped bundled catalog in %s because its sequence is current or older", duration)
                Result.success()
            }

            is CatalogInstallResult.Rejected -> {
                Timber.w("Bundled catalog rejected after %s: %s", duration, result.reason)
                Result.failure()
            }
        }
        return if (legacyRuleCacheCleanup.run()) seedResult else retryOrFail()
    }

    internal companion object {
        const val WORK_NAME = "catalog-seed"

        fun startupWork() = OneTimeWorkRequestBuilder<DelegatingWorker>()
            .setInputData(CatalogSeedWorker::class.delegatedData())
            .build()
    }
}
