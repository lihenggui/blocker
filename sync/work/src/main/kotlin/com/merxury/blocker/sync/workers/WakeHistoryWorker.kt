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
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.data.wake.WakeAuthorizationRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.domain.wake.WakeProtectionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class WakeHistoryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val protection: WakeProtectionManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (protection.reconcile()) Result.success() else Result.retry()
}

/**
 * Schedules the wake feature's collection: every 30 minutes, after other packages change, and as
 * soon as the privileged channel becomes ready or changes identity, such as after the grant at
 * first launch. It only observes the channel; acquiring it is up to the channel and the screens.
 */
@Singleton
class WakeWork @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authorization: WakeAuthorizationRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun initialize() {
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
            // Any identity reads, root and system also write. A job backing off after the
            // channel was lost must not delay its return.
            authorization.readyIdentity.collect { if (it != null) enqueue(context, ExistingWorkPolicy.REPLACE) }
        }
    }

    companion object {
        internal fun enqueue(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
            name: String = "wake-history-update",
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
                    WakeWork.enqueue(context, ExistingWorkPolicy.REPLACE, "wake-history-package-change").result.addListener(pending::finish, Runnable::run)
                } catch (e: RuntimeException) {
                    pending.finish()
                    throw e
                }
            }
        }
    }
}
