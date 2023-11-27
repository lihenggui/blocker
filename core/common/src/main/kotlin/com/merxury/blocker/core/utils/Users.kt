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

package com.merxury.blocker.core.utils

import android.os.Build
import android.os.UserHandle
import org.lsposed.hiddenapibypass.HiddenApiBypass
import timber.log.Timber

object Users {
    /**
     * Get current active user id in the Android system
     *
     * @return current user id
     */
    fun getCurrentUserId(): Int {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // TODO find a better way to get current user id on Android 9.0 and below
            0
        } else {
            val userId = HiddenApiBypass.getDeclaredMethod(
                UserHandle::class.java,
                "myUserId",
            )
                .invoke(null)
            if (userId !is Int) {
                Timber.e("Received invalid userId")
                return 0
            }
            return userId
        }
    }
}
