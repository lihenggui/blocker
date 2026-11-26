// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import com.merxury.blocker.core.model.wake.WakeBlock
import com.merxury.blocker.core.model.wake.WakeBlockMethod
import com.merxury.blocker.core.model.wake.WakeBlockOutcome
import com.merxury.blocker.core.model.wake.WakePath
import kotlinx.coroutines.flow.Flow

interface WakeBlockRepository {
    /** Every block the user made on the wake screens. */
    val blocks: Flow<List<WakeBlock>>

    /** Cuts each path with its requested method. Connects the privileged channel, which may prompt. */
    suspend fun block(requests: List<Pair<WakePath, WakeBlockMethod>>): WakeBlockOutcome

    /** Removes the blocks of [pathKeys]. Connects the privileged channel, which may prompt. */
    suspend fun unblock(pathKeys: Collection<String>): WakeBlockOutcome

    /** Allows paths, including components switched off outside the wake screens. */
    suspend fun unblockPaths(paths: List<WakePath>): WakeBlockOutcome

    /** Removes the app's stored blocks and returns the original rows for completed or partial changes. */
    suspend fun removeAppBlocks(packageName: String, outboundPathKeys: Set<String>): Pair<WakeBlockOutcome, List<WakeBlock>>

    /** Reapplies the removed rows with their original block times. */
    suspend fun restoreBlocks(blocks: List<WakeBlock>): WakeBlockOutcome
}
