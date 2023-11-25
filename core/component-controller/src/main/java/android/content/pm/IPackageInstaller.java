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
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.List;

public interface IPackageInstaller extends IInterface {

    int createSession(PackageInstaller.SessionParams params, String installerPackageName,
            int userId)
            throws RemoteException;

    void updateSessionAppIcon(int sessionId, Bitmap appIcon)
            throws RemoteException;

    void updateSessionAppLabel(int sessionId, String appLabel)
            throws RemoteException;

    void abandonSession(int sessionId)
            throws RemoteException;

    IPackageInstallerSession openSession(int sessionId)
            throws RemoteException;

    PackageInstaller.SessionInfo getSessionInfo(int sessionId)
            throws RemoteException;

    ParceledListSlice<PackageInstaller.SessionInfo> getAllSessions(int userId)
            throws RemoteException;

    ParceledListSlice<PackageInstaller.SessionInfo> getMySessions(String installerPackageName,
            int userId)
            throws RemoteException;

    @RequiresApi(29)
    ParceledListSlice<PackageInstaller.SessionInfo> getStagedSessions()
            throws RemoteException;

    void registerCallback(IPackageInstallerCallback callback, int userId)
            throws RemoteException;

    void unregisterCallback(IPackageInstallerCallback callback)
            throws RemoteException;

    // removed from 26
    void uninstall(String packageName, String callerPackageName, int flags,
            IntentSender statusReceiver, int userId);

    @RequiresApi(26)
    void uninstall(VersionedPackage versionedPackage, String callerPackageName, int flags,
            IntentSender statusReceiver, int userId)
            throws RemoteException;

    /**
     * Uninstall the given package, removing it completely from the device. This
     * method is only available to the current "installer of record" for the
     * package.
     *
     * @param packageName    The package to uninstall.
     * @param flags          Flags for uninstall.
     * @param statusReceiver Where to deliver the result of the operation indicated by the extra
     *                       #EXTRA_STATUS. Refer to the individual status codes
     *                       on how to handle them.
     */
    void uninstall(@NonNull String packageName, int flags, @NonNull IntentSender statusReceiver)
            throws RemoteException;

    @RequiresApi(29)
    void installExistingPackage(String packageName, int installFlags, int installReason,
            IntentSender statusReceiver, int userId, List<String> whiteListedPermissions)
            throws RemoteException;

    void setPermissionsResult(int sessionId, boolean accepted)
            throws RemoteException;

    abstract class Stub extends Binder implements IPackageInstaller {

        public static IPackageInstaller asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}