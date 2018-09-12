package com.merxury.libkit.utils

import android.content.ComponentName
import android.content.pm.*
import android.os.Build
import android.util.Log
import com.merxury.libkit.entity.Application
import java.util.*

/**
 * Created by Mercury on 2017/12/30.
 * A class that gets activities, broadcasts, content providers, and services
 */

object ApplicationUtil {
    private val TAG = "ApplicationUtil"
    private val MARKET_URL = "market://details?id="

    /**
     * Get a list of installed applications on device
     *
     * @param pm PackageManager
     * @return list of application info
     */
    fun getApplicationList(pm: PackageManager): MutableList<Application> {
        val installedApplications = pm.getInstalledPackages(0)
        val appList = ArrayList<Application>(installedApplications.size)
        for (info in installedApplications) {
            appList.add(Application(pm, info))
        }
        return appList
    }

    /**
     * get a list of installed third party applications
     *
     * @param pm PackageManager
     * @return a list of installed third party applications
     */
    fun getThirdPartyApplicationList(pm: PackageManager): MutableList<Application> {
        val installedApplications = pm.getInstalledPackages(0)
        val thirdPartyList = ArrayList<Application>(installedApplications.size)
        for (info in installedApplications) {
            if (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                thirdPartyList.add(Application(pm, info))
            }
        }
        return thirdPartyList
    }

    /**
     * get a list of system applications
     *
     * @param pm PackageManager
     * @return a list of installed system applications
     */
    fun getSystemApplicationList(pm: PackageManager): MutableList<Application> {

        val installedApplications = pm.getInstalledPackages(0)
        val sysAppList = ArrayList<Application>(installedApplications.size)
        for (info in installedApplications) {
            if (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) {
                //System App
                sysAppList.add(Application(pm, info))
            }
        }
        return sysAppList
    }

    /**
     * get a list of activity of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of activity
     */
    fun getActivityList(pm: PackageManager, packageName: String): MutableList<ActivityInfo> {
        val activities = ArrayList<ActivityInfo>()
        try {
            var flags = PackageManager.GET_ACTIVITIES
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags or PackageManager.GET_DISABLED_COMPONENTS
            } else {
                flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS
            }
            val components = pm.getPackageInfo(packageName, flags).activities
            if (components != null && components.size > 0) {
                Collections.addAll(activities, *components)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot find specified package.")
        } catch (e: RuntimeException) {
            Log.e(TAG, e.message)
            return ApkUtils.getActivities(pm, packageName)
        }

        return activities
    }

    /**
     * get a list of receiver of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of receiver
     */
    fun getReceiverList(pm: PackageManager, packageName: String): MutableList<ActivityInfo> {
        val receivers = ArrayList<ActivityInfo>()
        try {
            var flags = PackageManager.GET_RECEIVERS
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags or PackageManager.GET_DISABLED_COMPONENTS
            } else {
                flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS
            }
            val components = pm.getPackageInfo(packageName, flags).receivers
            if (components != null && components.size > 0) {
                Collections.addAll(receivers, *components)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot find specified package.")
        }

        return receivers
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of service
     */
    fun getServiceList(pm: PackageManager, packageName: String): MutableList<ServiceInfo> {
        val services = ArrayList<ServiceInfo>()
        try {
            var flags = PackageManager.GET_SERVICES
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags or PackageManager.GET_DISABLED_COMPONENTS
            } else {
                flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS
            }
            val components = pm.getPackageInfo(packageName, flags).services
            if (components != null && components.size > 0) {
                Collections.addAll(services, *components)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot find specified package.")
        }

        return services
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of provider
     */
    fun getProviderList(pm: PackageManager, packageName: String): MutableList<ProviderInfo> {
        val providers = ArrayList<ProviderInfo>()
        try {
            var flags = PackageManager.GET_PROVIDERS
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags or PackageManager.GET_DISABLED_COMPONENTS
            } else {
                flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS
            }
            val components = pm.getPackageInfo(packageName, flags).providers
            if (components != null && components.size > 0) {
                Collections.addAll(providers, *components)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot find specified package.")
        }

        return providers
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
    fun getApplicationComponents(pm: PackageManager, packageName: String, flags: Int): PackageInfo? {
        var info: PackageInfo? = null
        try {
            info = pm.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot find specified package.")
        }

        return info
    }

    /**
     * get a list of components of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return a set of components
     */
    fun getApplicationComponents(pm: PackageManager, packageName: String): PackageInfo {
        var flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_PROVIDERS or
                PackageManager.GET_RECEIVERS or PackageManager.GET_SERVICES or
                PackageManager.GET_INTENT_FILTERS
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            flags = flags or PackageManager.GET_DISABLED_COMPONENTS
        } else {
            flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS
        }
        var info = PackageInfo()
        try {
            info = pm.getPackageInfo(packageName, flags)
        } catch (e: RuntimeException) {
            Log.e(TAG, e.message)
            info = getPackageInfoFromManifest(pm, packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot find specified package.")
        }

        return info
    }

    private fun getPackageInfoFromManifest(pm: PackageManager, packageName: String): PackageInfo {
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
            Log.e(TAG, e.message)
            return false
        }

        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    /**
     * check an application is installed or not
     *
     * @param pm PackageManager
     * @return true : component is enabled , false: component is disabled
     */
    fun isAppInstalled(pm: PackageManager, packageName: String?): Boolean {
        if (packageName == null || packageName.trim { it <= ' ' }.isEmpty()) {
            return false
        }
        try {
            pm.getApplicationInfo(packageName, 0)
            return true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.d(TAG, packageName + "is not installed.")
        }

        return false
    }
}
