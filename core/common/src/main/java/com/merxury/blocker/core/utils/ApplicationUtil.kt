/*
 * Copyright 2022 Blocker
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

/**
 * Created by Mercury on 2017/12/30.
 * A class that gets activities, broadcasts, content providers, and services
 */

package com.merxury.blocker.core.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.os.Build
import com.elvishew.xlog.XLog
import com.merxury.blocker.core.extension.getApplicationInfoCompat
import com.merxury.blocker.core.extension.getInstalledPackagesCompat
import com.merxury.blocker.core.extension.getPackageInfoCompat
import com.merxury.blocker.core.model.Application
import com.merxury.blocker.core.model.toApplication
import java.util.Collections
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ApplicationUtil {
    private const val BLOCKER_PACKAGE_NAME = "com.merxury.blocker"
    private val logger = XLog.tag("ApplicationUtil").build()

    /**
     * Get a list of installed applications on device
     *
     * @param context Context
     * @return list of application info
     */
    suspend fun getApplicationList(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<Application> {
        val pm = context.packageManager
        return withContext(dispatcher) {
            val installedApp = pm.getInstalledPackagesCompat(0)
            installedApp.asSequence()
                .filterNot { it.packageName == BLOCKER_PACKAGE_NAME }
                .map { it.toApplication(pm) }
                .toMutableList()
        }
    }

    /**
     * get a list of installed third party applications
     *
     * @param context Context
     * @return a list of installed third party applications
     */
    suspend fun getThirdPartyApplicationList(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<Application> {
        val pm = context.packageManager
        return withContext(dispatcher) {
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getInstalledPackages(0)
            }
            installedPackages.asSequence()
                .filter { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .filterNot { it.packageName == BLOCKER_PACKAGE_NAME }
                .map { it.toApplication(pm) }
                .toMutableList()
        }
    }

    /**
     * get a list of system applications
     *
     * @param context Context
     * @return a list of installed system applications
     */
    suspend fun getSystemApplicationList(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<Application> {
        val pm = context.packageManager
        return withContext(dispatcher) {
            val installedPackages = pm.getInstalledPackagesCompat(0)
            installedPackages.asSequence()
                .filter { it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
                .map { it.toApplication(pm) }
                .toMutableList()
        }
    }

    /**
     * get a list of activity of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of activity
     */
    suspend fun getActivityList(
        pm: PackageManager,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<ActivityInfo> {
        return withContext(dispatcher) {
            val activities = ArrayList<ActivityInfo>()
            try {
                var flags = PackageManager.GET_ACTIVITIES
                flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    flags or @Suppress("DEPRECATION") PackageManager.GET_DISABLED_COMPONENTS
                } else {
                    flags or PackageManager.MATCH_DISABLED_COMPONENTS
                }
                val components = pm.getPackageInfoCompat(packageName, flags).activities
                if (components != null && components.isNotEmpty()) {
                    Collections.addAll(activities, *components)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                logger.w("Cannot find specified package $packageName.")
            } catch (e: RuntimeException) {
                logger.e("Failed to fetch activity list for $packageName", e)
                return@withContext ApkUtils.getActivities(pm, packageName)
            }
            activities
        }
    }

    /**
     * get a list of receiver of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of receiver
     */
    suspend fun getReceiverList(
        pm: PackageManager,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<ActivityInfo> {
        return withContext(dispatcher) {
            val receivers = ArrayList<ActivityInfo>()
            try {
                var flags = PackageManager.GET_RECEIVERS
                flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    flags or @Suppress("DEPRECATION") PackageManager.GET_DISABLED_COMPONENTS
                } else {
                    flags or PackageManager.MATCH_DISABLED_COMPONENTS
                }
                val components = pm.getPackageInfoCompat(packageName, flags).receivers
                if (components != null && components.isNotEmpty()) {
                    Collections.addAll(receivers, *components)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                logger.e("Cannot find specified package $packageName.")
            }
            receivers
        }
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of service
     */

    suspend fun getServiceList(
        pm: PackageManager,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<ServiceInfo> {
        return withContext(dispatcher) {
            val services = ArrayList<ServiceInfo>()
            try {
                var flags = PackageManager.GET_SERVICES
                flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    flags or @Suppress("DEPRECATION") PackageManager.GET_DISABLED_COMPONENTS
                } else {
                    flags or PackageManager.MATCH_DISABLED_COMPONENTS
                }
                val components = pm.getPackageInfoCompat(packageName, flags).services
                if (components != null && components.isNotEmpty()) {
                    Collections.addAll(services, *components)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                logger.e("Cannot find specified package $packageName.")
            } catch (e: RuntimeException) {
                logger.e("Failed to fetch service list $packageName", e)
                return@withContext ApkUtils.getServices(pm, packageName)
            }
            services
        }
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of provider
     */
    suspend fun getProviderList(
        pm: PackageManager,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): MutableList<ProviderInfo> {
        return withContext(dispatcher) {
            val providers = ArrayList<ProviderInfo>()
            try {
                var flags = PackageManager.GET_PROVIDERS
                flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    flags or @Suppress("DEPRECATION") PackageManager.GET_DISABLED_COMPONENTS
                } else {
                    flags or PackageManager.MATCH_DISABLED_COMPONENTS
                }
                val components = pm.getPackageInfoCompat(packageName, flags).providers
                if (components != null && components.isNotEmpty()) {
                    Collections.addAll(providers, *components)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                logger.e("Cannot find specified package $packageName.")
            }
            providers
        }
    }

    /**
     * get a list of components of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @param flags       usable flags are below
     * GET_ACTIVITIES, GET_CONFIGURATIONS, GET_GIDS, GET_INSTRUMENTATION,
     * GET_INTENT_FILTERS, GET_PERMISSIONS, GET_PROVIDERS, GET_RECEIVERS,
     * GET_SERVICES, GET_SIGNATURES, MATCH_DISABLED_COMPONENTS (API level 24), MATCH_DISABLED_UNTIL_USED_COMPONENTS(API level 24)
     * @return a set of components
     */
    suspend fun getApplicationComponents(
        pm: PackageManager,
        packageName: String,
        flags: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): PackageInfo? {
        return withContext(dispatcher) {
            var info: PackageInfo? = null
            try {
                info = pm.getPackageInfoCompat(packageName, flags)
            } catch (e: PackageManager.NameNotFoundException) {
                logger.e("Cannot find specified package.")
            }
            info
        }
    }

    suspend fun getApplicationInfo(
        context: Context,
        packageName: String
    ): Application? {
        val pm = context.packageManager
        val info = getApplicationComponents(pm, packageName, 0) ?: return null
        return info.toApplication(pm)
    }

    /**
     * get a list of components of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return a set of components
     */

    suspend fun getApplicationComponents(
        pm: PackageManager,
        packageName: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): PackageInfo {
        return withContext(dispatcher) {
            var flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS or
                PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or
                @Suppress("DEPRECATION") PackageManager.GET_INTENT_FILTERS
            flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags or @Suppress("DEPRECATION") PackageManager.GET_DISABLED_COMPONENTS
            } else {
                flags or PackageManager.MATCH_DISABLED_COMPONENTS
            }
            var info = PackageInfo()
            try {
                info = pm.getPackageInfoCompat(packageName, flags)
            } catch (e: RuntimeException) {
                logger.e("Can't get application components", e)
                info = getPackageInfoFromManifest(pm, packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                logger.e("Cannot find specified package.")
            }
            info
        }
    }

    private suspend fun getPackageInfoFromManifest(
        pm: PackageManager,
        packageName: String
    ): PackageInfo {
        val info = PackageInfo()
        info.packageName = packageName
        info.activities = getActivityList(pm, packageName).toTypedArray()
        info.services = getServiceList(pm, packageName).toTypedArray()
        info.receivers = getReceiverList(pm, packageName).toTypedArray()
        info.providers = getProviderList(pm, packageName).toTypedArray()
        return info
    }

    /**
     * check a component is enabled or not
     *
     * @param pm            PackageManager
     * @param componentName name of a component
     * @return true : component is enabled , false: component is disabled
     */
    fun checkComponentIsEnabled(pm: PackageManager, componentName: ComponentName): Boolean {
        val state: Int
        try {
            state = pm.getComponentEnabledSetting(componentName)
        } catch (e: Exception) {
            e.printStackTrace()
            logger.e(e.message)
            return false
        }
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    /**
     * check an application is installed or not
     *
     * @param pm PackageManager
     * @return true : component is enabled , false: component is disabled
     */
    fun isAppInstalled(pm: PackageManager, packageName: String?): Boolean {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getApplicationInfo(packageName, 0)
            }
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            logger.d(packageName + "is not installed.")
        }
        return false
    }

    fun isSystemApp(pm: PackageManager, packageName: String?): Boolean {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false
        }
        try {
            val info = pm.getApplicationInfoCompat(packageName, 0)
            return info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        } catch (e: PackageManager.NameNotFoundException) {
            logger.d(packageName + "is not installed.")
        }
        return false
    }

    suspend fun isProvider(
        pm: PackageManager,
        packageName: String,
        componentName: String
    ): Boolean {
        return getProviderList(pm, packageName).any { it.name == componentName }
    }

    suspend fun isActivity(
        pm: PackageManager,
        packageName: String,
        componentName: String
    ): Boolean {
        return getActivityList(pm, packageName).any { it.name == componentName }
    }
}
