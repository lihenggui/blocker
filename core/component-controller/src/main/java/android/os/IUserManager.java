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

package android.os;

import android.content.pm.UserInfo;
import androidx.annotation.RequiresApi;

import java.util.List;

/**
 * Compile-time stand-in for the hidden binder interface of the user service; the framework's own
 * interface is loaded at run time. Only the methods Blocker calls are declared.
 */
public interface IUserManager extends IInterface {

    /** Up to Android 10. */
    List<UserInfo> getUsers(boolean excludeDying) throws RemoteException;

    @RequiresApi(Build.VERSION_CODES.R)
    List<UserInfo> getUsers(boolean excludePartial, boolean excludeDying, boolean excludePreCreated)
            throws RemoteException;

    /** The full user a profile belongs to, or null when the user is not a profile. */
    UserInfo getProfileParent(int userId) throws RemoteException;

    /** Null when the user does not exist. */
    UserInfo getUserInfo(int userId) throws RemoteException;

    abstract class Stub extends Binder implements IUserManager {

        public static IUserManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
