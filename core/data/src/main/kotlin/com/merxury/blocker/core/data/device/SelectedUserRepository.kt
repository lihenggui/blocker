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

import com.merxury.blocker.core.di.ApplicationScope
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.UnavailableReason
import com.merxury.blocker.core.model.system.DeviceUser
import com.merxury.blocker.core.model.system.UserKind
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user whose apps the screens show, one at a time, shared by every screen that shows apps of
 * one user. Kept in memory only: each start of the app begins with [ownUser].
 */
interface SelectedUserRepository {
    /** The user Blocker runs in. */
    val ownUser: UserId

    /**
     * The users whose apps can be shown now, by id. While the privileged channel is ready these
     * are the cached users, and also before its first check at start ends, so a channel granted
     * before does not show a single user first. Once the channel is unavailable, only [ownUser],
     * because no other user can be read and cached data of other users may be out of date. Never empty: [ownUser] is listed even before
     * the users are first read.
     */
    val users: StateFlow<List<DeviceUser>>

    /** The chosen user when listed in [users], otherwise [ownUser]. */
    val selectedUser: StateFlow<UserId>

    /** Selects [user]; a user not in [users] is ignored. */
    fun select(user: UserId)
}

@Singleton
internal class InMemorySelectedUserRepository(
    override val ownUser: UserId,
    deviceUsers: DeviceUserRepository,
    channel: PrivilegedChannel,
    scope: CoroutineScope,
) : SelectedUserRepository {

    @Inject constructor(
        deviceUsers: DeviceUserRepository,
        channel: PrivilegedChannel,
        @ApplicationScope scope: CoroutineScope,
    ) : this(ownUser(), deviceUsers, channel, scope)

    private val ownFallback = DeviceUser(
        id = ownUser,
        name = null,
        kind = if (ownUser == UserId.SYSTEM) UserKind.OWNER else UserKind.SECONDARY,
        parent = null,
        running = true,
    )

    private val choice = MutableStateFlow(ownUser)

    override val users: StateFlow<List<DeviceUser>> = combine(deviceUsers.users, channel.state) { cached, state ->
        if (state is ChannelState.Ready) {
            val chosen = choice.value
            if (cached.none { it.id == chosen }) choice.compareAndSet(chosen, ownUser)
        }
        val own = cached.find { it.id == ownUser } ?: ownFallback
        if (state is ChannelState.Ready || state == NOT_CHECKED) {
            (cached.filter { it.id != ownUser } + own).sortedBy { it.id.value }
        } else {
            listOf(own)
        }
    }.stateIn(scope, SharingStarted.Eagerly, listOf(ownFallback))

    override val selectedUser: StateFlow<UserId> = combine(users, choice) { reachable, chosen ->
        if (reachable.any { it.id == chosen }) chosen else ownUser
    }.stateIn(scope, SharingStarted.Eagerly, ownUser)

    override fun select(user: UserId) {
        if (users.value.none { it.id == user }) {
            Timber.w("Cannot select user ${user.value}: not reachable")
            return
        }
        choice.value = user
    }

    private companion object {
        val NOT_CHECKED = ChannelState.Unavailable(UnavailableReason.NOT_CHECKED)
    }
}
