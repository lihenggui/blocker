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

package com.merxury.blocker.core.system

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentDeclaration
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.PmSetting
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadResult

/**
 * Reads what the system knows about users, the apps installed for them, and the components of
 * those apps. It reads the live system each time and keeps nothing.
 *
 * Every read can fail, and a failure is never reported as an empty answer.
 */
interface SystemPackageReader {
    /** The users whose apps can be read from here. Without root or Shizuku that is one user. */
    suspend fun users(): ReadResult<List<DeviceUser>>

    /** Every app installed for [user], including disabled apps and Blocker itself. */
    suspend fun apps(user: UserId): ReadResult<List<InstalledApp>>

    suspend fun app(key: AppKey): ReadResult<InstalledApp>

    /**
     * The components [app] declares in its manifest, including those it ships disabled. The answer
     * is the same for every user that has the app; the user only says where to look it up.
     */
    suspend fun declarations(app: AppKey): ReadResult<List<ComponentDeclaration>>

    /**
     * The PackageManager setting of each of [components] for the user of [app]. A component whose
     * setting cannot be read is [PmSetting.UNKNOWN]; the result has an entry for every component.
     *
     * Pass components from [declarations]. For a name the app does not declare, recent Android
     * answers [PmSetting.DEFAULT] and older releases refuse, which gives [PmSetting.UNKNOWN]. A
     * component of another package is [PmSetting.UNKNOWN].
     */
    suspend fun pmSettings(
        app: AppKey,
        components: Collection<ComponentId>,
    ): ReadResult<Map<ComponentId, PmSetting>>
}
