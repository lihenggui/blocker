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

package com.merxury.blocker.core.rule.backup

import com.merxury.blocker.core.data.device.ComponentStatusRepository
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Clears every PackageManager setting that disables a component for one user, as read from the
 * system when the reset runs, one package at a time. Intent Firewall rules are not touched. A
 * package or component the system refuses fails alone; the others are still cleared.
 */
class PackageManagerReset @Inject constructor(
    private val components: ComponentStatusRepository,
    private val channel: PrivilegedChannel,
) {
    /**
     * Unblocks the PackageManager layer of each component disabled for [user]. Connects the
     * privileged channel first, which may show the su or Shizuku prompt.
     */
    suspend fun reset(user: UserId, onProgress: RuleRunProgress = { _, _, _ -> }): RuleRunResult {
        channel.connect()
        val disabled = when (val read = components.disabledByPackageManager(user)) {
            is ReadResult.Success -> read.value
            is ReadResult.Failure -> return RuleRunResult.Failed(read.reason.reason)
        }
        val summary = SummaryBuilder()
        val byPackage = disabled.groupBy { it.id.packageName }
        byPackage.entries.forEachIndexed { index, (packageName, keys) ->
            onProgress(packageName, index, byPackage.size)
            val statuses = components.components(AppKey(packageName, user)).first().associateBy { it.declaration.id }
            val declarations = keys.mapNotNull { statuses[it.id]?.declaration }
            summary.skip(SkipReason.NOT_DECLARED, keys.size - declarations.size)
            if (declarations.isEmpty()) return@forEachIndexed
            val request = ControlRequest(user, declarations, ControlGoal.UNBLOCK, setOf(ControlLayer.PACKAGE_MANAGER))
            val states = statuses.mapValues { it.value.state }.toMutableMap()
            if (components.controlAndCount(request, states, summary) > 0) summary.apps++
        }
        return RuleRunResult.Completed(summary.build())
    }
}
