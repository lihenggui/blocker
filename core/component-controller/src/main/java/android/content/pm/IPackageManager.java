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
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import androidx.annotation.RequiresApi;

public interface IPackageManager extends IInterface {

    IPackageInstaller getPackageInstaller()
            throws RemoteException;

    ParceledListSlice<PackageInfo> getInstalledPackages(int flags, int userId)
            throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId, String callingPackage)
            throws RemoteException;

    /**
     * Set the enabled setting for a package component (activity, receiver, service, provider).
     * This setting will override any enabled state which may have been set by the component in its
     * manifest.
     *
     * @param componentName The component to enable
     * @param newState The new enabled state for the component.
     * @param flags Optional behavior flags.
     */
    void setComponentEnabledSetting(ComponentName componentName, int newState, int flags, int userId)
            throws RemoteException;

    int getComponentEnabledSetting(ComponentName componentName, int userId)
            throws RemoteException;

    /**
     * Set the enabled setting for an application
     * This setting will override any enabled state which may have been set by the application in
     * its manifest.  It also overrides the enabled state set in the manifest for any of the
     * application's components.  It does not override any enabled state set by
     * {@link #setComponentEnabledSetting} for any of the application's components.
     *
     * @param packageName The package name of the application to enable
     * @param newState The new enabled state for the application.
     * @param flags Optional behavior flags.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    void setApplicationEnabledSetting(String packageName, int newState, int flags, int userId, String callingPackage)
            throws RemoteException;

    void setApplicationEnabledSetting(String packageName, int newState, int flags, int userId)
            throws RemoteException;

    /**
     * Return the enabled setting for an application. This returns
     * the last value set by
     * #setApplicationEnabledSetting(String, int, int); in most
     * cases this value will be #COMPONENT_ENABLED_STATE_DEFAULT since
     * the value originally specified in the manifest has not been modified.
     *
     * @param packageName The package name of the application to retrieve.
     * @return Returns the current enabled state for the application.
     * @throws IllegalArgumentException if the named package does not exist.
     */
    int getApplicationEnabledSetting(String packageName, int userId)
            throws RemoteException;

    /**
     * Attempts to delete a package. Since this may take a little while, the
     * result will be posted back to the given observer. A deletion will fail if
     * the calling context lacks the
     * {@link android.Manifest.permission#DELETE_PACKAGES} permission, if the
     * named package cannot be found, or if the named package is a system
     * package.
     *
     * @param packageName The name of the package to delete
     * @param observer An observer callback to get notified when the package
     *            deletion is complete.
     *            {@link android.content.pm.IPackageDeleteObserver#packageDeleted}
     *            will be called when that happens. observer may be null to
     *            indicate that no callback is desired.
     */
    void deletePackage(String packageName, IPackageDeleteObserver observer, int flags)
            throws RemoteException;

    /**
     * Attempts to delete a package. Since this may take a little while, the
     * result will be posted back to the given observer. A deletion will fail if
     * the named package cannot be found, or if the named package is a system
     * package.
     *
     * @param packageName The name of the package to delete
     * @param observer An observer callback to get notified when the package
     *            deletion is complete.
     *            {@link android.content.pm.IPackageDeleteObserver#packageDeleted}
     *            will be called when that happens. observer may be null to
     *            indicate that no callback is desired.
     * @param userId The user Id
     */
    void deletePackageAsUser(String packageName, IPackageDeleteObserver observer, int flags, int userId)
            throws RemoteException;

    /**
     * Attempts to clear the user data directory of an application.
     * Since this may take a little while, the result will
     * be posted back to the given observer.  A deletion will fail if the
     * named package cannot be found, or if the named package is a "system package".
     *
     * @param packageName The name of the package
     * @param observer An observer callback to get notified when the operation is finished
     * android.content.pm.IPackageDataObserver#onRemoveCompleted(String, boolean)
     * will be called when that happens.  observer may be null to indicate that
     * no callback is desired.
     *
     */
    void clearApplicationUserData(String packageName, IPackageDataObserver observer, int userId)
            throws RemoteException;

    /**
     * Attempts to delete the cache files associated with an application.
     * Since this may take a little while, the result will
     * be posted back to the given observer.  A deletion will fail if the calling context
     * lacks the {@link android.Manifest.permission#DELETE_CACHE_FILES} permission, if the
     * named package cannot be found, or if the named package is a "system package".
     *
     * @param packageName The name of the package to delete
     * @param observer An observer callback to get notified when the cache file deletion
     * is complete.
     * {@link android.content.pm.IPackageDataObserver#onRemoveCompleted(String, boolean)}
     * will be called when that happens.  observer may be null to indicate that
     * no callback is desired.
     *
     */
    void deleteApplicationCacheFiles(String packageName, IPackageDataObserver observer)
            throws RemoteException;

    /**
     * Attempts to delete the cache files associated with an application for a given user. Since
     * this may take a little while, the result will be posted back to the given observer. A
     * deletion will fail if the calling context lacks the
     * {@link android.Manifest.permission#DELETE_CACHE_FILES} permission, if the named package
     * cannot be found, or if the named package is a "system package". If {@code userId} does not
     * belong to the calling user, the caller must have
     * android.Manifest.permission#INTERACT_ACROSS_USERS permission.
     *
     * @param packageName The name of the package to delete
     * @param userId the user for which the cache files needs to be deleted
     * @param observer An observer callback to get notified when the cache file deletion is
     *            complete.
     *            {@link android.content.pm.IPackageDataObserver#onRemoveCompleted(String, boolean)}
     *            will be called when that happens. observer may be null to indicate that no
     *            callback is desired.
     */
    void deleteApplicationCacheFilesAsUser(String packageName, int userId, IPackageDataObserver observer)
            throws RemoteException;


    abstract class Stub extends Binder implements IPackageManager {

        public static IPackageManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}