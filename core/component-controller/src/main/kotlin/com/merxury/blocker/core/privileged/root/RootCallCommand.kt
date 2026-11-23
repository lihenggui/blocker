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
import com.merxury.blocker.core.privileged.CallReply
import com.merxury.blocker.core.privileged.PrivilegedCall
import com.merxury.blocker.core.privileged.PrivilegedProcessSystemServices
import com.merxury.blocker.core.privileged.encodeOutcome
import kotlinx.parcelize.Parcelize

/**
 * Runs [call] in the root process and sends back its outcome from [encodeOutcome]. A call that
 * throws still completes this command; only a broken root process fails it.
 */
@Parcelize
internal class RootCallCommand(val call: PrivilegedCall<*>) : RootCommand<CallReply> {
    override suspend fun execute(): CallReply = CallReply.of(encodeOutcome(call, PrivilegedProcessSystemServices))
}
