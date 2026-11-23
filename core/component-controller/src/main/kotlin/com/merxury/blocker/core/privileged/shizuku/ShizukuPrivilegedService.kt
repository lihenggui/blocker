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

import android.content.Context
import android.os.Binder
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.PrivilegedProcessSystemServices
import com.merxury.blocker.core.privileged.SystemServices
import com.merxury.blocker.core.privileged.encodeOutcome
import com.merxury.blocker.core.privileged.unmarshallCall
import kotlin.system.exitProcess

/**
 * Blocker's Shizuku user service. Shizuku loads this class by name from Blocker's APK in a process
 * of its own that runs as Shizuku's uid, and hands its binder to Blocker. It runs the
 * [PrivilegedCall]s that [PrivilegedCall.needsPrivilegedProcess] for Blocker's uid and nobody else.
 *
 * Shizuku 13 and later pass a [Context] of Blocker's package to the constructor; that is how the
 * service knows which uid to serve. Without one it refuses every call. Shizuku looks both
 * constructors up by reflection, so they must stay and be kept by R8.
 */
internal class ShizukuPrivilegedService @JvmOverloads constructor(context: Context? = null) : IPrivilegedService.Stub() {

    private val runner = PrivilegedCallRunner(
        allowedUid = context?.applicationInfo?.uid,
        callingUid = Binder::getCallingUid,
        system = PrivilegedProcessSystemServices,
    )

    override fun run(call: CallBytes): CallBytes = runner.run(call)

    override fun destroy() {
        exitProcess(0)
    }
}

/**
 * What [ShizukuPrivilegedService] does with one request, apart from the binder.
 *
 * @param allowedUid the only uid served; null serves nobody
 */
internal class PrivilegedCallRunner(
    private val allowedUid: Int?,
    private val callingUid: () -> Int,
    private val system: SystemServices,
) {
    /**
     * Checks the caller, then reads and runs the call. What the call throws is part of the
     * outcome; what this throws reaches the caller as a binder exception. Closes [request]
     * either way, so a refused caller's pipe is not left open.
     *
     * @throws SecurityException when the caller is not the allowed uid or the call is not one of
     * Blocker's calls that need this process
     * @throws IllegalStateException when the request cannot be read or the reply not built
     */
    fun run(request: CallBytes): CallBytes = request.use {
        val caller = callingUid()
        if (allowedUid == null) throw SecurityException("No app context from Shizuku, so no caller is served")
        if (caller != allowedUid) throw SecurityException("uid $caller may not use this service")
        try {
            val call = unmarshallCall(request.read(CallBytes.MAX_REQUEST_BYTES))
            if (!call.needsPrivilegedProcess) throw SecurityException("$call does not run in this service")
            CallBytes.reply(encodeOutcome(call, system))
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            // Binder only carries a few exception types back; this one maps to a system error.
            throw IllegalStateException(e.toString(), e)
        }
    }
}
