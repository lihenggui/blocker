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

import com.merxury.blocker.core.data.device.SelectedUserRepository
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [SelectedUserRepository]. Tests set the reachable users with [sendUsers]; as in the
 * real one, the selection falls back to [ownUser] when the selected user is no longer reachable.
 */
@Singleton
class FakeSelectedUserRepository(
    override val ownUser: UserId,
) : SelectedUserRepository {

    @Inject constructor() : this(UserId.SYSTEM)

    private val reachable = MutableStateFlow(listOf(ownDeviceUser(ownUser)))
    override val users: StateFlow<List<DeviceUser>> = reachable

    private var choice = ownUser
    private val selected = MutableStateFlow(ownUser)
    override val selectedUser: StateFlow<UserId> = selected

    override fun select(user: UserId) {
        if (reachable.value.any { it.id == user }) {
            choice = user
            selected.value = user
        }
    }

    /** Sets the reachable users, as a refresh or a change of the privileged channel would. */
    fun sendUsers(users: List<DeviceUser>) {
        reachable.value = users.sortedBy { it.id.value }
        selected.value = choice.takeIf { id -> users.any { it.id == id } } ?: ownUser
    }

    private companion object {
        fun ownDeviceUser(id: UserId) = DeviceUser(
            id = id,
            name = null,
            kind = if (id == UserId.SYSTEM) UserKind.OWNER else UserKind.SECONDARY,
            parent = null,
            running = true,
        )
    }
}
