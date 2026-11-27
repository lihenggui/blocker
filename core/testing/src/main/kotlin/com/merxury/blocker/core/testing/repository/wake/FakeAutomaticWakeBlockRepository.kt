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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FakeAutomaticWakeBlockRepository : AutomaticWakeBlockRepository {
    private val rows = MutableStateFlow<List<Row>>(emptyList())
    override val blocks = rows.map { entries -> entries.map(Row::visible) }.distinctUntilChanged()
    override val ownedComponents = MutableStateFlow<Set<ComponentId>>(emptySet())
    val currentBlocks: List<AutomaticWakeBlock> get() = rows.value.map(Row::visible)
    val allowedIds: Set<String> get() = currentBlocks.filter { it.allowed }.mapTo(mutableSetOf()) { it.pathId }
    val changes = mutableListOf<Pair<Set<String>, Boolean>>()

    fun setBlocks(blocks: List<AutomaticWakeBlock>) {
        rows.value = blocks.map { Row(it, configured = it.active) }
    }

    override suspend fun setBlocked(pathIds: Set<String>, blocked: Boolean): ChangeResult {
        val done = rows.value.map { it.block.pathId }.filterTo(mutableSetOf()) { it in pathIds }
        val changed = rows.value.filter { it.block.pathId in done && it.block.allowed == blocked }
            .mapTo(mutableSetOf()) { it.block.pathId }
        if (done.isNotEmpty()) {
            changes += done to blocked
            rows.value = rows.value.map { row ->
                if (row.block.pathId !in changed) {
                    row
                } else {
                    row.copy(
                        block = row.block.copy(allowed = !blocked),
                        pending = blocked && !row.configured,
                    )
                }
            }
        }
        val missing = pathIds - done
        return ChangeResult(WakeBlockOutcome(done, missing.takeIf { it.isNotEmpty() }?.let { WakeBlockFailure.NotBlockable(it.toList()) }), changed)
    }

    override suspend fun allowForApp(packageName: String): WakeBlockOutcome {
        val ids = currentBlocks.filter { !it.allowed && (it.caller == packageName || it.target == packageName) }
            .mapTo(mutableSetOf()) { it.pathId }
        return setBlocked(ids, blocked = false).outcome
    }

    private data class Row(val block: AutomaticWakeBlock, val configured: Boolean, val pending: Boolean = false) {
        fun visible() = block.copy(active = (configured || pending) && !block.allowed)
    }
}
