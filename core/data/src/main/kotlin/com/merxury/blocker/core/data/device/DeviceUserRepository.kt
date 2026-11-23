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

import com.merxury.blocker.core.database.cache.ComponentCacheDao
import com.merxury.blocker.core.database.cache.asEntity
import com.merxury.blocker.core.database.cache.asExternalModel
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.system.SystemPackageReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject

/**
 * The users on the device, as last read from the system into the component cache. Without root or
 * Shizuku the system shows only the user Blocker runs in, so that is all a refresh can store.
 */
interface DeviceUserRepository {
    /** The cached users, by id. Emits again whenever they change. */
    val users: Flow<List<DeviceUser>>

    /**
     * Reads the users from the system and makes the cache equal to them: users that are gone are
     * removed with their apps and component state. A failed read leaves the cache as it is. Never
     * connects the privileged channel and never throws except for cancellation.
     *
     * @return the users read
     */
    suspend fun refresh(): ReadResult<List<DeviceUser>>
}

internal class LocalDeviceUserRepository @Inject constructor(
    private val dao: ComponentCacheDao,
    private val reader: SystemPackageReader,
) : DeviceUserRepository {

    override val users: Flow<List<DeviceUser>> = dao.observeUsers().map { users -> users.map { it.asExternalModel() } }

    override suspend fun refresh(): ReadResult<List<DeviceUser>> {
        val read = reader.users()
        when (read) {
            is ReadResult.Success -> dao.replaceUsers(read.value.map { it.asEntity() })
            is ReadResult.Failure -> Timber.w("Cannot read the users, keep the cached ones: ${read.reason}")
        }
        return read
    }
}
