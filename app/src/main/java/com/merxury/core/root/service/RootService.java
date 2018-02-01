package com.merxury.core.root.service;

import android.content.ComponentName;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import com.merxury.core.IController;
import com.stericson.RootShell.containers.RootClass;

public class RootService implements IController{
    private static final String TAG = "RootController-fast";
    private static IPackageManager mPm;
    private static RootService instance;

    @Override
    public boolean switchComponent(String packageName, String componentName, int state) {
        ComponentName cn = new ComponentName(packageName, componentName);
        try {
            mPm.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP, Binder.getCallingUid());
        }catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

    public static RootService getInstance() {
        if(mPm == null) {
            synchronized (RootService.class) {
                if (mPm == null) {
                    mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
                }
            }
        }
        if(instance == null) {
            synchronized (RootService.class) {
                if(instance == null) {
                    instance = new RootService();
                }
            }
        }
        return instance;
    }
}
