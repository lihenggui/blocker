package android.content.pm;

import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IPackageManager extends IInterface {

    IPackageInstaller getPackageInstaller()
            throws RemoteException;

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId)
            throws RemoteException;

    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId)
            throws RemoteException;

    int getComponentEnabledSetting(ComponentName componentName, int userId)
            throws RemoteException;

    void setApplicationEnabledSetting(String packageName, int newState, int flags, int userId)
            throws RemoteException;

    int getApplicationEnabledSetting(String packageName, int userId)
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {

        public static IPackageManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}