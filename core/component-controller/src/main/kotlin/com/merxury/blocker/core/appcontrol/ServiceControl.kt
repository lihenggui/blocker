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

import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentKey
import com.merxury.blocker.core.model.privileged.CallFailure
import com.merxury.blocker.core.model.privileged.CallResult

/**
 * Starts, stops and lists the services of apps through the privileged channel, each for the user
 * in its key. Connects the channel when needed, which may show a prompt, and never throws except
 * for cancellation. A package that is not installed for that user, or a service it does not
 * declare, is [CallFailure.TargetNotFound].
 */
interface ServiceControl {
    /**
     * Running services of [app] for its user: those started and not yet stopped. A service that
     * is only bound by a client is left out, since [stop] cannot stop it. The shell and the system
     * uid may list any user; root only user 0, and [CallFailure.NotPermitted] for any other,
     * because the system answers uid 0 with user 0 alone. Starting and stopping reach every user
     * for all of them.
     */
    suspend fun runningServices(app: AppKey): CallResult<Set<ComponentId>>

    /**
     * Starts [service]. [CallFailure.NotPermitted], with the system's reason, when the service is
     * not exported to the identity the channel runs as or needs a permission it lacks, which is
     * common for the shell, or when the system does not let the app start services while it is
     * in the background. [CallFailure.SystemError] when the system does not start it otherwise,
     * for example because the service is disabled.
     */
    suspend fun start(service: ComponentKey): CallResult<Unit>

    /**
     * Stops [service]. A service that is not running succeeds. A service still bound by a client
     * keeps running until it is unbound. [CallFailure.NotPermitted] when the service is not
     * exported to the identity or needs a permission it lacks.
     */
    suspend fun stop(service: ComponentKey): CallResult<Unit>
}
