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

package com.merxury.blocker.core.testing.repository.device

import com.merxury.blocker.core.data.device.DeviceUserRepository
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

/** In-memory [DeviceUserRepository]; tests set the cached users and what a refresh answers. */
class FakeDeviceUserRepository @Inject constructor() : DeviceUserRepository {
    private val cached = MutableStateFlow<List<DeviceUser>>(emptyList())

    /** What the system shows; a successful [refresh] stores these. */
    var systemUsers: List<DeviceUser> = emptyList()

    /** When set, [refresh] fails with it and leaves the cache as it is. */
    var refreshFailure: ReadFailure? = null

    var refreshCount = 0
        private set

    override val users: Flow<List<DeviceUser>> = cached

    override suspend fun refresh(): ReadResult<List<DeviceUser>> {
        refreshCount++
        refreshFailure?.let { return ReadResult.Failure(it) }
        cached.value = systemUsers.sortedBy { it.id.value }
        return ReadResult.Success(cached.value)
    }

    /** Sets the cached users as a refresh would. */
    fun sendUsers(users: List<DeviceUser>) {
        cached.value = users.sortedBy { it.id.value }
    }
}
