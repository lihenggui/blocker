// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository.wake

import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository
import com.merxury.blocker.core.data.wake.AutomaticWakeBlockRepository.ChangeResult
import com.merxury.blocker.core.model.component.ComponentId
import com.merxury.blocker.core.model.wake.AutomaticWakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAutomaticWakeBlockRepository : AutomaticWakeBlockRepository {
    override val blocks = MutableStateFlow<List<AutomaticWakeBlock>>(emptyList())
    override val ownedComponents = MutableStateFlow<Set<ComponentId>>(emptySet())
    val allowedIds: Set<String> get() = blocks.value.filter { it.allowed }.mapTo(mutableSetOf()) { it.pathId }
    val changes = mutableListOf<Pair<Set<String>, Boolean>>()

    override suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): ChangeResult {
        val done = blocks.value.map { it.pathId }.filterTo(mutableSetOf()) { it in pathIds }
        val changed = blocks.value.filter { it.pathId in done && it.allowed == blocked }.mapTo(mutableSetOf()) { it.pathId }
        if (done.isNotEmpty()) {
            changes += done to blocked
            blocks.value = blocks.value.map { if (it.pathId in done && it.allowed == blocked) it.copy(active = blocked, allowed = !blocked) else it }
        }
        val missing = pathIds - done
        return ChangeResult(WakeBlockOutcome(done, missing.takeIf { it.isNotEmpty() }?.let { WakeBlockFailure.NotBlockable(it.toList()) }), changed)
    }

    override suspend fun allowForApp(packageName: String): WakeBlockOutcome {
        val ids = blocks.value.filter { !it.allowed && (it.caller == packageName || it.target == packageName) }
            .mapTo(mutableSetOf()) { it.pathId }
        return setBlocked(ids, blocked = false).outcome
    }
}
