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

package com.merxury.blocker.core.privileged.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.DeadObjectException
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.awaitPipeWriters

internal const val BLOCKER_UID = 10_123

/**
 * Shizuku starting [ShizukuPrivilegedService] in this process: each bind creates a new service
 * that runs calls against [system] for callers that look like [callerUid].
 */
internal class FakeUserServiceConnector(private val system: SystemServices) : UserServiceConnector {
    var version = 13

    /** The uid the next service sees each call come from. */
    var callerUid = BLOCKER_UID

    /** Whether a bind delivers the binder; false leaves the service starting forever. */
    var attaches = true

    var binds = 0
        private set
    val unbinds = mutableListOf<Boolean>()
    val connections = mutableListOf<ServiceConnection>()
    val services = mutableListOf<FakeService>()

    override fun serverVersion(): Int = version

    override fun bind(connection: ServiceConnection) {
        binds++
        connections += connection
        if (!attaches) return
        val service = FakeService(PrivilegedCallRunner(BLOCKER_UID, { callerUid }, system))
        services += service
        connection.onServiceConnected(COMPONENT, service)
    }

    override fun unbind(connection: ServiceConnection, stop: Boolean) {
        unbinds += stop
    }

    internal class FakeService(private val runner: PrivilegedCallRunner) : IPrivilegedService.Stub() {
        /** Makes the next call fail as a service process that died. */
        var dead = false
        var calls = 0
            private set

        override fun run(call: CallBytes): CallBytes {
            calls++
            if (dead) throw DeadObjectException()
            check(awaitPipeWriters())
            return runner.run(call)
        }

        override fun destroy() = Unit
    }

    private companion object {
        val COMPONENT = ComponentName("com.merxury.blocker", "ShizukuPrivilegedService")
    }
}
