// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import com.merxury.blocker.core.data.wake.WakeAuthorizationRepository
import com.merxury.blocker.core.data.wake.WakeRunRequester
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.domain.wake.WakeProtectionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class WakeHistoryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val protection: WakeProtectionManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) return Result.failure()
        return if (protection.reconcile()) Result.success() else Result.retry()
    }
}

/**
 * Schedules the wake feature's collection: every 30 minutes, after other packages change, and as
 * soon as the privileged channel becomes ready or changes identity while no collection is waiting
 * or running, such as after the grant at first launch. It only observes the channel; acquiring it
 * is up to the channel and the screens.
 */
@Singleton
class WakeWork @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authorization: WakeAuthorizationRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) : WakeRunRequester {
    private val initialized = AtomicBoolean(false)

    override val isRunning: Flow<Boolean>
        get() = WorkManager.getInstance(context).getWorkInfosFlow(activeOneTimeWork)
            .map { workInfos ->
                workInfos.any { info ->
                    info.state == WorkInfo.State.RUNNING ||
                        (info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount == 0)
                }
            }
            .distinctUntilChanged()

    override fun requestRun() {
        // A collector may already have read the old overrides or be waiting to retry.
        enqueue(context, ExistingWorkPolicy.REPLACE, REQUESTED_WORK)
    }

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "wake-history-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DelegatingWorker>(30, TimeUnit.MINUTES)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .setInputData(WakeHistoryWorker::class.delegatedData()).build(),
        )
        // Android 8+ restricts manifest delivery for other packages' installs and updates.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.registerReceiver(
                context,
                WakePackagesChangedReceiver(),
                IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        enqueue(context)
        scope.launch {
            // Any identity reads, root and system also write. A run already waiting or running
            // stays: the run itself connects the channel quietly, so replacing it here would
            // cancel it, and a run backing off after the channel was lost would restart at once.
            authorization.readyIdentity.collect { if (it != null) enqueue(context) }
        }
    }

    companion object {
        private const val UPDATE_WORK = "wake-history-update"
        private const val REQUESTED_WORK = "wake-history-requested"
        internal const val PACKAGE_CHANGE_WORK = "wake-history-package-change"
        private val activeOneTimeWork = WorkQuery.Builder
            .fromUniqueWorkNames(listOf(UPDATE_WORK, REQUESTED_WORK, PACKAGE_CHANGE_WORK))
            .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
            .build()

        internal fun enqueue(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
            name: String = UPDATE_WORK,
        ) = WorkManager.getInstance(context).enqueueUniqueWork(
            name,
            policy,
            OneTimeWorkRequestBuilder<DelegatingWorker>().setInputData(WakeHistoryWorker::class.delegatedData()).build(),
        )
    }
}

class WakePackagesChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_FULLY_REMOVED,
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED, Intent.ACTION_MANAGED_PROFILE_AVAILABLE, Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            -> {
                val pending = goAsync()
                try {
                    // A package update must run even if the regular collector is backing off.
                    WakeWork.enqueue(context, ExistingWorkPolicy.REPLACE, WakeWork.PACKAGE_CHANGE_WORK).result.addListener(pending::finish, Runnable::run)
                } catch (e: RuntimeException) {
                    pending.finish()
                    throw e
                }
            }
        }
    }
}
