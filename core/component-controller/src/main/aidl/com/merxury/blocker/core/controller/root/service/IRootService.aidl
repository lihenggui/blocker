package com.merxury.blocker.core.controller.root.service;

interface IRootService {
    boolean setComponentEnabledSetting(String packageName, String componentName, int state);
    void setApplicationEnabledSetting(String packageName, int newState);
    boolean clearCache(String packageName);
    boolean clearData(String packageName);
    boolean uninstallApp(String packageName, long versionCode);
    boolean forceStop(String packageName);
    boolean isAppRunning(String packageName);
    boolean isServiceRunning(String packageName, String serviceName);
    boolean startService(String packageName, String serviceName);
    boolean stopService(String packageName, String serviceName);
    boolean refreshRunningAppList();
    boolean refreshRunningServiceList();
}
