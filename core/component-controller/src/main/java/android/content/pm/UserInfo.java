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

package android.content.pm;

import androidx.annotation.RequiresApi;

/**
 * Compile-time stand-in for the hidden framework class; the framework's own class is loaded at
 * run time. Only the members Blocker reads are declared.
 */
public class UserInfo {
    public int id;
    public String name;
    public int flags;

    /** The id of the full user a profile belongs to; the user's own id or -10000 for a full user. */
    public int profileGroupId;

    public UserInfo(int id, String name, int flags) {
        throw new UnsupportedOperationException();
    }

    public boolean isManagedProfile() {
        throw new UnsupportedOperationException();
    }

    /** Any kind of profile, such as a managed, clone or private profile. */
    @RequiresApi(30)
    public boolean isProfile() {
        throw new UnsupportedOperationException();
    }
}
