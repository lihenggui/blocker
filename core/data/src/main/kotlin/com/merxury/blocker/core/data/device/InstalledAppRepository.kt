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

package com.merxury.blocker.core.data.device

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.system.AppEnabledSetting
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.Flow

/**
 * The apps installed for each user, as last read from the system into the component cache.
 *
 * Reads never connect the privileged channel, so they never show a prompt; another user's apps
 * can be read only while the channel is ready. No method throws except for cancellation.
 */
interface InstalledAppRepository {
    /** The cached apps of [user], by package name. Emits again whenever they change. */
    fun apps(user: UserId): Flow<List<InstalledApp>>

    /** The cached app, or null while it is not cached as installed for that user. */
    fun app(key: AppKey): Flow<InstalledApp?>

    /**
     * The cached apps of [user] whose label or package name contains [keyword], ignoring ASCII
     * case. `%` and `_` match themselves.
     */
    fun search(user: UserId, keyword: String): Flow<List<InstalledApp>>

    /**
     * Reads the apps of [user] from the system and makes that user's cached apps equal to them;
     * an app that is gone is removed with that user's component state for it and with the record
     * of what Blocker disabled in it for that user. The users are
     * refreshed first when [user] is not cached, and the result is
     * [ReadFailure.UserNotReachable] when the system does not show it. A failed read leaves the
     * cache as it is.
     */
    suspend fun refresh(user: UserId): ReadResult<Unit>

    /**
     * Reads one app from the system and stores it. When the system says it is not installed for
     * that user, it is removed from the cache with that user's component state, and with the
     * record of what Blocker disabled in it for that user, and the result is success with null. Any other failure leaves the cache as it is.
     */
    suspend fun refreshApp(key: AppKey): ReadResult<InstalledApp?>

    /**
     * Enables or disables the app for its user, as AppControl does, which may show the su or
     * Shizuku prompt. Afterwards the cached app is read again from the system, whatever the call
     * answered, since a failed call may still have changed it.
     *
     * @return the call's result
     */
    suspend fun setEnabled(key: AppKey, enabled: Boolean): CallResult<AppEnabledSetting>

    /**
     * Uninstalls the app for its user only, as AppControl does, which may show the su or Shizuku
     * prompt. Afterwards the cached app is read again from the system, so an app that is gone is
     * removed from the cache.
     *
     * @return the call's result
     */
    suspend fun uninstall(key: AppKey): CallResult<Unit>
}
