package com.merxury.core;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Mercury on 2017/12/30.
 * A class that gets activities, broadcasts, content providers, and services
 */

public class ApplicationComponents {
    private static final String TAG = "ApplicationComponents";

    /**
     * Get a list of installed applications on device
     *
     * @param pm PackageManager
     * @return list of package info
     */
    public static List<PackageInfo> getApplicationList(PackageManager pm) {
        return pm.getInstalledPackages(0);
    }

    /**
     * get a list of installed third party applications
     *
     * @param pm PackageManager
     * @return a list of installed third party applications
     */
    public static List<PackageInfo> getThirdPartyApplicationList(PackageManager pm) {
        List<PackageInfo> thirdPartyList = new ArrayList<>(64);
        List<PackageInfo> installedApplications = pm.getInstalledPackages(0);
        for (PackageInfo info : installedApplications) {
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                thirdPartyList.add(info);
            }
        }
        return thirdPartyList;
    }

    /**
     * get a list of system applications
     *
     * @param pm PackageManager
     * @return a list of installed system applications
     */
    public static List<PackageInfo> getSystemApplicationList(PackageManager pm) {

        List<PackageInfo> sysAppList = new ArrayList<>(64);
        List<PackageInfo> installedApplications = pm.getInstalledPackages(0);
        for (PackageInfo info : installedApplications) {
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                //System App
                sysAppList.add(info);
            }
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                //app was installed as an update to a built-in system app
                sysAppList.add(info);
            }
        }
        return sysAppList;
    }

    /**
     * get a list of activity of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of activity
     */
    @NonNull
    public static ActivityInfo[] getActivityList(@NonNull PackageManager pm, @NonNull String packageName) {
        ActivityInfo[] activities = null;
        try {
            int flags = PackageManager.GET_ACTIVITIES;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            activities = pm.getPackageInfo(packageName, flags).activities;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return activities == null ? new ActivityInfo[0] : activities;
    }

    /**
     * get a list of receiver of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of receiver
     */
    @NonNull
    public static ActivityInfo[] getReceiverList(@NonNull PackageManager pm, @NonNull String packageName) {
        ActivityInfo[] receivers = null;
        try {
            int flags = PackageManager.GET_RECEIVERS;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            receivers = pm.getPackageInfo(packageName, flags).receivers;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }

        return receivers == null ? new ActivityInfo[0] : receivers;
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of service
     */
    @NonNull
    public static ServiceInfo[] getServiceList(@NonNull PackageManager pm, @NonNull String packageName) {
        ServiceInfo[] services = null;
        try {
            int flags = PackageManager.GET_SERVICES;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            services = pm.getPackageInfo(packageName, flags).services;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return services == null ? new ServiceInfo[0] : services;
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of provider
     */
    @NonNull
    public static ProviderInfo[] getProviderList(@NonNull PackageManager pm, @NonNull String packageName) {
        ProviderInfo[] providers = null;
        try {
            int flags = PackageManager.GET_PROVIDERS;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            providers = pm.getPackageInfo(packageName, flags).providers;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return providers == null ? new ProviderInfo[0] : providers;
    }

    /**
     * get a list of components of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @param flags       usable flags are below
     *                    GET_ACTIVITIES, GET_CONFIGURATIONS, GET_GIDS, GET_INSTRUMENTATION,
     *                    GET_INTENT_FILTERS, GET_PERMISSIONS, GET_PROVIDERS, GET_RECEIVERS,
     *                    GET_SERVICES, GET_SIGNATURES, MATCH_DISABLED_COMPONENTS (API level 24), MATCH_DISABLED_UNTIL_USED_COMPONENTS(API level 24)
     * @return a set of components
     */
    public static PackageInfo getApplicationComponents(@NonNull PackageManager pm, @NonNull String packageName, int flags) {
        PackageInfo info = null;
        try {
            info = pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return info;
    }

    /**
     * get a list of components of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return a set of components
     */
    @NonNull
    public static PackageInfo getApplicationComponents(@NonNull PackageManager pm, @NonNull String packageName) throws PackageManager.NameNotFoundException {
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS |
                PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES |
                PackageManager.GET_INTENT_FILTERS;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
        } else {
            flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
        }
        PackageInfo info = null;
        try {
            info = pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }

        return info;
    }

    /**
     * check a component is enabled or not
     *
     * @param pm          PackageManager
     * @param componentName name of a component
     * @return true : component is enabled , false: component is disabled
     */
    public static boolean checkComponentIsEnabled(PackageManager pm, ComponentName componentName) {
        int state = pm.getComponentEnabledSetting(componentName);
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }

}
