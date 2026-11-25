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
import com.merxury.blocker.core.data.device.InstalledAppRepository
import com.merxury.blocker.core.data.repository.userdata.UserDataRepository
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ControlLayer
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlRequest
import com.merxury.blocker.core.model.control.blockLayers
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Blocks the components a MyAndroidTools (MAT) export lists, one `package/class` per line, for one
 * user, with the layers of the controller type the user has chosen when the import runs.
 *
 * Lines that are not `package/class` are skipped and counted, as are components of apps not
 * installed for the user and components the installed app does not declare.
 */
class MatRuleImporter @Inject constructor(
    private val installedApps: InstalledAppRepository,
    private val components: ComponentStatusRepository,
    private val channel: PrivilegedChannel,
    private val userData: UserDataRepository,
) {
    /**
     * Blocks the components [lines] name in [target]'s apps, third-party apps only unless
     * [includeSystemApps]. Connects the privileged channel first, which may show the su or
     * Shizuku prompt.
     */
    suspend fun import(
        lines: List<String>,
        target: UserId,
        includeSystemApps: Boolean = false,
        onProgress: RuleRunProgress = { _, _, _ -> },
    ): RuleRunResult {
        if (!channel.connectAllowing()) return RuleRunResult.Failed(FailureReason.NO_PRIVILEGE)
        val layers = userData.userData.first().controllerType.blockLayers
        val apps = when (val read = installedApps.installedApps(target)) {
            is ReadResult.Success -> read.value
            is ReadResult.Failure -> return RuleRunResult.Failed(read.reason.reason)
        }
        val parsed = parseMatLines(lines)
        val summary = SummaryBuilder()
        summary.skip(SkipReason.MALFORMED_LINE, parsed.malformed)
        val byPackage = parsed.components.groupBy { it.packageName }
        byPackage.entries.forEachIndexed { index, (packageName, ids) ->
            onProgress(packageName, index, byPackage.size)
            val app = apps[packageName]
            when {
                app == null -> summary.skip(SkipReason.APP_NOT_INSTALLED, ids.size)
                app.isSystem && !includeSystemApps -> summary.skip(SkipReason.SYSTEM_APP, ids.size)
                else -> block(app, ids, layers, summary)
            }
        }
        return RuleRunResult.Completed(summary.build())
    }

    private suspend fun block(app: InstalledApp, ids: List<ComponentId>, layers: Set<ControlLayer>, summary: SummaryBuilder) {
        val statuses = when (val read = components.freshComponents(app.key)) {
            is ReadResult.Success -> read.value

            is ReadResult.Failure -> {
                if (read.reason is ReadFailure.PackageNotFound) summary.skip(SkipReason.APP_NOT_INSTALLED, ids.size) else summary.fail(FailureReason.STATE_UNREADABLE)
                return
            }
        }
        val declared = statuses.associateBy { it.declaration.id }
        val (known, unknown) = ids.partition { it in declared }
        summary.skip(SkipReason.NOT_DECLARED, unknown.size)
        if (known.isEmpty()) return
        val request = ControlRequest(app.key.userId, known.map { declared.getValue(it).declaration }, ControlGoal.BLOCK, layers)
        val states = statuses.associate { it.declaration.id to it.state }.toMutableMap()
        if (components.control(request).countControlOutcomes(request.goal, states, summary) > 0) summary.apps++
    }
}
