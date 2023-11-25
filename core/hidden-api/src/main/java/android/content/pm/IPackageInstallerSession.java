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

import android.content.IntentSender;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface IPackageInstallerSession extends IInterface {

    void setClientProgress(float progress)
            throws RemoteException;

    void addClientProgress(float progress)
            throws RemoteException;

    String[] getNames()
            throws RemoteException;

    ParcelFileDescriptor openWrite(String name, long offsetBytes, long lengthBytes)
            throws RemoteException;

    ParcelFileDescriptor openRead(String name)
            throws RemoteException;

    @RequiresApi(27)
    void write(String name, long offsetBytes, long lengthBytes, ParcelFileDescriptor fd)
            throws RemoteException;

    @RequiresApi(24)
    void removeSplit(String splitName)
            throws RemoteException;

    void close()
            throws RemoteException;

    // removed from 28
    void commit(IntentSender statusReceiver)
            throws RemoteException;

    @RequiresApi(28)
    void commit(IntentSender statusReceiver, boolean forTransferred)
            throws RemoteException;

    @RequiresApi(28)
    void transfer(String packageName)
            throws RemoteException;

    void abandon()
            throws RemoteException;

    @RequiresApi(29)
    boolean isMultiPackage()
            throws RemoteException;

    @RequiresApi(29)
    int[] getChildSessionIds()
            throws RemoteException;

    @RequiresApi(29)
    void addChildSessionId(int sessionId)
            throws RemoteException;

    @RequiresApi(29)
    void removeChildSessionId(int sessionId)
            throws RemoteException;

    @RequiresApi(29)
    int getParentSessionId()
            throws RemoteException;

    @RequiresApi(29)
    boolean isStaged()
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageInstallerSession {

        public static IPackageInstallerSession asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}