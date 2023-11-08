/*
 * Copyright 2023 Blocker
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

package android.content.pm;

import android.content.ComponentName;
import android.os.*;
import androidx.annotation.RequiresApi;

public interface IPackageManager extends IInterface {

    IPackageInstaller getPackageInstaller()
            throws RemoteException;

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId, String callingPackage)
            throws RemoteException;

    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId)
            throws RemoteException;

    int getComponentEnabledSetting(ComponentName componentName, int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void setApplicationEnabledSetting(String packageName, int newState, int flags, int userId, String callingPackage)
            throws RemoteException;

    void setApplicationEnabledSetting(String packageName, int newState, int flags, int userId)
            throws RemoteException;

    int getApplicationEnabledSetting(String packageName, int userId)
            throws RemoteException;

    void deletePackage(String packageName, IPackageDeleteObserver observer, int flags);

    abstract class Stub extends Binder implements IPackageManager {

        public static IPackageManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}