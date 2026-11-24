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

package com.merxury.blocker.feature.applist.impl

import com.merxury.blocker.core.appcontrol.ServiceControl
import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.model.ComponentType
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.Availability
import com.merxury.blocker.core.model.component.IfwBlock
import com.merxury.blocker.core.model.component.availability
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.ui.applist.ServiceCounts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject

/** Counts the services of one app for its user, for the service line of the app list. */
internal class ServiceCounter @Inject constructor(
    private val components: ComponentStatusRepository,
    private val serviceControl: ServiceControl,
    private val channel: PrivilegedChannel,
) {
    // A few apps at a time, so a screen of rows does not queue a burst of system calls.
    private val permits = Semaphore(MAX_CONCURRENT)

    /**
     * Refreshes [app]'s components, then counts its services that run, that are blocked and in
     * total. While the Intent Firewall rules cannot be reached at all, such as with Shizuku
     * running as the shell, blocked counts what the PackageManager settings and the manifest
     * block, which is all that can be known then. Null when any part cannot be read otherwise:
     * without the privileged channel, which running services need, when the refresh fails, or when
     * a service's state is unknown for another reason, such as a damaged rule file.
     */
    suspend fun count(app: AppKey): ServiceCounts? {
        // Listing running services connects the channel, which may prompt; a list never asks.
        if (channel.state.value !is ChannelState.Ready) return null
        return permits.withPermit {
            val refreshed = components.refresh(app)
            if (refreshed is ReadResult.Failure) {
                Timber.d("No service counts for $app: ${refreshed.reason}")
                return@withPermit null
            }
            val running = when (val result = serviceControl.runningServices(app)) {
                is CallResult.Success -> result.value

                is CallResult.Failure -> {
                    Timber.d("No running services of $app: ${result.reason}")
                    return@withPermit null
                }
            }
            val firewallReadable = components.firewallReadable.first()
            val services = components.components(app).first().filter { it.declaration.type == ComponentType.SERVICE }
            val availability = services.map { service ->
                if (firewallReadable) {
                    service.availability
                } else {
                    // The rules are unknown by design here, so judge by the other layer alone.
                    service.declaration.availability(service.state.copy(ifw = IfwBlock.NOT_BLOCKED))
                }
            }
            if (availability.any { it == Availability.Unknown }) return@withPermit null
            ServiceCounts(
                running = services.count { it.declaration.id in running },
                blocked = availability.count { it is Availability.Blocked },
                total = services.size,
            )
        }
    }

    private companion object {
        const val MAX_CONCURRENT = 3
    }
}
