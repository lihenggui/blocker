// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.sync.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import com.merxury.blocker.core.controllers.wake.authorizedShizukuUid
import com.merxury.blocker.core.data.respository.userdata.UserDataRepository
import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.dispatchers.BlockerDispatchers.IO
import com.merxury.blocker.core.dispatchers.Dispatcher
import com.merxury.blocker.core.domain.wake.WakeProtectionManager
import com.merxury.blocker.core.model.data.ControllerType
import com.merxury.blocker.core.utils.LibrootRootAvailabilityChecker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
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
    private val root: LibrootRootAvailabilityChecker,
    private val userData: UserDataRepository,
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
        enqueue(context)
        // Binder and permission callbacks arrive on the main thread. They only inspect existing grants.
        Shizuku.addBinderReceivedListenerSticky { refreshShizuku() }
        Shizuku.addBinderDeadListener { onShizukuChanged(null) }
        Shizuku.addRequestPermissionResultListener { _, _ -> refreshShizuku() }
        scope.launch {
            root.rootGranted.collect { if (it) enqueue(context, ExistingWorkPolicy.REPLACE) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        refreshShizuku()
        val needsDirectRoot = shizukuUid != 0
        scope.launch(ioDispatcher) {
            // Ask the existing controller about direct root only while Blocker is in the foreground.
            if (userData.userData.first().controllerType != ControllerType.SHIZUKU && needsDirectRoot) root.isRootAvailable()
        }
    }

    private fun refreshShizuku() = onShizukuChanged(authorizedShizukuUid())

    private fun onShizukuChanged(uid: Int?) {
        if (shizukuUid == uid) return
        shizukuUid = uid
        // A job backing off after a disconnected service must not delay a new grant.
        enqueue(context, ExistingWorkPolicy.REPLACE)
    }

    companion object {
        internal fun enqueue(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) = WorkManager.getInstance(context).enqueueUniqueWork(
            "wake-history-update",
            policy,
            OneTimeWorkRequestBuilder<DelegatingWorker>().setInputData(WakeHistoryWorker::class.delegatedData()).build(),
        )
    }
}

class WakePackagesChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_FULLY_REMOVED,
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_MANAGED_PROFILE_ADDED,
            Intent.ACTION_MANAGED_PROFILE_REMOVED, Intent.ACTION_MANAGED_PROFILE_AVAILABLE, Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
            -> {
                val pending = goAsync()
                try {
                    WakeWork.enqueue(context).result.addListener(pending::finish, Runnable::run)
                } catch (e: RuntimeException) {
                    pending.finish()
                    throw e
                }
            }
        }
    }
}
