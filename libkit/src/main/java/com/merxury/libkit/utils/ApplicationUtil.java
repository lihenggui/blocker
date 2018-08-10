package com.merxury.libkit.utils;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;

import com.merxury.libkit.entity.Application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Mercury on 2017/12/30.
 * A class that gets activities, broadcasts, content providers, and services
 */

public class ApplicationUtil {
    private static final String TAG = "ApplicationUtil";
    private static final String MARKET_URL = "market://details?id=";

    /**
     * Get a list of installed applications on device
     *
     * @param pm PackageManager
     * @return list of application info
     */
    public static List<Application> getApplicationList(@NonNull PackageManager pm) {
        List<PackageInfo> installedApplications = pm.getInstalledPackages(0);
        List<Application> appList = new ArrayList<>(installedApplications.size());
        for (PackageInfo info : installedApplications) {
            appList.add(new Application(pm, info));
        }
        return appList;
    }

    /**
     * get a list of installed third party applications
     *
     * @param pm PackageManager
     * @return a list of installed third party applications
     */
    @NonNull
    public static List<Application> getThirdPartyApplicationList(@NonNull PackageManager pm) {
        List<PackageInfo> installedApplications = pm.getInstalledPackages(0);
        List<Application> thirdPartyList = new ArrayList<>(installedApplications.size());
        for (PackageInfo info : installedApplications) {
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                thirdPartyList.add(new Application(pm, info));
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
    @NonNull
    public static List<Application> getSystemApplicationList(@NonNull PackageManager pm) {

        List<PackageInfo> installedApplications = pm.getInstalledPackages(0);
        List<Application> sysAppList = new ArrayList<>(installedApplications.size());
        for (PackageInfo info : installedApplications) {
            if ((info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                //System App
                sysAppList.add(new Application(pm, info));
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
    public static List<ComponentInfo> getActivityList(@NonNull PackageManager pm, @NonNull String packageName) {
        List<ComponentInfo> activities = new ArrayList<>();
        try {
            int flags = PackageManager.GET_ACTIVITIES;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            ComponentInfo[] components = pm.getPackageInfo(packageName, flags).activities;
            if (components != null && components.length > 0) {
                Collections.addAll(activities, components);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        } catch (RuntimeException e) {
            Log.e(TAG, e.getMessage());
            return ApkUtils.INSTANCE.getActivities(pm, packageName);
        }
        return activities;
    }

    /**
     * get a list of receiver of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of receiver
     */
    @NonNull
    public static List<ComponentInfo> getReceiverList(@NonNull PackageManager pm, @NonNull String packageName) {
        List<ComponentInfo> receivers = new ArrayList<>();
        try {
            int flags = PackageManager.GET_RECEIVERS;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            ComponentInfo[] components = pm.getPackageInfo(packageName, flags).receivers;
            if (components != null && components.length > 0) {
                Collections.addAll(receivers, components);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }

        return receivers;
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of service
     */
    @NonNull
    public static List<ComponentInfo> getServiceList(@NonNull PackageManager pm, @NonNull String packageName) {
        List<ComponentInfo> services = new ArrayList<>();
        try {
            int flags = PackageManager.GET_SERVICES;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            ComponentInfo[] components = pm.getPackageInfo(packageName, flags).services;
            if (components != null && components.length > 0) {
                Collections.addAll(services, components);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return services;
    }

    /**
     * get a list of service of a specified application
     *
     * @param pm          PackageManager
     * @param packageName package name
     * @return list of provider
     */
    @NonNull
    public static List<ComponentInfo> getProviderList(@NonNull PackageManager pm, @NonNull String packageName) {
        List<ComponentInfo> providers = new ArrayList<>();
        try {
            int flags = PackageManager.GET_PROVIDERS;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                flags = flags | PackageManager.GET_DISABLED_COMPONENTS;
            } else {
                flags = flags | PackageManager.MATCH_DISABLED_COMPONENTS;
            }
            ComponentInfo[] components = pm.getPackageInfo(packageName, flags).providers;
            if (components != null && components.length > 0) {
                Collections.addAll(providers, components);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return providers;
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
    public static PackageInfo getApplicationComponents(@NonNull PackageManager pm, @NonNull String packageName) {
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
        if (info == null) {
            return new PackageInfo();
        }
        return info;
    }

    /**
     * check a component is enabled or not
     *
     * @param pm            PackageManager
     * @param componentName name of a component
     * @return true : component is enabled , false: component is disabled
     */
    public static boolean checkComponentIsEnabled(@NonNull PackageManager pm, @NonNull ComponentName componentName) {
        int state;
        try {
            state = pm.getComponentEnabledSetting(componentName);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            return false;
        }
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }

    /**
     * check an application is installed or not
     *
     * @param pm PackageManager
     * @return true : component is enabled , false: component is disabled
     */
    public static boolean isAppInstalled(@NonNull PackageManager pm, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        try {
            pm.getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, packageName + "is not installed.");
        }
        return false;
    }
}
