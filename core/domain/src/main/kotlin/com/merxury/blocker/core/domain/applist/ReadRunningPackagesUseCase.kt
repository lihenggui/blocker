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

package com.merxury.blocker.core.domain.applist

import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.privileged.PrivilegedChannel
import timber.log.Timber
import javax.inject.Inject

/** Reads which apps of a user run, for the running badge of a list of apps. */
class ReadRunningPackagesUseCase @Inject constructor(
    private val appControl: AppControl,
    private val channel: PrivilegedChannel,
) {
    /**
     * The package names running in [user], or null when that cannot be read. Reading connects the
     * channel, which may prompt, and a list never asks: without a ready channel this reads nothing.
     */
    suspend operator fun invoke(user: UserId): Set<String>? {
        if (channel.state.value !is ChannelState.Ready) return null
        return when (val result = appControl.runningPackages(user)) {
            is CallResult.Success -> result.value

            is CallResult.Failure -> {
                Timber.d("No running apps of user ${user.value}: ${result.reason}")
                null
            }
        }
    }
}
