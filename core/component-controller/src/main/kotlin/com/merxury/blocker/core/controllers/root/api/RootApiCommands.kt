/*
 * Copyright 2025 Blocker
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
import android.content.Intent
import android.content.pm.IPackageDataObserver
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.VersionedPackage
import android.os.Build
import android.os.Parcelable
import be.mygod.librootkotlinx.ParcelableBoolean
import be.mygod.librootkotlinx.ParcelableStringList
import be.mygod.librootkotlinx.RootCommand
import be.mygod.librootkotlinx.systemContext
import com.merxury.blocker.core.controllers.utils.ContextUtils.userId
import com.merxury.blocker.core.utils.ApplicationUtil
import kotlinx.parcelize.Parcelize
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber

private const val MAX_SERVICE_COUNT = 10000

private object RootApiServices {
    private val context get() = systemContext
    val userId get() = context.userId
    val packageName get() = context.packageName

    private val hiddenApiAccess by lazy {
        try {
            HiddenApiBypass.addHiddenApiExemptions("")
        } catch (e: Throwable) {
            Timber.w(e, "Failed to add hidden API exemptions in root process")
        }
    }

    val pm: IPackageManager by lazy {
        hiddenApiAccess
        Timber.d("Get package manager service")
        IPackageManager.Stub.asInterface(
            SystemServiceHelper.getSystemService("package"),
        )
    }

    val am: IActivityManager by lazy {
        hiddenApiAccess
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

    val packageInstaller: IPackageInstaller by lazy {
        IPackageInstaller.Stub.asInterface(
            pm.packageInstaller.asBinder(),
        )
    }

    fun isSystemApp(packageName: String): Boolean = ApplicationUtil.isSystemApp(context.packageManager, packageName)

    fun uninstallResultIntent(): PendingIntent {
        val broadcastIntent = Intent("com.merxury.blocker.UNINSTALL_APP_RESULT_ACTION")
        return PendingIntent.getBroadcast(
            context,
            0,
            broadcastIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

@Parcelize
internal data class SetComponentEnabledSettingCommand(
    private val packageName: String,
    private val componentName: String,
    private val state: Int,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            RootApiServices.pm.setComponentEnabledSetting(
                ComponentName(packageName, componentName),
                state,
                0,
                RootApiServices.userId,
                RootApiServices.packageName,
            )
        } else {
            RootApiServices.pm.setComponentEnabledSetting(
                ComponentName(packageName, componentName),
                state,
                0,
                RootApiServices.userId,
            )
        }
        return ParcelableBoolean(true)
    }
}

@Parcelize
internal data class SetApplicationEnabledSettingCommand(
    private val packageName: String,
    private val newState: Int,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        RootApiServices.pm.setApplicationEnabledSetting(
            packageName,
            newState,
            0,
            RootApiServices.userId,
            RootApiServices.packageName,
        )
        return ParcelableBoolean(true)
    }
}

@Parcelize
internal data class ClearCacheCommand(
    private val packageName: String,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        RootApiServices.pm.deleteApplicationCacheFiles(
            packageName,
            object : IPackageDataObserver.Stub() {
                override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                    Timber.i("Clear cache for $packageName succeeded: $succeeded")
                }
            },
        )
        return ParcelableBoolean(true)
    }
}

@Parcelize
internal data class ClearDataCommand(
    private val packageName: String,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        RootApiServices.pm.clearApplicationUserData(
            packageName,
            object : IPackageDataObserver.Stub() {
                override fun onRemoveCompleted(packageName: String?, succeeded: Boolean) {
                    Timber.i("Clear data for $packageName succeeded: $succeeded")
                }
            },
            RootApiServices.userId,
        )
        return ParcelableBoolean(true)
    }
}

@Parcelize
internal data class UninstallAppCommand(
    private val packageName: String,
    private val versionCode: Long,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        val intent = RootApiServices.uninstallResultIntent()
        val flags = if (RootApiServices.isSystemApp(packageName)) {
            0x00000004
        } else {
            0x00000002
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            RootApiServices.packageInstaller.uninstall(
                VersionedPackage(packageName, versionCode),
                RootApiServices.packageName,
                flags,
                intent.intentSender,
                RootApiServices.userId,
            )
        } else {
            RootApiServices.packageInstaller.uninstall(
                packageName,
                RootApiServices.packageName,
                flags,
                intent.intentSender,
                RootApiServices.userId,
            )
        }
        return ParcelableBoolean(true)
    }
}

@Parcelize
internal data class ForceStopCommand(
    private val packageName: String,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        Timber.i("Force stop $packageName")
        RootApiServices.am.forceStopPackage(packageName, RootApiServices.userId)
        return ParcelableBoolean(true)
    }
}

@Parcelize
internal class RefreshRunningAppListCommand : RootCommand<ParcelableStringList> {
    override suspend fun execute(): ParcelableStringList {
        val packages = RootApiServices.am.runningAppProcesses
            ?.flatMap { it.pkgList?.toList() ?: emptyList() }
            ?.distinct()
            ?: emptyList()
        Timber.v("Loaded ${packages.size} running package names")
        return ParcelableStringList(packages)
    }
}

@Parcelize
internal data class RunningServiceState(
    val packageName: String,
    val className: String,
    val started: Boolean,
) : Parcelable

@Parcelize
internal data class RunningServiceList(
    val services: List<RunningServiceState>,
) : Parcelable

@Parcelize
internal class RefreshRunningServiceListCommand : RootCommand<RunningServiceList> {
    override suspend fun execute(): RunningServiceList {
        val services = RootApiServices.am.getServices(MAX_SERVICE_COUNT, 0)
            .map(ActivityManager.RunningServiceInfo::toRunningServiceState)
        Timber.v("Loaded ${services.size} running services")
        return RunningServiceList(services)
    }
}

@Parcelize
internal data class StartServiceCommand(
    private val packageName: String,
    private val serviceName: String,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        val intent = Intent().apply {
            component = ComponentName(packageName, serviceName)
        }
        val cn = RootApiServices.am.startService(
            null,
            intent,
            intent.type,
            false,
            RootApiServices.packageName,
            null,
            RootApiServices.userId,
        )
        return ParcelableBoolean(
            when {
                cn == null -> {
                    Timber.e("Error: Cannot found $packageName/$serviceName; no service started.")
                    false
                }
                cn.packageName == "!" -> {
                    Timber.e("Error in launching $packageName/$serviceName: Requires permission ${cn.className}")
                    false
                }
                cn.packageName == "!!" -> {
                    Timber.e("Error in launching $packageName/$serviceName:: ${cn.className}")
                    false
                }
                cn.packageName == "?" -> {
                    Timber.e("Error in launching $packageName/$serviceName:: ${cn.className}")
                    false
                }
                else -> true
            },
        )
    }
}

@Parcelize
internal data class StopServiceCommand(
    private val packageName: String,
    private val serviceName: String,
) : RootCommand<ParcelableBoolean> {
    override suspend fun execute(): ParcelableBoolean {
        val intent = Intent().apply {
            component = ComponentName(packageName, serviceName)
        }
        val result = RootApiServices.am.stopService(null, intent, intent.type, RootApiServices.userId)
        return ParcelableBoolean(
            when (result) {
                0 -> {
                    Timber.w("Service $packageName/$serviceName not stopped: was not running.")
                    false
                }
                1 -> {
                    Timber.i("Service $packageName/$serviceName stopped")
                    true
                }
                -1 -> {
                    Timber.e("Error stopping service $packageName/$serviceName")
                    false
                }
                else -> true
            },
        )
    }
}

private fun ActivityManager.RunningServiceInfo.toRunningServiceState() = RunningServiceState(
    packageName = service.packageName,
    className = service.className,
    started = started,
)
