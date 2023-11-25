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
import android.app.IActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.VersionedPackage
import android.net.Uri
import android.os.Build
import android.os.UserHandleHidden
import android.provider.Settings
import com.merxury.blocker.core.controllers.IAppController
import com.merxury.blocker.core.utils.ApplicationUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
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
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Timber.i("Add hidden API exemptions")
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/app/IActivityManager;",
                "Landroid/app/IActivityManager\$Stub;",
                "Landroid/content/pm/IPackageManager;",
                "Landroid/content/pm/IPackageInstaller;",
                "Landroid/content/pm/IPackageInstaller\$Stub;",
                "Landroid/os/UserHandle;",
            )
        }
    }

    private val pm: IPackageManager by lazy {
        Timber.d("Get package manager service from ShizukuBinderWrapper")
        IPackageManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("package"),
            ),
        )
    }

    private val am: IActivityManager by lazy {
        Timber.d("Get activity manager service from ShizukuBinderWrapper")
        IActivityManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService("activity"),
            ),
        )
    }

    private val packageInstaller: IPackageInstaller by lazy {
        Timber.d("Get package installer service from IPackageManager")
        IPackageInstaller.Stub.asInterface(
            ShizukuBinderWrapper(
                pm.packageInstaller.asBinder(),
            ),
        )
    }

    private var currentRunningProcess = mutableListOf<ActivityManager.RunningAppProcessInfo>()

    override suspend fun disable(packageName: String): Boolean {
        Timber.i("Disable $packageName")
        val uid = Shizuku.getUid()
        if (uid == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }

        val userId = UserHandleHidden.getUserId(uid)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pm.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_DISABLED,
                0,
                userId,
                context.packageName,
            )
        } else {
            pm.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_DISABLED,
                0,
                userId,
            )
        }
        return true
    }

    override suspend fun enable(packageName: String): Boolean {
        Timber.i("Enable $packageName")
        val uid = Shizuku.getUid()
        if (uid == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }

        val userId = UserHandleHidden.getUserId(uid)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            pm.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_ENABLED,
                0,
                userId,
                context.packageName,
            )
        } else {
            pm.setApplicationEnabledSetting(
                packageName,
                COMPONENT_ENABLED_STATE_ENABLED,
                0,
                userId,
            )
        }
        return true
    }

    override suspend fun clearCache(packageName: String): Boolean {
        Timber.i("Start clear cache: $packageName")
        val uid = Shizuku.getUid()
        if (uid == SHELL_UID) {
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
        val uid = Shizuku.getUid()
        if (uid == SHELL_UID) {
            openAppDetails(packageName)
            return true
        }

        val userId = UserHandleHidden.getUserId(uid)
        return suspendCoroutine { cont ->
            pm.clearApplicationUserData(
                packageName,
                object : IPackageDataObserver.Stub() {
                    override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                        Timber.i("Clear data for $packageName succeeded: $succeeded")
                        cont.resumeWith(Result.success(succeeded))
                    }
                },
                userId,
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
                0,
            )
        } else {
            packageInstaller.uninstall(
                packageName,
                context.packageName,
                flags,
                intent.intentSender,
                0,
            )
        }

        return true
    }

    override suspend fun forceStop(packageName: String): Boolean {
        Timber.i("Force stop $packageName")
        val processes = am.getRunningAppProcesses()
        Timber.e(processes.toString())
        am.forceStopPackage(packageName, 0)
        return true
    }

    override suspend fun refreshRunningAppList() {
        if (!Shizuku.pingBinder()) {
            // Avoid calling this method when Shizuku is not connected
            return
        }
        currentRunningProcess = am.runningAppProcesses ?: mutableListOf()
    }

    override fun isAppRunning(packageName: String): Boolean {
        return currentRunningProcess.any { it.processName == packageName }
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
