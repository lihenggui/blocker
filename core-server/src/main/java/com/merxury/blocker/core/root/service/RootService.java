package com.merxury.blocker.core.root.service;

import android.content.ComponentName;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

public class RootService extends IRootService.Stub {
    public static final String NAME = "blocker.rootservice";
    private static final String TAG = "RootService";
    private static IPackageManager mPm;


    public RootService() {
        mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package"));
    }

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

    @Override
    public int getUid() throws RemoteException {
        return Binder.getCallingUid();
    }

    @Override
    public int getPid() throws RemoteException {
        return Binder.getCallingPid();
    }


    public boolean switchComponent(ComponentName name, int state) {
        try {
            mPm.setComponentEnabledSetting(name, state, PackageManager.DONT_KILL_APP, Binder.getCallingUid());
        }catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            return false;
        }
        return true;
    }

}
