package com.merxury.function;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
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
     * @param context android context
     * @return list of package info
     */
    public static List<PackageInfo> getApplicationList(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.getInstalledPackages(0);
    }

    /**
     * get a list of installed third party applications
     *
     * @param context android context
     * @return a list of installed third party applications
     */
    public static List<PackageInfo> getThirdPartyApplicationList(Context context) {
        PackageManager pm = context.getPackageManager();
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
     * @param context android context
     * @return a list of installed system applications
     */
    public static List<PackageInfo> getSystemApplicationList(Context context) {
        PackageManager pm = context.getPackageManager();
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
     * @param context     android context
     * @param packageName package name
     * @return list of activity
     */
    public static ActivityInfo[] getActivitiyList(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        ActivityInfo[] activities = null;
        try {
            activities = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES).activities;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return activities;
    }

    /**
     * get a list of receiver of a specified application
     *
     * @param context     android context
     * @param packageName package name
     * @return list of receiver
     */
    public static ActivityInfo[] getReceiverList(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        ActivityInfo[] receivers = null;
        try {
            receivers = pm.getPackageInfo(packageName, PackageManager.GET_RECEIVERS).receivers;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return receivers;
    }

    /**
     * get a list of service of a specified application
     *
     * @param context     android context
     * @param packageName package name
     * @return list of service
     */
    public static ServiceInfo[] getServiceList(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        ServiceInfo[] services = null;
        try {
            services = pm.getPackageInfo(packageName, PackageManager.GET_SERVICES).services;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return services;
    }

    /**
     * get a list of service of a specified application
     *
     * @param context     android context
     * @param packageName package name
     * @return list of provider
     */
    public static ProviderInfo[] getProviderList(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        ProviderInfo[] providers = null;
        try {
            providers = pm.getPackageInfo(packageName, PackageManager.GET_PROVIDERS).providers;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return providers;
    }

    /**
     * get a list of activity info of a specified application
     *
     * @param context     android context
     * @param packageName package name
     * @param flags       usable flags are below
     *                    GET_ACTIVITIES, GET_CONFIGURATIONS, GET_GIDS, GET_INSTRUMENTATION,
     *                    GET_INTENT_FILTERS, GET_PERMISSIONS, GET_PROVIDERS, GET_RECEIVERS,
     *                    GET_SERVICES, GET_SIGNATURES, MATCH_DISABLED_COMPONENTS, MATCH_DISABLED_UNTIL_USED_COMPONENTS
     * @return list of provider
     */
    public static PackageInfo getProviderList(Context context, String packageName, int flags) {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = null;
        try {
            info = pm.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Cannot find specified package.");
        }
        return info;
    }

}
