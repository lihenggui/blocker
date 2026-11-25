// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package android.os;

/** Compile-time stand-in for the framework's hidden device idle binder interface. */
public interface IDeviceIdleController extends IInterface {

    String[] getSystemPowerWhitelist() throws RemoteException;

    String[] getUserPowerWhitelist() throws RemoteException;

    void removePowerSaveWhitelistApp(String packageName) throws RemoteException;

    abstract class Stub extends Binder implements IDeviceIdleController {
        public static IDeviceIdleController asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
