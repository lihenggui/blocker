// Copyright 2026 Blocker
// SPDX-License-Identifier: Apache-2.0

package com.merxury.blocker.core.data.wake

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes wake firewall changes, reconciliation and the sender filter probe. */
@Singleton
class WakeRuleLock @Inject constructor() {
    private val mutex = Mutex()
    private var lastBlockTime = 0L

    /** Called under this lock so a remove and re-block cannot reuse the old row identity. */
    internal fun nextBlockTime(previous: Long?): Long {
        lastBlockTime = maxOf(System.currentTimeMillis(), lastBlockTime + 1, (previous ?: 0L) + 1)
        return lastBlockTime
    }

    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock { action() }
}
