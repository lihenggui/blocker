/*
 * Copyright 2024 Blocker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.pm;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import androidx.annotation.NonNull;

/**
 * API for package data change related callbacks from the Package Manager.
 * Some usage scenarios include deletion of cache directory, generate
 * statistics related to code, data, cache usage(TODO)
 */
public interface IPackageStatsObserver extends IInterface {
    /** Local-side IPC implementation stub class. */
    abstract class Stub extends Binder implements IPackageStatsObserver {
        /** Construct the stub at attach it to the interface. */
        public Stub() {
            throw new UnsupportedOperationException("Stub!");
        }

        /**
         * Cast an IBinder object into an android.content.pm.IPackageStatsObserver interface,
         * generating a proxy if needed.
         */
        public static IPackageStatsObserver asInterface(IBinder obj) {
            throw new UnsupportedOperationException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new UnsupportedOperationException("Stub!");
        }

        @Override
        public boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags)
                throws RemoteException {
            throw new UnsupportedOperationException("Stub!");
        }

        private static class Proxy implements IPackageStatsObserver {

            Proxy(IBinder remote) {
                throw new UnsupportedOperationException("Stub!");
            }

            @Override
            public IBinder asBinder() {
                throw new UnsupportedOperationException("Stub!");
            }

            public String getInterfaceDescriptor() {
                throw new UnsupportedOperationException("Stub!");
            }

            @Override
            public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) {
                throw new UnsupportedOperationException("Stub!");
            }
        }
    }

    void onGetStatsCompleted(PackageStats pStats, boolean succeeded);
}
