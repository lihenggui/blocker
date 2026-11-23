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

package com.merxury.blocker.core.privileged

import kotlinx.parcelize.Parcelize

/** A call that needs the privileged process and only reads [SystemServices.callerPackage]. */
@Parcelize
internal data class EchoInPrivilegedProcess(val text: String) : PrivilegedCall<String> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): String = "$text for ${system.callerPackage}"
}

/** A call that needs the privileged process and fails there as [run] says. */
@Parcelize
internal data class FailInPrivilegedProcess(val message: String) : PrivilegedCall<String> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): String = throw SecurityException(message)
}

/** A call that needs the privileged process and answers how long [text] is, so its answer stays small. */
@Parcelize
internal data class MeasureInPrivilegedProcess(val text: String) : PrivilegedCall<Int> {
    override val needsPrivilegedProcess: Boolean get() = true

    override fun run(system: SystemServices): Int = text.length
}

/** A call that needs the privileged process only to run a tool the shell may run too. */
@Parcelize
internal data class ToolInPrivilegedProcess(val text: String) : PrivilegedCall<String> {
    override val needsPrivilegedProcess: Boolean get() = true

    override val needsSystemFiles: Boolean get() = false

    override fun run(system: SystemServices): String = "$text for ${system.callerPackage}"
}

/** A call that consumes what it reads, so it must not reach the system twice. */
@Parcelize
internal data class ConsumeOnce(val text: String) : PrivilegedCall<String> {
    override val retryable: Boolean get() = false

    override fun run(system: SystemServices): String = text
}
