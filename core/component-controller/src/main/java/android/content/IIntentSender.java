/*
 * Copyright 2026 Blocker
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

package android.content;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * The binder behind an {@link IntentSender}. The system calls {@code send} one way; which overload
 * depends on the release, so an implementation overrides both.
 */
public interface IIntentSender extends IInterface {
    /** Android 7. */
    void send(int code, Intent intent, String resolvedType, IIntentReceiver finishedReceiver,
            String requiredPermission, Bundle options) throws RemoteException;

    /** Android 8 and later. */
    void send(int code, Intent intent, String resolvedType, IBinder whitelistToken,
            IIntentReceiver finishedReceiver, String requiredPermission, Bundle options)
            throws RemoteException;

    abstract class Stub extends Binder implements IIntentSender {
        public Stub() {
            throw new UnsupportedOperationException("Stub!");
        }

        public static IIntentSender asInterface(IBinder obj) {
            throw new UnsupportedOperationException("Stub!");
        }

        @Override
        public IBinder asBinder() {
            throw new UnsupportedOperationException("Stub!");
        }
    }
}
