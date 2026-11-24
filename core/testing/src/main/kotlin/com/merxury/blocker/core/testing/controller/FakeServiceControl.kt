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

package com.merxury.blocker.core.testing.controller

import com.merxury.blocker.core.appcontrol.ServiceControl
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult

/**
 * [ServiceControl] over [running]. [start] and [stop] change it, [stop] unless [stopFailure] is
 * set; [runningServices] answers from it unless [runningFailure] is set. [runningRequests] counts
 * the apps asked about and [stopped] the services asked to stop.
 */
class FakeServiceControl : ServiceControl {
    /** Running services, per app. */
    val running = mutableMapOf<AppKey, MutableSet<ComponentId>>()

    /** When set, [runningServices] fails with it. */
    var runningFailure: CallFailure? = null

    /** The apps [runningServices] was asked about, in order. */
    val runningRequests = mutableListOf<AppKey>()

    /** When set, [stop] fails with it and changes nothing. */
    var stopFailure: CallFailure? = null

    /** The services [stop] was asked to stop, in order. */
    val stopped = mutableListOf<ComponentKey>()

    override suspend fun runningServices(app: AppKey): CallResult<Set<ComponentId>> {
        runningRequests += app
        runningFailure?.let { return CallResult.Failure(it) }
        return CallResult.Success(running[app].orEmpty().toSet())
    }

    override suspend fun start(service: ComponentKey): CallResult<Unit> {
        running.getOrPut(AppKey(service.id.packageName, service.userId)) { mutableSetOf() } += service.id
        return CallResult.Success(Unit)
    }

    override suspend fun stop(service: ComponentKey): CallResult<Unit> {
        stopped += service
        stopFailure?.let { return CallResult.Failure(it) }
        running[AppKey(service.id.packageName, service.userId)]?.remove(service.id)
        return CallResult.Success(Unit)
    }
}
