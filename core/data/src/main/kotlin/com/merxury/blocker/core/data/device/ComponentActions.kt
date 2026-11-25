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

import com.merxury.blocker.core.appcontrol.AppControl
import com.merxury.blocker.core.appcontrol.ServiceControl
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.privileged.CallResult
import javax.inject.Inject

/** Privileged activity and service operations for the user in each key. */
interface ComponentActions {
    suspend fun startActivity(activity: ComponentKey): CallResult<Unit>

    suspend fun stopService(service: ComponentKey): CallResult<Unit>

    suspend fun runningServices(app: AppKey): CallResult<Set<ComponentId>>
}

class LocalComponentActions @Inject constructor(
    private val apps: AppControl,
    private val services: ServiceControl,
) : ComponentActions {
    override suspend fun startActivity(activity: ComponentKey): CallResult<Unit> = apps.startActivity(activity)

    override suspend fun stopService(service: ComponentKey): CallResult<Unit> = services.stop(service)

    override suspend fun runningServices(app: AppKey): CallResult<Set<ComponentId>> = services.runningServices(app)
}
