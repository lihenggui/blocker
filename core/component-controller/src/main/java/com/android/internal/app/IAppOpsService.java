// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.android.internal.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/** Compile-time stand-in for the framework's hidden app-ops binder interface. */
public interface IAppOpsService extends IInterface {

    int checkOperation(int code, int uid, String packageName) throws RemoteException;

    void setMode(int code, int uid, String packageName, int mode) throws RemoteException;

    abstract class Stub extends Binder implements IAppOpsService {
        public static IAppOpsService asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
