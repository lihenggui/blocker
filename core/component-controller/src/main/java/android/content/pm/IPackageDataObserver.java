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

import android.os.*;
import androidx.annotation.NonNull;

/**
 * API for package data change related callbacks from the Package Manager.
 * Some usage scenarios include deletion of cache directory, generate
 * statistics related to code, data, cache usage (WIP)
 */
public interface IPackageDataObserver extends IInterface {
    /** Local-side IPC implementation stub class. */
    abstract class Stub extends Binder implements IPackageDataObserver {
        private static final String DESCRIPTOR =
                "android.content.pm.IPackageDataObserver";

        /** Construct the stub at attach it to the interface. */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an android.content.pm.IPackageDataObserver interface,
         * generating a proxy if needed.
         */
        public static IPackageDataObserver asInterface(IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin instanceof IPackageDataObserver))) {
                return ((android.content.pm.IPackageDataObserver) iin);
            }
            return new android.content.pm.IPackageDataObserver.Stub.Proxy(obj);
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
                case TRANSACTION_onRemoveCompleted: {
                    data.enforceInterface(DESCRIPTOR);
                    String _arg0;
                    _arg0 = data.readString();
                    boolean _arg1;
                    _arg1 = (0 != data.readInt());
                    this.onRemoveCompleted(_arg0, _arg1);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements android.content.pm.IPackageDataObserver {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            public String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            @Override
            public void onRemoveCompleted(String packageName, boolean succeeded)
                    throws RemoteException {
                Parcel _data = Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeString(packageName);
                    _data.writeInt(((succeeded) ? (1) : (0)));
                    mRemote.transact(Stub.TRANSACTION_onRemoveCompleted, _data, null,
                            IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }

        static final int TRANSACTION_onRemoveCompleted = IBinder.FIRST_CALL_TRANSACTION;
    }

    void onRemoveCompleted(String packageName, boolean succeeded) throws RemoteException;
}
