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
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.merxury.blocker.core.data.wake.WakeAuthorizationRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.wake.WakeProtectionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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

@Singleton
class WakeWork @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authorization: WakeAuthorizationRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : DefaultLifecycleObserver {
    private var shizukuUid: Int? = null

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
        // Binder and permission callbacks arrive on the main thread. They only inspect existing grants.
        authorization.observeShizukuAuthorization(::onShizukuChanged)
        scope.launch {
            authorization.rootGranted.collect { if (it) enqueue(context, ExistingWorkPolicy.REPLACE) }
        }
        scope.launch {
            // Enforce as soon as the channel can write, such as after the grant at first launch.
            authorization.channelWritable.collect { if (it) enqueue(context, ExistingWorkPolicy.REPLACE) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        onShizukuChanged(authorization.shizukuUid)
        if (shizukuUid != 0) {
            scope.launch(ioDispatcher) {
                // Ask su about root for reading evidence only while Blocker is in the foreground.
                authorization.checkForegroundRoot()
            }
        }
    }

    private fun onShizukuChanged(uid: Int?) {
        if (shizukuUid == uid) return
        shizukuUid = uid
        // A job backing off after a disconnected service must not delay a new grant.
        enqueue(context, ExistingWorkPolicy.REPLACE)
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
