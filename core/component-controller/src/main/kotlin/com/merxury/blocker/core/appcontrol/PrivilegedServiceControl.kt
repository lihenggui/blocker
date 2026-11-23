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

package com.merxury.blocker.core.appcontrol

import android.content.ComponentName
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.privileged.CallResult
import com.merxury.blocker.core.privileged.ActivePrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedChannel
import com.merxury.blocker.core.privileged.PrivilegedExecutor
import com.merxury.blocker.core.privileged.calls.RunningServices
import com.merxury.blocker.core.privileged.calls.StartService
import com.merxury.blocker.core.privileged.calls.StopService
import timber.log.Timber
import javax.inject.Inject

/** [ServiceControl] on the privileged channel; each operation is one privileged call. */
internal class PrivilegedServiceControl(
    private val channel: PrivilegedChannel,
    private val executor: PrivilegedExecutor,
) : ServiceControl {

    @Inject constructor(channel: ActivePrivilegedChannel) : this(channel as PrivilegedChannel, channel as PrivilegedExecutor)

    override suspend fun runningServices(app: AppKey): CallResult<Set<ComponentId>> {
        channel.runningListRefusal(app.userId)?.let { return CallResult.Failure(it) }
        return executor.execute(RunningServices(app.packageName, app.userId)).map { services -> listed(app, services) }
    }

    private fun listed(app: AppKey, services: RunningServices.Services): Set<ComponentId> {
        if (services.capped) {
            Timber.w("The system listed ${RunningServices.MAX_SERVICES} services; some of ${app.packageName} may be missing")
        }
        return services.classNames.mapNotNull { ComponentId.unflatten("${app.packageName}/$it") }.toSet()
    }

    override suspend fun start(service: ComponentKey): CallResult<Unit> = executor.execute(StartService(service.id.toComponentName(), service.userId))

    override suspend fun stop(service: ComponentKey): CallResult<Unit> = executor.execute(StopService(service.id.toComponentName(), service.userId))

    private fun ComponentId.toComponentName() = ComponentName(packageName, className)
}
