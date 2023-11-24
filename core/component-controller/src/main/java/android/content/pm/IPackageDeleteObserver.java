/*
 * Copyright 2023 Blocker
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

public interface IPackageDeleteObserver extends IInterface {
    /** Local-side IPC implementation stub class. */
    abstract class Stub extends Binder implements IPackageDeleteObserver {
        private static final java.lang.String DESCRIPTOR =
                "android.content.pm.IPackageDeleteObserver";

        /** Construct the stub at attach it to the interface. */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an android.content.pm.IPackageDeleteObserver interface,
         * generating a proxy if needed.
         */
        public static IPackageDeleteObserver asInterface(IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin instanceof IPackageDeleteObserver))) {
                return ((IPackageDeleteObserver) iin);
            }
            return new IPackageDeleteObserver.Stub.Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, @NonNull Parcel data, Parcel reply, int flags)
                throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_packageDeleted: {
                    data.enforceInterface(DESCRIPTOR);
                    java.lang.String _arg0;
                    _arg0 = data.readString();
                    int _arg1;
                    _arg1 = data.readInt();
                    this.packageDeleted(_arg0, _arg1);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IPackageDeleteObserver {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            public java.lang.String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            @Override
            public void packageDeleted(java.lang.String packageName, int returnCode)
                    throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(packageName);
                    _data.writeInt(returnCode);
                    mRemote.transact(Stub.TRANSACTION_packageDeleted, _data, null,
                            IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }

        static final int TRANSACTION_packageDeleted = IBinder.FIRST_CALL_TRANSACTION;
    }

    void packageDeleted(java.lang.String packageName, int returnCode)
            throws RemoteException;
}
