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
import com.merxury.blocker.core.data.device.PmBlockRecordSeeder
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
 * Clears the PackageManager settings with which Blocker disabled components for one user, one
 * package at a time, back to DEFAULT, so each component follows its manifest again. Only the
 * components Blocker recorded as disabled by itself and that the system still shows DISABLED when
 * the reset runs are cleared, see [ComponentStatusRepository.disabledByBlocker]; components the app
 * or the system disabled are left alone, and Intent Firewall rules are not touched. Each cleared
 * component loses its record.
 *
 * Every count is of components: cleared ones are applied; recorded ones of apps no longer installed
 * are skipped as [SkipReason.APP_NOT_INSTALLED], and those the app no longer declares as
 * [SkipReason.NOT_DECLARED]; a component the system refuses fails alone. An app whose settings cannot
 * be read counts once as [FailureReason.STATE_UNREADABLE] and keeps its records; the others are
 * still cleared.
 */
class PackageManagerReset @Inject constructor(
    private val components: ComponentStatusRepository,
    private val channel: PrivilegedChannel,
    private val seeder: PmBlockRecordSeeder,
) {
    /**
     * Unblocks the PackageManager layer of each component Blocker disabled for [user]. Connects
     * the privileged channel first, which may show the su or Shizuku prompt.
     */
    suspend fun reset(user: UserId, onProgress: RuleRunProgress = { _, _, _ -> }): RuleRunResult {
        if (!channel.connectAllowing()) return RuleRunResult.Failed(FailureReason.NO_PRIVILEGE)
        if (!seeder.seedOnce()) return RuleRunResult.Failed(FailureReason.STATE_UNREADABLE)
        val found = when (val read = components.disabledByBlocker(user)) {
            is ReadResult.Success -> read.value
            is ReadResult.Failure -> return RuleRunResult.Failed(read.reason.reason)
        }
        val summary = SummaryBuilder()
        summary.fail(FailureReason.STATE_UNREADABLE, found.unreadableApps.size)
        summary.skip(SkipReason.APP_NOT_INSTALLED, found.notInstalled)
        summary.skip(SkipReason.NOT_DECLARED, found.notDeclared)
        val byPackage = found.components.groupBy { it.id.packageName }
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
