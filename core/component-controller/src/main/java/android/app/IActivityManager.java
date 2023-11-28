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

package android.app;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import androidx.annotation.RequiresApi;

import java.util.List;

public interface IActivityManager extends IInterface {
    void forceStopPackage(String packageName, int userId)
            throws RemoteException;

    List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses()
            throws RemoteException;

    List<ActivityManager.RunningServiceInfo> getServices(int maxNum, int flags)
            throws RemoteException;

    void killBackgroundProcesses(String packageName) throws RemoteException;

    void getMemoryInfo(ActivityManager.MemoryInfo outInfo) throws RemoteException;

    ComponentName startService(IApplicationThread caller, Intent service,
            String resolvedType, boolean requireForeground, String callingPackage,
            String callingFeatureId, int userId);

    int stopService(IApplicationThread caller, Intent service, String resolvedType, int userId);

    @RequiresApi(26)
    abstract class Stub extends Binder implements IActivityManager {

        public static IActivityManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
