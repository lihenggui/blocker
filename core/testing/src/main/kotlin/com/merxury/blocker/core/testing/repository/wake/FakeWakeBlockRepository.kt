// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.testing.repository.wake

import com.merxury.blocker.core.data.wake.WakeBlockRepository
import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockFailure
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import kotlinx.coroutines.flow.MutableStateFlow

class FakeWakeBlockRepository : WakeBlockRepository {
    override val blocks = MutableStateFlow<List<WakeBlock>>(emptyList())
    var excluded = emptySet<String>()
    val requests = mutableListOf<Pair<WakePath, WakeBlockMethod>>()
    val unblockedPaths = mutableListOf<WakePath>()

    override suspend fun block(requests: List<Pair<WakePath, WakeBlockMethod>>): WakeBlockOutcome {
        this.requests += requests
        val previous = blocks.value.associateBy { it.pathKey }
        return restoreBlocks(
            requests.map { (path, method) ->
                WakeBlock(path.pathKey, path.target, method, path.componentType, path.actor, path.component, path.action, maxOf(100, (previous[path.pathKey]?.blockedAtMillis ?: 0) + 1))
            },
        )
    }

    override suspend fun blockRecordingChanges(requests: List<Pair<WakePath, WakeBlockMethod>>): Pair<WakeBlockOutcome, WakeBlockRepository.Snapshot> {
        val before = blocks.value.associateBy { it.pathKey }
        val outcome = block(requests)
        val written = blocks.value.filter { it.pathKey in outcome.done }
        return outcome to Snapshot(before.filterKeys { it in outcome.done }, written)
    }

    override suspend fun undoBlockChanges(snapshot: WakeBlockRepository.Snapshot): WakeBlockOutcome {
        require(snapshot is Snapshot)
        val current = blocks.value
        val keys = snapshot.written.filter { it in current }.map { it.pathKey }
        return change(keys) { done ->
            blocks.value = current.filterNot { it.pathKey in done } + done.mapNotNull(snapshot.before::get)
        }
    }

    private data class Snapshot(val before: Map<String, WakeBlock>, val written: List<WakeBlock>) : WakeBlockRepository.Snapshot {
        override val hasChanges get() = written.isNotEmpty()
    }

    override suspend fun unblock(pathKeys: Collection<String>): WakeBlockOutcome = change(pathKeys) { done ->
        blocks.value = blocks.value.filterNot { it.pathKey in done }
    }

    override suspend fun unblockPaths(paths: List<WakePath>): WakeBlockOutcome {
        unblockedPaths += paths
        return unblock(paths.map { it.pathKey })
    }

    override suspend fun removeAppBlocks(packageName: String, outboundPathKeys: Set<String>): Pair<WakeBlockOutcome, List<WakeBlock>> {
        val selected = blocks.value.filter { it.target == packageName || it.pathKey in outboundPathKeys }
        val outcome = unblock(selected.map { it.pathKey })
        return outcome to selected.filter { it.pathKey in outcome.done }
    }

    override suspend fun restoreBlocks(blocks: List<WakeBlock>): WakeBlockOutcome = change(blocks.map { it.pathKey }) { done ->
        this.blocks.value = (this.blocks.value.filterNot { it.pathKey in done } + blocks.filter { it.pathKey in done })
    }

    private fun change(keys: Collection<String>, apply: (Set<String>) -> Unit): WakeBlockOutcome {
        val failed = keys.filter { it in excluded }
        val done = keys.toSet() - failed.toSet()
        apply(done)
        return WakeBlockOutcome(done, failed.takeIf { it.isNotEmpty() }?.let { WakeBlockFailure.NotBlockable(it) })
    }
}
