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
 * In-memory [SelectedUserRepository]. Use [sendUsers] to change the cached users and
 * [setAccessAvailable] to simulate temporarily losing or regaining the privileged channel.
 */
@Singleton
class FakeSelectedUserRepository(
    override val ownUser: UserId,
) : SelectedUserRepository {

    @Inject constructor() : this(UserId.SYSTEM)

    private val ownFallback = ownDeviceUser(ownUser)
    private var cached = listOf(ownFallback)
    private var accessAvailable = true
    private val reachable = MutableStateFlow(cached)
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

    /** Replaces the cached users as a refresh would. */
    fun sendUsers(users: List<DeviceUser>) {
        val own = users.find { it.id == ownUser } ?: ownFallback
        cached = (users.filter { it.id != ownUser } + own).sortedBy { it.id.value }
        updateSelection()
    }

    fun setAccessAvailable(available: Boolean) {
        accessAvailable = available
        updateSelection()
    }

    private fun updateSelection() {
        if (accessAvailable && cached.none { it.id == choice }) choice = ownUser
        reachable.value = if (accessAvailable) cached else listOf(cached.first { it.id == ownUser })
        selected.value = choice.takeIf { id -> reachable.value.any { it.id == id } } ?: ownUser
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
