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

package com.merxury.blocker.core.privileged.root

import be.mygod.librootkotlinx.RootCommand
import com.merxury.blocker.core.privileged.CallBytes
import com.merxury.blocker.core.privileged.PrivilegedProcessSystemServices
import com.merxury.blocker.core.privileged.encodeOutcome
import com.merxury.blocker.core.privileged.unmarshallCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

/**
 * Runs the call in [request], from [com.merxury.blocker.core.privileged.marshallCall], in the
 * root process and sends back its outcome from [encodeOutcome]. A call that throws still
 * completes this command; only a broken root process fails it.
 */
@Parcelize
internal class RootCallCommand(val request: CallBytes) : RootCommand<CallBytes> {
    override suspend fun execute(): CallBytes = withContext(Dispatchers.IO) {
        val call = unmarshallCall(request.read(CallBytes.MAX_REQUEST_BYTES))
        CallBytes.reply(encodeOutcome(call, PrivilegedProcessSystemServices))
    }
}
