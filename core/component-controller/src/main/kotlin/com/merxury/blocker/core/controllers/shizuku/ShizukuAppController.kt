/*
 * Copyright 2023 Blocker
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

package com.merxury.blocker.core.controllers.shizuku

import android.app.ActivityManager
import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.VersionedPackage
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.ContextUtils.userId
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.suspendCoroutine

private const val SHELL_UID = 2000

class ShizukuAppController @Inject constructor(
    @ApplicationContext private val context: Context,
) : IAppController {

    private val pm: IPackageManager by lazy {
        IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("package"),
            ),
        )
    }

    private val am: IActivityManager by lazy {
        Timber.d("Get activity manager service from ShizukuBinderWrapper")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IActivityManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("activity"),
                ),
            )
        } else {
            ActivityManagerNative.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("activity"),
                ),
            )
        }
    }

    private val packageInstaller: IPackageInstaller by lazy {
        IPackageInstaller.Stub.asInterface(
            ShizukuBinderWrapper(
                pm.packageInstaller.asBinder(),
            ),
        )
    }

    private var currentRunningProcess = mutableListOf<ActivityManager.RunningAppProcessInfo>()

    override suspend fun disable(packageName: String): Boolean {
        Timber.i("Disable $packageName")
        try {
            pm.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_DISABLED_USER,
                0,
                context.userId,
                context.packageName,
            )
        } catch (e: SecurityException) {
            Timber.e(e, "Cannot disable $packageName, redirect to app details")
            openAppDetails(packageName)
            return false
        }
        return true
    }

    override suspend fun enable(packageName: String): Boolean {
        Timber.i("Enable $packageName")
        try {
            pm.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_ENABLED,
                0,
                context.userId,
                context.packageName,
            )
        } catch (e: SecurityException) {
            Timber.e(e, "Cannot enable $packageName, redirect to app details")
            openAppDetails(packageName)
            return false
        }
        return true
    }

    override suspend fun clearCache(packageName: String): Boolean {
        Timber.i("Start clear cache: $packageName")
        val userId = Shizuku.getUid()
        if (userId == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }
        return suspendCoroutine { cont ->
            pm.deleteApplicationCacheFiles(
                packageName,
                object : IPackageDataObserver.Stub() {
                    override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                        Timber.i("Clear cache for $packageName succeeded: $succeeded")
                        cont.resumeWith(Result.success(succeeded))
                    }
                },
            )
        }
    }

    override suspend fun clearData(packageName: String): Boolean {
        Timber.i("Start clear data: $packageName")
        val userId = Shizuku.getUid()
        if (userId == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }
        return suspendCoroutine { cont ->
            pm.clearApplicationUserData(
                packageName,
                object : IPackageDataObserver.Stub() {
                    override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                        Timber.i("Clear data for $packageName succeeded: $succeeded")
                        cont.resumeWith(Result.success(succeeded))
                    }
                },
                context.userId,
            )
        }
    }

    override suspend fun uninstallApp(packageName: String, versionCode: Long): Boolean {
        val broadcastIntent = Intent("com.merxury.blocker.UNINSTALL_APP_RESULT_ACTION")
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val isSystemApp = ApplicationUtil.isSystemApp(context.packageManager, packageName)
        // 0x00000004 = PackageManager.DELETE_SYSTEM_APP
        // 0x00000002 = PackageManager.DELETE_ALL_USERS
        val flags = if (isSystemApp) 0x00000004 else 0x00000002
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInstaller.uninstall(
                VersionedPackage(packageName, versionCode),
                context.packageName,
                flags,
                intent.intentSender,
                context.userId,
            )
        } else {
            packageInstaller.uninstall(
                packageName,
                context.packageName,
                flags,
                intent.intentSender,
                context.userId,
            )
        }

        return true
    }

    override suspend fun forceStop(packageName: String): Boolean {
        Timber.i("Force stop $packageName")
        am.forceStopPackage(packageName, context.userId)
        return true
    }

    override suspend fun refreshRunningAppList() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return
            }
        } catch (e: Throwable) {
            Timber.e(e, "Check Shizuku permission failed")
            return
        }
        if (!Shizuku.pingBinder()) {
            // Avoid calling this method when Shizuku is not connected
            return
        }
        currentRunningProcess = am.runningAppProcesses ?: mutableListOf()
    }

    override fun isAppRunning(packageName: String): Boolean {
        return currentRunningProcess.any { packageName in it.pkgList }
    }

    private fun openAppDetails(packageName: String) {
        Timber.i("Open app details for $packageName")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.setData(uri)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}
