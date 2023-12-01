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

package com.merxury.blocker.core.controllers.root.api

import android.app.ActivityManager
import android.app.ActivityManagerNative
import android.app.IActivityManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.VersionedPackage
import android.os.Build
import android.os.IBinder
import com.merxury.blocker.core.controller.root.service.IRootService
import com.merxury.blocker.core.utils.ApplicationUtil
import com.merxury.blocker.core.utils.ContextUtils.userId
import com.topjohnwu.superuser.ipc.RootService
import timber.log.Timber

private const val MAX_SERVICE_COUNT = 10000

class RootServer : RootService() {
    override fun onCreate() {
        super.onCreate()
        Timber.d("RootService onCreate")
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.d("RootService onBind")
        return Ipc(this)
    }

    override fun onRebind(intent: Intent) {
        super.onRebind(intent)
        Timber.d("AIDLService: onRebind, daemon process reused")
    }

    override fun onUnbind(intent: Intent): Boolean {
        super.onUnbind(intent)
        Timber.d("AIDLService: onUnbind, client process unbound")
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("RootService onDestroy")
    }

    class Ipc(private val context: Context) : IRootService.Stub() {
        private var serviceList: List<ActivityManager.RunningServiceInfo> = listOf()
        private var currentRunningProcess = mutableListOf<ActivityManager.RunningAppProcessInfo>()

        private val pm: IPackageManager by lazy {
            Timber.d("Get package manager service")
            IPackageManager.Stub.asInterface(
                SystemServiceHelper.getSystemService("package"),
            )
        }

        private val am: IActivityManager by lazy {
            Timber.d("Get activity manager service")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IActivityManager.Stub.asInterface(
                    SystemServiceHelper.getSystemService("activity"),
                )
            } else {
                ActivityManagerNative.asInterface(
                    SystemServiceHelper.getSystemService("activity"),
                )
            }
        }

        private val packageInstaller: IPackageInstaller by lazy {
            IPackageInstaller.Stub.asInterface(
                pm.packageInstaller.asBinder(),
            )
        }

        override fun setComponentEnabledSetting(
            packageName: String?,
            componentName: String?,
            state: Int,
        ): Boolean {
            if (packageName == null || componentName == null) {
                Timber.w("Invalid component info provided")
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, componentName),
                    state,
                    0,
                    context.userId,
                    context.packageName,
                )
            } else {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, componentName),
                    state,
                    0,
                    context.userId,
                )
            }
            return true
        }

        override fun setApplicationEnabledSetting(packageName: String?, newState: Int) {
            pm.setApplicationEnabledSetting(
                packageName,
                newState,
                0,
                context.userId,
                context.packageName,
            )
        }

        override fun clearCache(packageName: String?): Boolean {
            pm.deleteApplicationCacheFiles(
                packageName,
                object : IPackageDataObserver.Stub() {
                    override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                        Timber.i("Clear cache for $packageName succeeded: $succeeded")
                    }
                },
            )
            return true
        }

        override fun clearData(packageName: String?): Boolean {
            pm.clearApplicationUserData(
                packageName,
                object : IPackageDataObserver.Stub() {
                    override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                        Timber.i("Clear data for $packageName succeeded: $succeeded")
                    }
                },
                context.userId,
            )
            return true
        }

        override fun uninstallApp(packageName: String?, versionCode: Long): Boolean {
            if (packageName == null) {
                Timber.w("Invalid package name provided")
                return false
            }
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

        override fun forceStop(packageName: String?): Boolean {
            Timber.i("Force stop $packageName")
            am.forceStopPackage(packageName, context.userId)
            return true
        }

        override fun refreshRunningAppList(): Boolean {
            currentRunningProcess = am.runningAppProcesses ?: mutableListOf()
            Timber.v("Loaded ${currentRunningProcess.size} running processes")
            return true
        }

        override fun refreshRunningServiceList(): Boolean {
            serviceList = am.getServices(MAX_SERVICE_COUNT, 0)
            Timber.v("Loaded ${serviceList.size} running services")
            return true
        }

        override fun isAppRunning(packageName: String?): Boolean {
            return currentRunningProcess.any { packageName in it.pkgList }
        }

        override fun isServiceRunning(packageName: String?, serviceName: String?): Boolean {
            return serviceList.any {
                it.service.packageName == packageName && it.service.className == serviceName
            }
        }

        override fun startService(packageName: String?, serviceName: String?): Boolean {
            if (packageName == null || serviceName == null) {
                Timber.w("Invalid component info provided")
                return false
            }
            val intent = Intent().apply {
                setComponent(ComponentName(packageName, serviceName))
            }
            val cn = am.startService(
                null,
                intent,
                intent.type,
                false,
                context.packageName,
                null,
                context.userId,
            )
            return if (cn == null) {
                Timber.e("Error: Cannot found $packageName/$serviceName; no service started.")
                false
            } else if (cn.packageName == "!") {
                Timber.e("Error in launching $packageName/$serviceName: Requires permission " + cn.className)
                false
            } else if (cn.packageName == "!!") {
                Timber.e("Error in launching $packageName/$serviceName:: " + cn.className)
                false
            } else if (cn.packageName == "?") {
                Timber.e("Error in launching $packageName/$serviceName:: " + cn.className)
                false
            } else {
                true
            }
        }

        override fun stopService(packageName: String?, serviceName: String?): Boolean {
            if (packageName == null || serviceName == null) {
                Timber.w("Invalid component info provided")
                return false
            }
            val intent = Intent().apply {
                setComponent(ComponentName(packageName, serviceName))
            }
            val result = am.stopService(null, intent, intent.type, context.userId)
            return when (result) {
                0 -> {
                    Timber.w("Service $packageName/$serviceName not stopped: was not running.")
                    false
                }

                1 -> {
                    Timber.i("Service $packageName/$serviceName stopped")
                    false
                }

                -1 -> {
                    Timber.e("Error stopping service $packageName/$serviceName")
                    false
                }

                else -> true
            }
        }
    }
}
