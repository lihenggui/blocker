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
import com.merxury.blocker.core.model.component.AppKey
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.component.ComponentStatus
import com.merxury.blocker.core.model.component.ControlState
import com.merxury.blocker.core.model.component.UserId
import com.merxury.blocker.core.model.control.ControlEvent
import com.merxury.blocker.core.model.control.ControlGoal
import com.merxury.blocker.core.model.control.ControlResult
import com.merxury.blocker.core.model.privileged.ChannelState
import com.merxury.blocker.core.model.privileged.PrivilegedIdentity
import com.merxury.blocker.core.model.system.InstalledApp
import com.merxury.blocker.core.model.system.ReadFailure
import com.merxury.blocker.core.model.system.ReadResult
import com.merxury.blocker.core.privileged.PrivilegedChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Connects the privileged channel, which may show the su or Shizuku prompt, and says whether it is
 * ready with an identity [allows] accepts. Every run is started by the user, so each connects once
 * before it reads users or apps; otherwise another user's apps could not be seen.
 */
internal suspend fun PrivilegedChannel.connectAllowing(allows: (PrivilegedIdentity) -> Boolean = { true }): Boolean {
    val state = connect()
    return state is ChannelState.Ready && allows(state.identity)
}

/** Reports progress: [current] is the item worked on, [done] of [total] are finished. */
typealias RuleRunProgress = suspend (current: String, done: Int, total: Int) -> Unit

/** Reads [user]'s apps from the system and returns them by package name. */
internal suspend fun InstalledAppRepository.installedApps(user: UserId): ReadResult<Map<String, InstalledApp>> = when (val read = refresh(user)) {
    is ReadResult.Success -> ReadResult.Success(apps(user).first().associateBy { it.key.packageName })
    is ReadResult.Failure -> read
}

/**
 * Reads [app]'s components from the system and returns them with their state as stored, or the
 * failure that left none: [ReadFailure.PackageNotFound] when the app is gone. A refresh that read
 * only some parts still returns the components; the unread parts are unknown in them.
 */
internal suspend fun ComponentStatusRepository.freshComponents(app: AppKey): ReadResult<List<ComponentStatus>> {
    val refresh = refresh(app)
    if (refresh is ReadResult.Failure && refresh.reason is ReadFailure.PackageNotFound) return refresh
    val statuses = components(app).first()
    if (statuses.isEmpty() && refresh is ReadResult.Failure) return refresh
    return ReadResult.Success(statuses)
}

/**
 * Counts each requested component once in [summary]: unchanged when
 * the goal was met and the state read back equals [states], applied when it was met by a change,
 * skipped when the controller protects the component, or failed with the first failing layer's
 * reason otherwise. [states] is updated with the state read back for attempted changes, so a
 * later request for the same components compares with it.
 */
internal suspend fun Flow<ControlEvent>.countControlOutcomes(
    goal: ControlGoal,
    states: MutableMap<ComponentId, ControlState>,
    summary: SummaryBuilder,
): Int {
    var applied = 0
    collect { event ->
        if (event !is ControlEvent.PackageDone) return@collect
        event.outcomes.forEach { outcome ->
            if (outcome.result == ControlResult.SKIPPED) {
                summary.skip(SkipReason.PROTECTED_COMPONENT)
                return@forEach
            }
            val id = outcome.key.id
            val before = states[id]
            if (outcome.state != before) summary.changed(id.packageName)
            states[id] = outcome.state
            when {
                outcome.result != ControlResult.ACHIEVED -> {
                    Timber.w("Cannot $goal ${id.flatten()} for user ${outcome.key.userId.value}: ${outcome.failures}")
                    summary.fail(outcome.failures.values.first().reason)
                }

                outcome.state == before -> summary.unchanged++

                else -> {
                    summary.applied++
                    applied++
                }
            }
        }
    }
    return applied
}
